-- Habilitar pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- Tabla de caché semántica
CREATE TABLE semantic_cache_entries (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   VARCHAR(64)    NOT NULL,
    embedding   vector(768)    NOT NULL,
    prompt      TEXT           NOT NULL,
    response    JSONB          NOT NULL,
    model_used  VARCHAR(128)   NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- Índice por tenant para los filtros
CREATE INDEX idx_cache_tenant ON semantic_cache_entries(tenant_id);

-- Índice HNSW sobre el embedding, para búsqueda rápida de vecinos por coseno
CREATE INDEX idx_cache_embedding_hnsw ON semantic_cache_entries
    USING hnsw (embedding vector_cosine_ops);