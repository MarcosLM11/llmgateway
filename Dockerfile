# syntax=docker/dockerfile:1.7

FROM bellsoft/liberica-openjdk-debian:26-cds AS builder
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --version
COPY src src
RUN ./gradlew bootJar -x test

FROM bellsoft/liberica-openjdk-debian:26-cds AS runtime
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "--enable-preview", "-jar", "/app/app.jar"]