# syntax=docker/dockerfile:1.7
# ─────────────────────────────────────────────────────────────────────────────
# Multi-stage build:
#   deps    – prefetch Gradle dependencies (rarely changes → stable cache layer)
#   builder – compile, package, generate minimal JRE via jlink, explode layers
#   runtime – distroless image with only the custom JRE + app layers
# ─────────────────────────────────────────────────────────────────────────────

ARG JAVA_IMAGE=eclipse-temurin:26-jdk-jammy
ARG RUNTIME_IMAGE=gcr.io/distroless/base-debian12:nonroot

# ── Stage 1: resolve dependencies only (cache-friendly) ──────────────────────
FROM ${JAVA_IMAGE} AS deps
WORKDIR /workspace
COPY --link gradlew build.gradle settings.gradle ./
COPY --link gradle/ gradle/
# Resolves and caches all runtime deps before any source is copied.
# A code-only change will never re-download dependencies.
RUN --mount=type=cache,target=/root/.gradle \
	./gradlew --no-daemon dependencies --configuration runtimeClasspath

# ── Stage 2: compile, package, extract layers, generate minimal JRE ──────────
FROM deps AS builder
COPY --link src/ src/
RUN --mount=type=cache,target=/root/.gradle \
	DOCKER_BUILD=true ./gradlew --no-daemon bootJar -x test

# Explode the fat JAR into Spring Boot layers so Docker can reuse the large
# dependency layers on code-only rebuilds (only the application/ layer changes).
RUN APP_JAR="$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' \
	  | head -n 1)" && \
	java -Djarmode=tools -jar "$APP_JAR" \
	  extract --layers --launcher --destination build/extracted

# Generate a minimal JRE containing only the modules the application needs.
RUN set -eux; \
	APP_JAR="$(find /workspace/build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' \
	  | head -n 1)"; \
	mkdir -p /tmp/app; \
	cd /tmp/app; \
	jar -xf "$APP_JAR"; \
	MODULES="$(jdeps \
	  --ignore-missing-deps \
	  --multi-release 26 \
	  --print-module-deps \
	  --class-path '/tmp/app/BOOT-INF/lib/*' \
	  /tmp/app/BOOT-INF/classes)"; \
	jlink \
	  --add-modules "$MODULES" \
	  --strip-debug \
	  --no-header-files \
	  --no-man-pages \
	  --compress=2 \
	  --output /opt/java-runtime

# ── Stage 3: minimal distroless runtime ──────────────────────────────────────
FROM ${RUNTIME_IMAGE} AS runtime

# OCI standard labels (https://github.com/opencontainers/image-spec)
LABEL org.opencontainers.image.title="llmgateway" \
      org.opencontainers.image.description="OpenAI-compatible LLM gateway" \
      org.opencontainers.image.licenses="MIT"

WORKDIR /app

# Custom JRE first – it changes very rarely → best candidate for layer caching.
COPY --from=builder --chown=65532:65532 /opt/java-runtime /opt/java-runtime

# App layers ordered most-stable → most-volatile.
# A code-only change only invalidates the last layer (application/).
COPY --from=builder --chown=65532:65532 \
     /workspace/build/extracted/dependencies/          ./
COPY --from=builder --chown=65532:65532 \
     /workspace/build/extracted/spring-boot-loader/    ./
COPY --from=builder --chown=65532:65532 \
     /workspace/build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=65532:65532 \
     /workspace/build/extracted/application/           ./

# Run as the distroless nonroot user (UID 65532).
USER 65532:65532
EXPOSE 8080

# NOTE: distroless has no shell/curl, so HEALTHCHECK cannot use exec-form shell
# commands. Use the docker-compose healthcheck or a Kubernetes liveness probe:
#   GET http://localhost:8080/actuator/health

# JVM container-aware flags:
#   UseContainerSupport    – honour cgroup CPU/memory limits (default JDK 11+, explicit here)
#   MaxRAMPercentage=75.0  – use 75 % of the container memory limit for the heap
#   ExitOnOutOfMemoryError – fail-fast so the orchestrator restarts cleanly
#   security.egd           – avoid /dev/random entropy blocking on startup
ENTRYPOINT ["/opt/java-runtime/bin/java", \
  "--enable-preview", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]
