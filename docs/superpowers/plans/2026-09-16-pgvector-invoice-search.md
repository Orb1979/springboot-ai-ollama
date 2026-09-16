# pgvector Invoice Semantic Search Implementation Plan

> **For agentic workers:** Execute task-by-task. Steps use checkbox syntax.

**Goal:** Store invoice embeddings in pgvector and expose filtered semantic search via API + invoices UI.

**Architecture:** Same Postgres with pgvector extension; Spring AI EmbeddingModel + PgVectorStore; upsert on analyze/update; search joins vector hits to invoice rows then applies filters.

**Tech Stack:** Spring Boot 4 / Spring AI 2.0.1, pgvector Docker image, Flyway, React frontend.

## Global Constraints

- Branch: `cursor/pgvector-invoice-search-8398`
- Default embedding: Ollama `nomic-embed-text`, dimensions **768**
- Embed structured fields only
- Do not add a second Postgres container

---

### Task 1: Infra (Docker, deps, Flyway, config, EmbeddingModel)

**Files:**
- Modify: `docker-compose.yml`, `build.gradle`, `application.properties`, `application-test.properties`
- Create: `src/main/resources/db/migration/V5__enable_pgvector.sql`
- Create: `src/main/java/com/example/ollama/config/EmbeddingModelConfig.java`

- [ ] Switch Postgres image to `pgvector/pgvector:pg17`
- [ ] Add `spring-ai-starter-vector-store-pgvector` and `spring-boot-starter-jdbc`
- [ ] Flyway: enable `vector`, `hstore`, `uuid-ossp`
- [ ] Properties for embedding provider/model + pgvector store
- [ ] `EmbeddingModel` bean mirroring ChatClient provider switch

### Task 2: Invoice embedding + search service

**Files:**
- Create: `InvoiceEmbeddingService.java`, `InvoiceSearchCriteria.java`
- Modify: `InvoiceAnalyzerService.java`, `InvoiceController.java`
- Test: `InvoiceEmbeddingServiceTest.java`, update `InvoiceAnalyzerServiceTest.java`

- [ ] Build summary text from invoice fields
- [ ] Upsert Document with deterministic UUID + `invoiceId` metadata
- [ ] Search: similarity → load invoices → filter → rank
- [ ] Blank `q`: filter-only path via repository
- [ ] Wire save/update to upsert embeddings

### Task 3: Frontend search UI

**Files:**
- Modify: `frontend/src/api/client.ts`, `InvoiceDataTable.tsx`, `InvoiceList.tsx`, related tests/CSS if needed

- [ ] `searchInvoices(params)` client
- [ ] Semantic search + filter controls calling API
- [ ] Clear search reloads full list

### Task 4: Verify, docs, PR

- [ ] Backend unit tests + frontend tests
- [ ] README notes (pgvector image, `ollama pull nomic-embed-text`, dimension warning)
- [ ] Commit, push, open PR
