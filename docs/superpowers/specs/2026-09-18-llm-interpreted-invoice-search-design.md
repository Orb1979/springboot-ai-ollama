# LLM-Interpreted Invoice Search — Design

## Goal

Improve natural-language invoice search so queries like  
`"invoices from Acme in Amsterdam over 500 euro"`  
reliably enforce structured constraints (supplier, city, amount, currency) while still using embeddings for soft ranking.

Embeddings alone are weak at hard constraints and filler words. This design adds an LLM parse step that turns free text into hard SQL filters, then ranks remaining candidates with vector search.

## Decisions

| Decision | Choice |
|---|---|
| Structured fields from NL | Hard filters (must match) |
| Supplier / city match | Case-insensitive **contains** |
| After filters | Vector search ranks within (or constrained by) the filtered set |
| UI filter params | Still supported; merge with LLM output (**explicit UI value wins** on conflict) |
| When LLM extracts nothing | Fall back to current behavior: vector search + any UI filters only |
| Chat stack | Reuse existing `ChatClient` / provider config (same pattern as invoice extraction) |

Out of scope for v1: fuzzy typo tolerance (`Amstrdam`), dedicated synonym tables, full-text search engines, RAG chat over invoices.

## Architecture

```
GET /ai/invoices/search?q=...&minAmount=...&...

1. If q is blank
   → SQL filters only (existing UI criteria + limit)
   → return InvoiceResponse[]

2. If q is present
   → LLM extracts InvoiceQueryInterpretation (schema-bound JSON)
   → Merge into InvoiceSearchCriteria:
        - hard filters from LLM (supplier, city, min/max amount, currency, dates, …)
        - UI params override LLM when both set
        - semanticQuery = LLM leftover (or original q if leftover blank)

3. Hard SQL via InvoiceSpecifications (must match)

4. Soft rank:
   → VectorStore.similaritySearch(semanticQuery)
   → Keep only hits whose invoiceId is in the SQL-matched set
     (or: load by vector ids then apply same specs — equivalent)
   → Preserve similarity order, apply limit

5. Return InvoiceResponse[] (with similarityScore when vectors ran)
```

### LLM extraction schema (illustrative)

```json
{
  "supplier": "Acme",
  "city": "Amsterdam",
  "minAmount": 500,
  "maxAmount": null,
  "currency": "EUR",
  "fromDate": null,
  "toDate": null,
  "paid": null,
  "updated": null,
  "semanticQuery": "Acme Amsterdam"
}
```

- Omit or null any field the model cannot confidently extract.
- `semanticQuery`: short text for embedding rank (entities / topic), without filler (“invoices from”, “over … euro”) when those became filters.
- Amount/currency parsing: accept common phrasing (“over 500”, “500 euro”, “€500”) into `minAmount` / `currency` where possible.

### Criteria / SQL additions

Extend search criteria with:

- `supplier` — `LOWER(supplier) LIKE %value%`
- `city` — `LOWER(supplierCity) LIKE %value%`

Keep existing: `minAmount`, `maxAmount`, `currency`, `fromDate`, `toDate`, and (if already present on the branch base) `paid` / `updated` / `limit`.

### Failure behavior

- LLM parse failure: log and fall back to vector search on raw `q` + UI filters (do not fail the whole request).
- Empty after hard filters: return `[]`.
- Orphan vectors (in vector table, missing invoice row): skip hit (same as today).

## API

`GET /ai/invoices/search`

Unchanged query params from the client’s perspective (`q`, amount/currency/date filters, `limit`, etc.).  
Optional later: expose `supplier` / `city` as explicit query params for the UI; v1 can populate them only via LLM from `q`.

Response: ranked `InvoiceResponse[]` (unchanged shape).

## Frontend

- Keep the free-text semantic search box as the primary NL entry.
- Keep existing structured filter controls; they remain hard filters and override LLM when set.
- No required UI change for v1 beyond verifying search still works with interpreted queries.
- Optional later: show “interpreted filters” chips (supplier, city, amount) for transparency — not required for v1.

## Testing

- Unit: merge rules (UI wins over LLM); criteria → specification predicates for supplier/city contains.
- Unit: search service — filtered set ∩ vector hits; empty filters; LLM failure fallback (mocked ChatClient).
- Unit/integration: example query interpretation fixture  
  `"invoices from Acme in Amsterdam over 500 euro"`  
  → supplier contains Acme, city contains Amsterdam, minAmount 500, currency EUR.

## Base branch note

Implementation branch: `feature/llm-query-interpreted-search` from `main`.  
If useful pieces from `feature/invoice-search-filters` (paged SQL filters, paid/updated, UUID document ids) are not yet on `main`, reintroduce or cherry-pick them as prerequisites in the implementation plan.

## Success criteria

- Query above returns only invoices that match Acme (contains), Amsterdam (contains), amount ≥ 500, and EUR when currency was inferred — not merely “invoice-shaped” vector neighbors.
- Ranking among survivors still uses embedding similarity when a semantic query remains.
- Blank `q` with UI filters still works without calling the LLM.
