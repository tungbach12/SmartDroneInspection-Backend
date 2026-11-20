-- Switch pgvector columns and HNSW indexes to 384 dimensions for the local multilingual
-- embedding model (intfloat/multilingual-e5-small) served by ai-inference.
DROP INDEX IF EXISTS ix_inspection_knowledge_chunks_hnsw;
DROP INDEX IF EXISTS ix_historical_finding_cases_hnsw;

ALTER TABLE inspection_knowledge_chunks
    ALTER COLUMN embedding TYPE vector(384)
    USING NULL;

ALTER TABLE historical_finding_cases
    ALTER COLUMN embedding TYPE vector(384)
    USING NULL;

CREATE INDEX ix_inspection_knowledge_chunks_hnsw
    ON inspection_knowledge_chunks USING hnsw (embedding vector_cosine_ops);

CREATE INDEX ix_historical_finding_cases_hnsw
    ON historical_finding_cases USING hnsw (embedding vector_cosine_ops);
