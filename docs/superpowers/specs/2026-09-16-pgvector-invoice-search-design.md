# pgvector Invoice Semantic Search — Design

## Goal

After analyzing an invoice into structured Postgres rows, also store a vector embedding so users can search invoices with natural-language queries plus optional exact filters (amount, currency, date).

## Decisions

- **Same Postgres + pgvector** (not a second database): swap Docker image to `pgvector/pgvector:pg17`, enable the `vector` extension.
- **Embed structured summary only** (supplier, address, invoice number, date, amount, currency).
- **Configurable embedding provider** (default Ollama `nomic-embed-text` / 768 dims; OpenAI switchable via config).
- **Search UX**: `GET /ai/invoices/search` + search UI on the invoices list (not chat/RAG in v1).

## Architecture

```
analyze/update invoice
  → persist invoice row (JPA)
  → build summary text
  → EmbeddingModel → VectorStore (pgvector table)
  → Document id = deterministic UUID from invoice id; metadata.invoiceId

search
  → embed query
  → VectorStore.similaritySearch(topK)
  → load Invoice entities by metadata invoiceIds (preserve rank)
  → apply SQL/Java filters (min/max amount, currency, from/to invoiceDate)
  → return InvoiceResponse[]
```

When `q` is blank, skip vector search and filter invoices from the relational table only.

## Infra

- `docker-compose.yml`: `pgvector/pgvector:pg17`
- Flyway `V5__enable_pgvector.sql`: `CREATE EXTENSION IF NOT EXISTS vector` (+ `hstore`, `uuid-ossp` as needed)
- Spring AI `spring-ai-starter-vector-store-pgvector` + `initialize-schema=true`
- Dimensions via `spring.ai.vectorstore.pgvector.dimensions` aligned with embedding model

## Config

```
app.ai.embedding.provider=ollama
app.ai.embedding.model=nomic-embed-text
spring.ai.vectorstore.pgvector.dimensions=768
spring.ai.vectorstore.pgvector.initialize-schema=true
spring.ai.vectorstore.pgvector.index-type=HNSW
spring.ai.vectorstore.pgvector.distance-type=COSINE_DISTANCE
```

Switching to OpenAI requires changing model, dimensions (e.g. 1536), and re-embedding (recreate/clear `vector_store`).

## API

`GET /ai/invoices/search?q=&minAmount=&maxAmount=&currency=&fromDate=&toDate=&limit=`

Returns ranked `InvoiceResponse[]`. Default `limit` 20.

## Frontend

Replace client-only table filter with a semantic search form (query + optional filters) that calls the search API. Keep “show all” via existing list when search is cleared.

## Error handling

- Embedding/vector failures on save: log and surface as analyze/update failure so structured row and vector stay in sync (transactional where practical; vector store is separate from JPA so best-effort delete+add after successful invoice save, with clear error if embedding fails after save).
- Prefer: save invoice, then upsert embedding; if embedding fails, return error indicating invoice was saved but indexing failed (or roll back invoice — prefer fail the request and delete invoice if embedding fails on create for stronger consistency in v1).

Consistency choice for v1: **on create, if embedding fails after save, delete the invoice and throw**. On update, if embedding fails, leave invoice updated and throw (user can retry).

## Out of scope

- Chat/RAG retrieval
- Embedding raw PDF text
- Separate vector DB
- Automatic re-index of historical invoices beyond a simple backfill note in README
