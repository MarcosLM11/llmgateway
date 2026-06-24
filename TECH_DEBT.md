# Known Technical Debt and Future Work

This document lists known limitations, simplifications taken for the sake of
portfolio scope, and items deliberately deferred to future work. Each is
flagged with rationale so reviewers can see what was understood but
intentionally not done.

Completed items are kept (struck through) to show the project's evolution.

---

## Security

- [ ] **API keys stored as plaintext in `application.yml`.** Acceptable for demo but production should hash with Argon2id or BCrypt and persist in a database.
- [ ] **API key comparison uses `String.equals`.** Vulnerable to timing attacks. Production should use `MessageDigest.isEqual` or a constant-time comparator.
- [ ] **Authentication principal is a raw `String tenantId`.** Should evolve to a typed `AuthenticatedTenant` record carrying `tenantId`, `apiKeyId`, permitted models, plan, etc.
- [x] ~~**Admin role separation.**~~ Implemented in Phase 6: `ApiKeyEntry.admin` flag drives `ROLE_ADMIN` granting access to `/admin/**` endpoints.
- [ ] **Generated security password warning at startup.** Cosmetic noise from `UserDetailsServiceAutoConfiguration`; could be silenced by excluding the autoconfig.

## Rate limiting

- [ ] **`enabled` flag on `RateLimitProperties` is unused.** Either consult it in the filter or apply `@ConditionalOnProperty` on the service bean.
- [ ] **Same quota for all tenants.** Production should support per-tenant quotas based on plan, ideally configurable at runtime.
- [ ] **In-memory buckets via Caffeine.** Not distributed across instances. For multi-instance deployments, migrate to `bucket4j-redis`.
- [ ] **`refillGreedy` vs `refillIntervally` is a deliberate choice.** Greedy is smoother but harder to reason about (continuous refill compensates consumption during slow request rates). Intervally is more predictable.

## Semantic cache

- [ ] **Similarity threshold (`cache.similarity-threshold`) is global, not per-tenant.** Different tenants may want different aggressiveness.
- [ ] **No TTL on cached entries.** Entries live forever. Production should implement either time-based eviction or LRU/LFU.
- [ ] **No bypass header.** `X-Gateway-Cache: bypass` planned but not implemented; cache is currently controlled only by `temperature`.
- [ ] **Embedding dimensions hardcoded to 768.** Tied to `nomic-embed-text`. Changing embedding model requires schema migration.
- [x] ~~**No metrics for hit/miss ratio.**~~ Added in Phase 5: `llmgateway.cache.lookups` counter with `result=hit/miss` tag, visible in Grafana.
- [ ] **Naive prompt concatenation.** Currently `role: content` joined by newlines. Doesn't handle long histories well; tokens beyond the embedding model's context window are silently truncated.
- [ ] **No admin endpoint to flush a tenant's cache.** Needed for invalidation scenarios.

## Architecture / module boundaries

- [x] ~~**Cycle `cache ↔ gateway`.**~~ Resolved in Phase 7: cache is now agnostic of domain types and stores `String` JSON. Serialization happens at the `ChatService` boundary via Jackson 3.
- [x] ~~**Cycle `cache ↔ providers`.**~~ Resolved in Phase 7: `EmbeddingService` moved to `cache/` as public interface. `OllamaEmbeddingService` (production) and `StubEmbeddingService` (test) implement it. No more shared concrete class with `MeterRegistry` coupling.

## Error handling

- [ ] **Exception messages from internal libraries can leak to clients.** Some handlers forward `e.getMessage()` directly. Should sanitize before responding.
- [ ] **No structured logging of caught domain errors.** `handleAllFailed` and `handleInvalidStrategy` build the response but don't log the suppressed exceptions. Infrastructure failures should be observable.

## Domain / mapping

- [ ] **`Role` enum exposes external name via `name().toLowerCase()` at the mapper boundary.** Could be moved into the enum as `externalName()` to centralize the convention.
- [ ] **Cache concatenates messages as `role: content`.** This is the prompt the embedding model sees. Convention is project-internal and undocumented.

## Build and deployment

- [ ] **Java 26 preview features (Structured Concurrency, JEP 525) require `--enable-preview` everywhere.** Tied to a specific JDK build; not yet GA.
- [ ] **Spring Boot 4.x is in SNAPSHOT.** Versions may shift; some BOMs (e.g. Testcontainers) had to be pinned explicitly to work around BOM gaps.
- [ ] **Docker runtime uses a pinned JDK base and a custom `jlink` runtime.** Preview features still depend on Java 26, so patch-level upgrades should be validated before release.
- [x] ~~**No CI pipeline. Tests run only locally.**~~ Added in Phase 7: GitHub Actions workflow runs `./gradlew clean build` on push and PR, uploads test and coverage reports as artifacts.
- [ ] **No healthcheck on the `llmgateway` Compose service.** Other services depending on it cannot wait properly.
- [ ] **GitHub Actions uses `actions/*@v4`** which internally declare Node 20. Runners force Node 24, emitting a deprecation warning. Upgrade to `@v5` when published.

## Observability

- [x] ~~**No metrics exporter, traces, or log aggregation.**~~ Phase 5: full OTEL stack with Prometheus, Tempo, Loki, Grafana. Trace ↔ logs correlation working bidirectionally.
- [ ] **Tracing sampling at 100%.** Production should reduce to 5-10% to control cost.
- [ ] **OTLP endpoint hardcoded to `localhost:4318`.** Overridden via env var in Docker Compose but should be fully externalized.
- [ ] **No custom spans.** Only Spring Boot auto-instrumentation. Manual spans like `semantic-cache-lookup` would add value.
- [ ] **OTLP logback appender is alpha.** Update when OTel publishes GA of the appender.
- [ ] **Loki single-instance, no retention configured.** Production needs cluster or separate ingester with 30-90d retention.
- [ ] **`service_name` is the only Loki label.** Rest is structured metadata. Deliberate to avoid cardinality explosion; tenant queries use post-stream filtering instead of indexed labels.
- [ ] **No log sampling.** Production may overload Loki under bursts; consider level-based filtering or sampling.
- [ ] **Minimal Grafana dashboard (4 panels).** Production would have separate dashboards per subsystem (cache, providers, embedding, JVM, HTTP).
- [ ] **Anonymous viewer with `Viewer` role.** Fine for portfolio, production needs OAuth/LDAP.
- [ ] **No alerting configured.** Production should define alerts (cache hit rate below X, latency P99 above Y, error rate spikes).
- [ ] **Tempo service map disabled by default.** Requires `metrics_generator` config which was omitted for simpler startup.

## Metering / events

- [x] ~~**No usage tracking per tenant.**~~ Phase 6: `UsageEvent` published to Kafka per request, consumed and persisted to Postgres with idempotency via `INSERT ... ON CONFLICT (request_id)`. Admin endpoint `GET /admin/usage?tenantId=...` exposes aggregates.
- [ ] **Only success-path events.** Production should also publish `FailedUsageEvent` for error tracking, retry budgets, and SLO/SLA dashboards.
- [ ] **`KafkaTemplate<String, Object>` instead of typed per-event template.** Trade-off taken in Phase 7 to keep `@ServiceConnection` wiring simple in tests. Production could split into typed templates per event class.
- [ ] **No Schema Registry / Avro.** JSON serialization with `JacksonJsonSerializer`. Schema evolution is implicit, which works at this scale but doesn't enforce contract across producer/consumer.
- [ ] **Single partition consumer group with `auto-offset-reset: earliest`.** Acceptable for demo; production needs partition count tuned to throughput and consumer parallelism.

## Testing & CI

- [x] ~~**Tests assume Postgres + Kafka running locally.**~~ Phase 7: migrated to `Testcontainers 2.0` via shared `AbstractIntegrationTest` base. `./gradlew test` works from a clean clone with only Docker required.
- [ ] **Coverage 78% global.** Module cores (cache, gateway, metering) are above 90%, but `gateway/web/` (45%) and `providers/ollama/` (34%) lag because mappers and Ollama adapters aren't covered by E2E. Pending: unit tests for mappers and DTO conversions.
- [ ] **Coverage badge in README is hardcoded.** Should integrate Codecov or Coveralls to publish `jacocoTestReport.xml` and get a dynamic badge.
- [ ] **No mutation testing.** Tools like PIT would surface coverage that's technically high but doesn't catch real regressions.
- [ ] **No load testing.** Gatling or k6 scenarios would validate rate-limit behavior, cache hit rates under realistic load, and provider race conditions.
- [ ] **No contract testing for the OpenAI-compatible API.** A Pact or Spring Cloud Contract test would catch breaking changes against real OpenAI SDK clients.

## Deferred phases

- [x] ~~**Phase 5**: Observability stack~~
- [x] ~~**Phase 6**: Event-driven metering~~
- [x] ~~**Phase 7**: Test harness + CI~~
- [ ] **gRPC endpoint + SDK helpers.** Originally planned as Phase 7, dropped in favor of testing & CI. May revisit later as separate work.
- [ ] **Per-tenant config UI.** Admin frontend to manage API keys, rate limits, model permissions.
- [x] ~~**Cost tracking.**~~ Implemented: `pricing_rules` table in Postgres (wildcard `*` fallback per provider), `estimated_cost_usd` column in `usage_events` (computed at consume-time via `CostCalculator` + `PricingRepository`), `BigDecimal` arithmetic, `null` for unknown pricing. Exposed in `GET /admin/usage` response (`totalCostUsd`, `requestsWithoutPricing`, per-model `totalCostUsd` / `avgCostPerRequestUsd`). Cache hits cost $0.