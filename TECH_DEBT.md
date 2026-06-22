# Known Technical Debt and Future Work

This document lists known limitations, simplifications taken for the sake of
portfolio scope, and items deliberately deferred to future phases. Each is
flagged with rationale so reviewers can see what was understood but
intentionally not done.

## Security

- **API keys stored as plaintext in `application.yml`.** Acceptable for demo
  but production should hash with Argon2id or BCrypt and persist in a database.
- **API key comparison uses `String.equals`.** Vulnerable to timing attacks.
  Production should use `MessageDigest.isEqual` or a constant-time comparator.
- **Authentication principal is a raw `String tenantId`.** Should evolve to
  a typed `AuthenticatedTenant` record carrying `tenantId`, `apiKeyId`,
  permitted models, plan, etc.
- **Generated security password warning at startup.** Cosmetic noise from
  `UserDetailsServiceAutoConfiguration`; could be silenced by excluding the
  autoconfig.

## Rate limiting

- **`enabled` flag on `RateLimitProperties` is unused.** Either consult it in
  the filter or apply `@ConditionalOnProperty` on the service bean.
- **Same quota for all tenants.** Production should support per-tenant
  quotas based on plan, ideally configurable at runtime.
- **In-memory buckets via Caffeine.** Not distributed across instances. For
  multi-instance deployments, migrate to `bucket4j-redis`.
- **`refillGreedy` versus `refillIntervally` is a deliberate choice.** Greedy
  is smoother but harder to reason about (continuous refill compensates
  consumption during slow request rates). Intervally is more predictable.

## Semantic cache

- **Similarity threshold (`gateway.cache.similarity-threshold`) is global,
  not per-tenant.** Different tenants may want different aggressiveness.
- **No TTL on cached entries.** Entries live forever. Production should
  implement either time-based eviction or LRU/LFU.
- **No bypass header.** `X-Gateway-Cache: bypass` planned but not
  implemented; cache is currently controlled only by `temperature`.
- **Embedding dimensions hardcoded to 768.** Tied to `nomic-embed-text`.
  Changing embedding model requires schema migration.
- **No metrics for hit/miss ratio.** Should land with observability phase.
- **Naive prompt concatenation.** Currently `role: content` joined by
  newlines. Doesn't handle long histories well; tokens beyond the context
  window of the embedding model are silently truncated.
- **No admin endpoint to flush a tenant's cache.** Needed for invalidation
  scenarios.

## Error handling

- **Exception messages from internal libraries can leak to clients.** Some
  handlers forward `e.getMessage()` directly. Should sanitize before
  responding.
- **No structured logging of caught domain errors.** `handleAllFailed` and
  `handleInvalidStrategy` build the response but don't log the suppressed
  exceptions. Infrastructure failures should be observable.

## Domain / mapping

- **`Role` enum exposes external name via `name().toLowerCase()` at the
  mapper boundary.** Could be moved into the enum as `externalName()` to
  centralize the convention.
- **Cache concatenates messages as `role: content`.** This is the prompt
  the embedding model sees. Convention is project-internal and undocumented.

## Build and deployment

- **Java 26 preview features (Structured Concurrency, JEP 525) require
  `--enable-preview` everywhere.** Tied to a specific JDK build; not yet
  GA.
- **Spring Boot 4.x is in SNAPSHOT.** Versions may shift; some BOMs (e.g.
  Testcontainers) had to be pinned explicitly to work around BOM gaps.
- **Image tag `bellsoft/liberica-openjdk-debian:26-cds` is not pinned to a
  patch version.** Future minor JDK releases may break the preview API.
- **No CI pipeline.** Tests run only locally.
- **No healthcheck on the `llmgateway` Compose service.** Other services
  depending on it cannot wait properly.

## Observability (planned for Phase 5)

- No metrics exporter, no traces, no log aggregation. Phase 5 will add
  Micrometer, Prometheus, Grafana, Loki, Tempo.

## Metering / events (planned for Phase 6)

- No usage tracking per tenant. Phase 6 will publish events to Kafka and
  let downstream services build dashboards or billing.

## gRPC and SDK (planned for Phase 7)

- Only REST is exposed today. Phase 7 will add a gRPC endpoint and a
  client library.



Sampling al 100%: en producción se baja (típico 5-10%).
Endpoint OTLP hardcoded a localhost:4318. Para Docker Compose se sobreescribe con env var.
Sin instrumentación manual extra: solo lo que Spring Boot auto-instrumenta. Spans custom (ej. "semantic-cache-lookup") se podrían añadir más adelante.



Appender OTLP en versión alpha. Cuando OTel publique GA del logback-appender, actualizar.
Loki single-instance, sin retention configurada. Producción: cluster o ingester separado, retention 30-90d.
service_name es el único label real en Loki. Resto va como structured metadata. Consciente: evita explosión de cardinalidad. Para queries por tenant: {service_name="llmgateway"} | tenantId="alice-corp" (filtro post-stream, no índice).
Sin sampling de logs. En producción podría sobrecargar Loki, considerar filtrado por level o muestreo.


Dashboard mínimo: 4 paneles. Producción tendría dashboards separados por sub-sistema (cache, providers, embedding, JVM, HTTP).
Anonymous viewer con role Viewer: bien para portfolio, en producción requiere auth real (OAuth, LDAP, etc.).
Sin alerting configurado: en producción definirías alertas en Grafana (cache hit rate por debajo de X, latencia P99 por encima de Y).
Service map de Tempo deshabilitado por defecto: requiere metrics_generator en Tempo config, que quitamos por simplificar arranque.