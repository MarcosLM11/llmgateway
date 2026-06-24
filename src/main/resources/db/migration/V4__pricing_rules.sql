CREATE TABLE pricing_rules (
    id                     BIGSERIAL      PRIMARY KEY,
    provider               VARCHAR(64)    NOT NULL,
    model                  VARCHAR(128)   NOT NULL,
    prompt_per_million     NUMERIC(12, 8) NOT NULL CHECK (prompt_per_million >= 0),
    completion_per_million NUMERIC(12, 8) NOT NULL CHECK (completion_per_million >= 0),
    effective_from         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    UNIQUE (provider, model, effective_from)
);

CREATE INDEX idx_pricing_provider_model ON pricing_rules (provider, model, effective_from DESC);

INSERT INTO pricing_rules (provider, model, prompt_per_million, completion_per_million) VALUES
    ('openai', 'gpt-4o',       2.50,  10.00),
    ('openai', 'gpt-4o-mini',  0.15,   0.60),
    ('openai', 'gpt-4.1',      2.00,   8.00),
    ('openai', 'gpt-4.1-mini', 0.40,   1.60),
    ('openai', 'o3',          10.00,  40.00),
    ('openai', 'o4-mini',      1.10,   4.40),
    ('ollama', '*',             0.00,   0.00),
    ('mock',   '*',             0.00,   0.00);