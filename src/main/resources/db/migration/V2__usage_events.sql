CREATE TABLE usage_events (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(64) UNIQUE NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    prompt_tokens INT NOT NULL,
    completion_tokens INT NOT NULL,
    cache_hit BOOLEAN NOT NULL,
    latency_ms BIGINT NOT NULL,
    event_timestamp TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_usage_events_tenant_time ON usage_events (tenant_id, event_timestamp DESC);
CREATE INDEX idx_usage_events_tenant_model ON usage_events (tenant_id, model);