# LLM Gateway

> A production-grade LLM gateway in front of multiple providers, with
> per-tenant authentication, rate limiting, and semantic response caching.

[![Java](https://img.shields.io/badge/Java-26-blue?logo=openjdk)](https://openjdk.org/projects/jdk/26/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-green?logo=spring)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## What it does

This is an OpenAI-compatible HTTP gateway that routes chat completion
requests to one of several LLM providers (Ollama, mock, future OpenAI/Anthropic),
handles authentication and rate limiting per tenant, and serves cached
responses for semantically similar prompts using `pgvector`.

Built as a portfolio project to explore Spring Boot 4, Java 26 preview
features (Structured Concurrency), Spring Modulith, and Spring AI.

## Features

- **OpenAI-compatible API** at `POST /v1/chat/completions`.
- **Multi-provider routing** with two strategies:
    - `sequential-fallback`: try providers in order until one succeeds.
    - `parallel-race`: query all providers concurrently, return the first
      successful response (uses Java 26 Structured Concurrency, JEP 525).
- **API key authentication** with per-tenant identity (`Authorization: Bearer sk-...`).
- **Rate limiting per tenant** with token bucket (Bucket4j + Caffeine).
- **Semantic response cache** using `pgvector` and Ollama embeddings
  (`nomic-embed-text`). Identical or semantically similar prompts return
  cached responses in milliseconds.
- **Structured errors** in OpenAI format, with `request_id` propagated
  via MDC for traceability.
- **Modular architecture** enforced by Spring Modulith.

## Architecture

```
+--------------+    HTTP    +-------------------------+
| API client   | ---------> |  GatewayController      |
+--------------+            |  RequestIdFilter        |
                            |  ApiKeyAuthFilter       |
                            |  RateLimitFilter        |
                            +-----------+-------------+
                                        |
                                        v
                            +-------------------------+
                            |  ChatService            |
                            |  - cache lookup         |
                            |  - provider routing     |
                            |  - cache store          |
                            +---+-----------------+---+
                                |                 |
                  cache         |                 |  providers
                  +-------------+                 +------------+
                  |                                            |
                  v                                            v
        +-------------------+                      +----------------------+
        | PgVector cache    |                      | OllamaLlmProvider    |
        | (Postgres +       |                      | MockLlmProvider      |
        |  Spring AI        |                      | (future: OpenAI, ...) 
        |  embeddings)      |                      +----------------------+
        +-------------------+
```

Modules (enforced by Spring Modulith):

- `gateway` — HTTP adapter, domain types (`ChatRequest`, `ChatResponse`), `ChatService`.
- `providers` — adapters for each LLM (Ollama, mock).
- `cache` — semantic cache (pgvector + Spring AI embeddings).

## Tech stack

- **Language**: Java 26 (with preview features: Structured Concurrency).
- **Framework**: Spring Boot 4.1, Spring Modulith 2.1.
- **Security**: Spring Security 7.1, custom API key filter.
- **Rate limiting**: Bucket4j 8 + Caffeine 3.
- **AI**: Spring AI 2 with Ollama (`nomic-embed-text` for embeddings).
- **Persistence**: PostgreSQL 17 + `pgvector`, Flyway, JdbcTemplate.
- **Testing**: JUnit 5, Testcontainers, AssertJ.
- **Build**: Gradle 9.

## Prerequisites

- Docker Desktop (for Postgres + pgvector).
- Ollama running locally on `0.0.0.0:11434` with at least:
    - `nomic-embed-text` (for embeddings).
    - `qwen2.5-coder:7b` or another chat model.
- JDK 26 (Liberica recommended) — only required if running the app
  outside Docker.

### Configure Ollama to accept external connections (macOS)

```bash
launchctl setenv OLLAMA_HOST "0.0.0.0:11434"
# Restart Ollama application
```

### Pull required models

```bash
ollama pull nomic-embed-text
ollama pull qwen2.5-coder:7b
```

## Quickstart

```bash
# 1. Start Postgres
docker compose up postgres -d

# 2. Run the gateway (from IntelliJ or CLI)
./gradlew bootRun

# 3. Test it
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer sk-test-alice" \
  -d '{
    "model": "qwen2.5-coder:7b",
    "messages": [{"role": "user", "content": "Hello"}]
  }'
```

To run everything dockerized:

```bash
docker compose up --build
```

## Demo

**Semantic cache in action.** The first call hits Ollama; the second
returns the cached response in milliseconds, even when the prompt is
phrased differently.

```bash
# First call — MISS (calls Ollama, ~2 seconds)
$ time curl -s -X POST localhost:8080/v1/chat/completions \
    -H "Authorization: Bearer sk-test-alice" \
    -H "Content-Type: application/json" \
    -d '{"model":"qwen2.5-coder:7b","messages":[{"role":"user","content":"What is the capital of France?"}]}'

real    0m1.98s

# Same call — HIT (cache responds, ~70ms)
$ time curl -s -X POST localhost:8080/v1/chat/completions \
    -H "Authorization: Bearer sk-test-alice" \
    -H "Content-Type: application/json" \
    -d '{"model":"qwen2.5-coder:7b","messages":[{"role":"user","content":"What is the capital of France?"}]}'

real    0m0.068s

# Reformulated — HIT (similarity above threshold, still cached)
$ time curl -s -X POST localhost:8080/v1/chat/completions \
    -H "Authorization: Bearer sk-test-alice" \
    -H "Content-Type: application/json" \
    -d '{"model":"qwen2.5-coder:7b","messages":[{"role":"user","content":"Capital of France?"}]}'

real    0m0.62s
```

**Rate limiting per tenant.**

```bash
# Configured at 10 requests/minute per tenant
$ for i in {1..11}; do
    curl -s -o /dev/null -w "%{http_code}\n" \
      -X POST localhost:8080/v1/chat/completions \
      -H "Authorization: Bearer sk-test-alice" \
      -H "Content-Type: application/json" \
      -d '{"model":"mock-fast","messages":[{"role":"user","content":"hi"}]}'
  done

200 200 200 200 200 200 200 200 200 200 429
```

**Structured errors with request_id traceability.**

```bash
$ curl -i -X POST localhost:8080/v1/chat/completions
HTTP/1.1 401
X-Request-Id: 32108579-565d-4fb6-9b74-8449742467b1
...
{
  "request_id": "32108579-565d-4fb6-9b74-8449742467b1",
  "error": {
    "message": "Missing or invalid API key",
    "type": "invalid_request_error",
    "code": "invalid_api_key"
  }
}
```

The same `request_id` appears in the response header, response body, and
server logs (via MDC), enabling end-to-end request tracing.

## Project structure

```
src/main/java/com/marcos/llmgateway/
├── gateway/                  # public API, domain types
│   ├── ChatRequest.java
│   ├── ChatResponse.java
│   ├── LlmProvider.java
│   └── internal/
│       ├── ChatService.java
│       └── web/
│           ├── GatewayController.java
│           ├── RequestIdFilter.java
│           ├── GatewayExceptionHandler.java
│           ├── security/     # ApiKeyAuthenticationFilter, etc.
│           └── ratelimit/    # RateLimitFilter, RateLimitService
├── providers/
│   ├── ollama/               # OllamaLlmProvider
│   └── mock/                 # MockLlmProvider
└── cache/
    ├── SemanticCache.java    # public interface
    └── internal/
        ├── PgVectorSemanticCache.java
        └── EmbeddingService.java
```

## Roadmap

- ✅ **Phase 1** — REST API, domain, basic routing
- ✅ **Phase 2** — Multi-provider, Structured Concurrency
- ✅ **Phase 3** — Structured errors, authentication, rate limiting
- ✅ **Phase 3.5** — Dockerized
- ✅ **Phase 4** — Semantic cache (pgvector + Spring AI)
- ⏳ **Phase 5** — Observability (Micrometer, Prometheus, Grafana, Loki, Tempo)
- ⏳ **Phase 6** — Event-driven metering with Kafka
- ⏳ **Phase 7** — gRPC endpoint + client SDK
- ⏳ **Phase 8** — Test harness expansion and CI

## Known limitations

This is a portfolio project, not production code. See
[`TECH_DEBT.md`](./TECH_DEBT.md) for a detailed list of known limitations
and rationale.

## License

MIT