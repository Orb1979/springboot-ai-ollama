# LLM-Interpreted Invoice Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Parse natural-language invoice search queries with an LLM into hard SQL filters (supplier, city, amount, currency, …), then rank survivors with vector search.

**Architecture:** When `q` is present, `InvoiceQueryInterpreter` uses `ChatClient` + schema-bound JSON to produce structured fields + optional `semanticQuery`. Merge with UI params (UI wins). Apply hard SQL via `InvoiceSpecifications`, then vector-rank within that set. On LLM failure, fall back to raw `q` + UI filters.

**Tech Stack:** Spring Boot, Spring AI `ChatClient` / `BeanOutputConverter`, JPA Specifications, pgvector `VectorStore`, JUnit 5 + Mockito, existing React search UI (no required UI change in v1).

## Global Constraints

- Hard filters must match (contains / case-insensitive for supplier and city).
- UI filter params override LLM-extracted values when both are set.
- LLM parse failure must not fail the HTTP request — log and fall back.
- Reuse `@Qualifier("generalClient") ChatClient` (same as invoice extraction).
- Out of scope: fuzzy typos, synonym tables, interpreted-filter chips in UI.
- Branch: `feature/llm-query-interpreted-search` (already created from `main`).

## File Structure

| File | Responsibility |
|---|---|
| `dto/InvoiceQueryInterpretation.java` | LLM JSON output (nullable structured fields + `semanticQuery`) |
| `dto/InvoiceSearchCriteria.java` | Search criteria including `supplier`, `city`, filters, `semanticQuery`/`limit` |
| `service/InvoiceQueryInterpreter.java` | Prompt + ChatClient → `InvoiceQueryInterpretation`; safe failure |
| `service/InvoiceSearchCriteriaMerger.java` | Merge LLM interpretation + UI criteria (UI wins) |
| `repo/InvoiceSpecifications.java` | Dynamic SQL predicates including supplier/city contains |
| `repo/InvoiceRepository.java` | `findMatching` / `findMatchingByIds` via Specifications + page limit |
| `service/InvoiceEmbeddingService.java` | SQL-only path + semantic path (vector ∩ SQL) |
| `service/InvoiceAnalyzerService.java` | Orchestrate: interpret (if q) → merge → `embeddingService.search` |
| `controller/InvoiceController.java` | Pass request params into criteria; default limit 25 |
| Tests under `src/test/java/...` | Unit tests per component |

---

### Task 1: Bring SQL filter foundation onto this branch

**Files:**
- Merge from: `feature/invoice-search-filters` (already has Specifications, paid/updated, limit 25, UUID doc ids, frontend search-on-mount, renamed `semanticQuery`)
- Resolve conflicts with design commit on current branch

**Interfaces:**
- Produces: working `InvoiceSearchCriteria` with `semanticQuery`, amount/currency/date/paid/updated/limit; `InvoiceSpecifications`; `InvoiceRepository.findMatching` / `findMatchingByIds`; embedding search that applies SQL after vectors

- [ ] **Step 1: Merge prerequisite branch**

```bash
git checkout feature/llm-query-interpreted-search
git merge feature/invoice-search-filters
# Resolve conflicts if any; keep design doc from this branch
```

- [ ] **Step 2: Verify unit tests pass**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit merge (if not already committed by merge)**

```bash
git status
# If merge created a commit automatically, skip; otherwise:
git add -A
git commit -m "Merge feature/invoice-search-filters into LLM search branch"
```

---

### Task 2: Extend criteria + SQL with supplier and city (contains)

**Files:**
- Modify: `src/main/java/com/example/ollama/dto/InvoiceSearchCriteria.java`
- Modify: `src/main/java/com/example/ollama/repo/InvoiceSpecifications.java`
- Modify: `src/main/java/com/example/ollama/controller/InvoiceController.java` (optional query params `supplier`, `city` for merge/testing)
- Modify: `src/test/java/com/example/ollama/dto/InvoiceSearchCriteriaTest.java`
- Modify: `src/test/java/com/example/ollama/repo/InvoiceSpecificationsTest.java`

**Interfaces:**
- Consumes: existing criteria record + Specifications
- Produces: `InvoiceSearchCriteria(..., String supplier, String city, ...)` with `hasFilters()` including supplier/city; predicates `LOWER(supplier) LIKE %x%` and `LOWER(supplierCity) LIKE %x%`

- [ ] **Step 1: Write failing criteria tests**

Add to `InvoiceSearchCriteriaTest.java`:

```java
@Test
void hasFilters_isTrueForSupplierOrCity() {
	assertThat(new InvoiceSearchCriteria(null, null, null, null, null, null, null, null, "Acme", null, 25).hasFilters())
			.isTrue();
	assertThat(new InvoiceSearchCriteria(null, null, null, null, null, null, null, null, null, "Amsterdam", 25).hasFilters())
			.isTrue();
}
```

(Adjust constructor arg order to match the record after you add `supplier` and `city` — place them after `updated` and before `limit`, or after dates; keep one consistent order across the plan:  
`semanticQuery, minAmount, maxAmount, currency, fromDate, toDate, paid, updated, supplier, city, limit`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.example.ollama.dto.InvoiceSearchCriteriaTest`
Expected: FAIL (constructor / `hasFilters` missing supplier/city)

- [ ] **Step 3: Extend record + Specifications**

In `InvoiceSearchCriteria`, add `String supplier`, `String city`; include them in `hasFilters()`; blank → null in compact constructor.

In `InvoiceSpecifications.matching`:

```java
if (criteria.supplier() != null) {
	predicates.add(cb.like(
			cb.lower(root.get("supplier")),
			"%" + criteria.supplier().toLowerCase(Locale.ROOT) + "%"
	));
}
if (criteria.city() != null) {
	predicates.add(cb.like(
			cb.lower(root.get("supplierCity")),
			"%" + criteria.city().toLowerCase(Locale.ROOT) + "%"
	));
}
```

Update controller to accept optional `@RequestParam String supplier, String city` and pass into criteria. Update all `new InvoiceSearchCriteria(...)` call sites/tests.

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests com.example.ollama.dto.InvoiceSearchCriteriaTest --tests com.example.ollama.repo.InvoiceSpecificationsTest --tests com.example.ollama.service.InvoiceEmbeddingServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/ollama/dto/InvoiceSearchCriteria.java \
  src/main/java/com/example/ollama/repo/InvoiceSpecifications.java \
  src/main/java/com/example/ollama/controller/InvoiceController.java \
  src/test/java/com/example/ollama/dto/InvoiceSearchCriteriaTest.java \
  src/test/java/com/example/ollama/repo/InvoiceSpecificationsTest.java \
  src/test/java/com/example/ollama/service/InvoiceEmbeddingServiceTest.java
git commit -m "Add supplier and city contains filters to invoice search"
```

---

### Task 3: InvoiceQueryInterpretation DTO + merger (UI wins)

**Files:**
- Create: `src/main/java/com/example/ollama/dto/InvoiceQueryInterpretation.java`
- Create: `src/main/java/com/example/ollama/service/InvoiceSearchCriteriaMerger.java`
- Create: `src/test/java/com/example/ollama/service/InvoiceSearchCriteriaMergerTest.java`

**Interfaces:**
- Produces:
  - `record InvoiceQueryInterpretation(String supplier, String city, BigDecimal minAmount, BigDecimal maxAmount, String currency, LocalDate fromDate, LocalDate toDate, Boolean paid, Boolean updated, String semanticQuery)`
  - `InvoiceSearchCriteriaMerger.merge(InvoiceSearchCriteria ui, InvoiceQueryInterpretation llm) → InvoiceSearchCriteria`

- [ ] **Step 1: Write failing merger tests**

```java
@Test
void uiAmount_overridesLlmAmount() {
	var ui = new InvoiceSearchCriteria("raw", new BigDecimal("100"), null, null, null, null, null, null, null, null, 25);
	var llm = new InvoiceQueryInterpretation("Acme", "Amsterdam", new BigDecimal("500"), null, "EUR", null, null, null, null, "Acme Amsterdam");
	InvoiceSearchCriteria merged = InvoiceSearchCriteriaMerger.merge(ui, llm);
	assertThat(merged.minAmount()).isEqualByComparingTo("100");
	assertThat(merged.supplier()).isEqualTo("Acme");
	assertThat(merged.city()).isEqualTo("Amsterdam");
	assertThat(merged.currency()).isEqualTo("EUR");
	assertThat(merged.semanticQuery()).isEqualTo("Acme Amsterdam");
}

@Test
void blankLlmSemanticQuery_fallsBackToUiQuery() {
	var ui = new InvoiceSearchCriteria("invoices from Acme", null, null, null, null, null, null, null, null, null, 25);
	var llm = new InvoiceQueryInterpretation("Acme", null, null, null, null, null, null, null, null, "  ");
	assertThat(InvoiceSearchCriteriaMerger.merge(ui, llm).semanticQuery()).isEqualTo("invoices from Acme");
}

@Test
void nullInterpretation_returnsUiUnchanged() {
	var ui = new InvoiceSearchCriteria("q", null, null, "EUR", null, null, null, null, null, null, 25);
	assertThat(InvoiceSearchCriteriaMerger.merge(ui, null)).isEqualTo(ui);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.example.ollama.service.InvoiceSearchCriteriaMergerTest`
Expected: FAIL (classes missing)

- [ ] **Step 3: Implement DTO + merger**

```java
public final class InvoiceSearchCriteriaMerger {
	private InvoiceSearchCriteriaMerger() {}

	public static InvoiceSearchCriteria merge(InvoiceSearchCriteria ui, InvoiceQueryInterpretation llm) {
		if (llm == null) {
			return ui;
		}
		String semantic = blankToNull(llm.semanticQuery());
		if (semantic == null) {
			semantic = ui.semanticQuery();
		}
		return new InvoiceSearchCriteria(
				semantic,
				firstNonNull(ui.minAmount(), llm.minAmount()),
				firstNonNull(ui.maxAmount(), llm.maxAmount()),
				firstNonNull(blankToNull(ui.currency()), blankToNull(llm.currency())),
				firstNonNull(ui.fromDate(), llm.fromDate()),
				firstNonNull(ui.toDate(), llm.toDate()),
				firstNonNull(ui.paid(), llm.paid()),
				firstNonNull(ui.updated(), llm.updated()),
				firstNonNull(blankToNull(ui.supplier()), blankToNull(llm.supplier())),
				firstNonNull(blankToNull(ui.city()), blankToNull(llm.city())),
				ui.limit()
		);
	}

	private static <T> T firstNonNull(T ui, T llm) {
		return ui != null ? ui : llm;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
```

- [ ] **Step 4: Run tests — expect PASS**

Run: `./gradlew test --tests com.example.ollama.service.InvoiceSearchCriteriaMergerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/ollama/dto/InvoiceQueryInterpretation.java \
  src/main/java/com/example/ollama/service/InvoiceSearchCriteriaMerger.java \
  src/test/java/com/example/ollama/service/InvoiceSearchCriteriaMergerTest.java
git commit -m "Add LLM query interpretation DTO and UI-wins criteria merger"
```

---

### Task 4: InvoiceQueryInterpreter (ChatClient)

**Files:**
- Create: `src/main/java/com/example/ollama/service/InvoiceQueryInterpreter.java`
- Create: `src/test/java/com/example/ollama/service/InvoiceQueryInterpreterTest.java`

**Interfaces:**
- Consumes: `@Qualifier("generalClient") ChatClient`
- Produces: `Optional<InvoiceQueryInterpretation> interpret(String rawQuery)` — empty on failure

- [ ] **Step 1: Write failing interpreter tests**

```java
@ExtendWith(MockitoExtension.class)
class InvoiceQueryInterpreterTest {
	@Mock ChatClient chatClient;
	// Mock the fluent chain: prompt() → system() → user() → call() → content()
	// Or mock ChatClient to throw / return bad JSON

	@Test
	void interpret_returnsEmpty_onChatFailure() {
		when(chatClient.prompt()).thenThrow(new RuntimeException("boom"));
		var interpreter = new InvoiceQueryInterpreter(chatClient);
		assertThat(interpreter.interpret("invoices from Acme in Amsterdam over 500 euro")).isEmpty();
	}
}
```

(Use the same fluent mock pattern already used elsewhere in the project if present; otherwise stub the chain with Mockito `RETURNS_DEEP_STUBS` on `ChatClient`.)

- [ ] **Step 2: Run test — expect FAIL**

Run: `./gradlew test --tests com.example.ollama.service.InvoiceQueryInterpreterTest`
Expected: FAIL (class missing)

- [ ] **Step 3: Implement interpreter**

Mirror `InvoiceAnalyzerService` extraction style:

```java
@Service
@Log4j2
public class InvoiceQueryInterpreter {
	private static final BeanOutputConverter<InvoiceQueryInterpretation> OUTPUT =
			new BeanOutputConverter<>(InvoiceQueryInterpretation.class);

	private static final String SYSTEM = """
			Extract invoice search filters from the user query.
			Return only fields you can infer confidently; otherwise null.
			- supplier: company or person name fragment
			- city: city name
			- minAmount / maxAmount: numeric bounds ("over 500" → minAmount 500)
			- currency: ISO code when stated (euro → EUR)
			- fromDate / toDate: ISO-8601 dates if present
			- paid / updated: true only if user clearly asks for paid or updated invoices
			- semanticQuery: short text for similarity ranking (names/places/topics),
			  without filler phrases or constraints already captured as filters
			""";

	private final ChatClient chatClient;

	public InvoiceQueryInterpreter(@Qualifier("generalClient") ChatClient chatClient) {
		this.chatClient = chatClient;
	}

	public Optional<InvoiceQueryInterpretation> interpret(String rawQuery) {
		if (rawQuery == null || rawQuery.isBlank()) {
			return Optional.empty();
		}
		try {
			String raw = chatClient.prompt()
					.system(SYSTEM)
					.user(rawQuery + "\n\n" + OUTPUT.getFormat())
					.call()
					.content();
			if (raw == null || raw.isBlank()) {
				return Optional.empty();
			}
			return Optional.ofNullable(OUTPUT.convert(raw));
		} catch (RuntimeException ex) {
			log.warn("Failed to interpret search query '{}': {}", rawQuery, ex.getMessage());
			return Optional.empty();
		}
	}
}
```

- [ ] **Step 4: Run tests — expect PASS**

Run: `./gradlew test --tests com.example.ollama.service.InvoiceQueryInterpreterTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/ollama/service/InvoiceQueryInterpreter.java \
  src/test/java/com/example/ollama/service/InvoiceQueryInterpreterTest.java
git commit -m "Add ChatClient-based invoice search query interpreter"
```

---

### Task 5: Wire interpret → merge → search in analyzer service

**Files:**
- Modify: `src/main/java/com/example/ollama/service/InvoiceAnalyzerService.java`
- Modify: `src/test/java/com/example/ollama/service/InvoiceAnalyzerServiceTest.java`

**Interfaces:**
- Consumes: `InvoiceQueryInterpreter`, `InvoiceSearchCriteriaMerger`, `InvoiceEmbeddingService.search`
- Produces: `searchInvoices(uiCriteria)` that interprets when `hasSemanticQuery()`, merges, then searches

- [ ] **Step 1: Write failing orchestration test**

```java
@Test
void searchInvoices_interpretsQuery_thenMerges_thenDelegates() {
	var ui = new InvoiceSearchCriteria("invoices from Acme in Amsterdam over 500 euro",
			null, null, null, null, null, null, null, null, null, 25);
	var interpretation = new InvoiceQueryInterpretation(
			"Acme", "Amsterdam", new BigDecimal("500"), null, "EUR",
			null, null, null, null, "Acme Amsterdam");
	when(invoiceQueryInterpreter.interpret(ui.semanticQuery())).thenReturn(Optional.of(interpretation));

	var expectedMerged = InvoiceSearchCriteriaMerger.merge(ui, interpretation);
	when(invoiceEmbeddingService.search(expectedMerged)).thenReturn(List.of());

	invoiceAnalyzerService.searchInvoices(ui);

	verify(invoiceEmbeddingService).search(expectedMerged);
}

@Test
void searchInvoices_onInterpretFailure_searchesWithUiCriteria() {
	var ui = new InvoiceSearchCriteria("acme", null, null, null, null, null, null, null, null, null, 25);
	when(invoiceQueryInterpreter.interpret("acme")).thenReturn(Optional.empty());
	when(invoiceEmbeddingService.search(ui)).thenReturn(List.of());

	invoiceAnalyzerService.searchInvoices(ui);

	verify(invoiceEmbeddingService).search(ui);
}

@Test
void searchInvoices_blankQuery_skipsInterpreter() {
	var ui = new InvoiceSearchCriteria(null, new BigDecimal("10"), null, "EUR", null, null, null, null, null, null, 25);
	when(invoiceEmbeddingService.search(ui)).thenReturn(List.of());

	invoiceAnalyzerService.searchInvoices(ui);

	verify(invoiceQueryInterpreter, never()).interpret(any());
	verify(invoiceEmbeddingService).search(ui);
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `./gradlew test --tests com.example.ollama.service.InvoiceAnalyzerServiceTest`
Expected: FAIL (missing collaborator / method behavior)

- [ ] **Step 3: Implement orchestration**

```java
public List<InvoiceSearchHit> searchInvoices(InvoiceSearchCriteria uiCriteria) {
	if (!uiCriteria.hasSemanticQuery()) {
		return invoiceEmbeddingService.search(uiCriteria);
	}
	InvoiceSearchCriteria merged = invoiceQueryInterpreter.interpret(uiCriteria.semanticQuery())
			.map(interpretation -> InvoiceSearchCriteriaMerger.merge(uiCriteria, interpretation))
			.orElse(uiCriteria);
	return invoiceEmbeddingService.search(merged);
}
```

Inject `InvoiceQueryInterpreter` in the constructor (update tests’ `@Mock` + constructor args).

- [ ] **Step 4: Ensure embedding search intersects vectors with SQL**

Confirm `InvoiceEmbeddingService.semanticSearch` already uses `findMatchingByIds(invoiceIds, criteria)` (from Task 1). If not, change `findAllById` to `findMatchingByIds` and truncate with `.limit(criteria.limit())`.

Also ensure SQL-only path uses `findMatching(criteria)` when there is no semantic query (filters-only / default list).

- [ ] **Step 5: Run tests — expect PASS**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/ollama/service/InvoiceAnalyzerService.java \
  src/main/java/com/example/ollama/service/InvoiceEmbeddingService.java \
  src/test/java/com/example/ollama/service/InvoiceAnalyzerServiceTest.java \
  src/test/java/com/example/ollama/service/InvoiceEmbeddingServiceTest.java
git commit -m "Wire LLM query interpretation into invoice search flow"
```

---

### Task 6: Manual smoke checklist (no new frontend required)

**Files:** none required (optional: document in PR)

- [ ] **Step 1: Start app + ensure invoices indexed**

Restart Spring Boot after pull. Confirm Ollama/OpenAI chat + embedding providers work.

- [ ] **Step 2: Exercise example query**

Call:

```bash
curl -s 'http://localhost:8080/ai/invoices/search?q=invoices%20from%20Acme%20in%20Amsterdam%20over%20500%20euro&limit=25'
```

Expected: only invoices matching supplier contains Acme, city contains Amsterdam, amount ≥ 500, currency EUR (when inferred).

- [ ] **Step 3: Exercise UI override**

```bash
curl -s 'http://localhost:8080/ai/invoices/search?q=invoices%20from%20Acme%20over%20500%20euro&minAmount=900&limit=25'
```

Expected: `minAmount=900` wins over LLM’s 500.

- [ ] **Step 4: Blank q with filters**

```bash
curl -s 'http://localhost:8080/ai/invoices/search?currency=EUR&limit=25'
```

Expected: SQL-only, no LLM call (check logs).

- [ ] **Step 5: Commit nothing unless you added notes; open PR when ready**

---

## Spec coverage (self-review)

| Spec requirement | Task |
|---|---|
| LLM → structured filters | 4, 5 |
| Hard SQL filters | 1, 2, 5 |
| Supplier/city contains | 2 |
| Vector rank after filters | 1, 5 |
| UI overrides LLM | 3, 5 |
| Fallback on LLM failure | 4, 5 |
| Blank q skips LLM | 5 |
| No required frontend chips | 6 (verify only) |
| Prerequisite filters/UUID/limit from other branch | 1 |

## Placeholder scan

None intentional. Constructor argument order must stay consistent:  
`semanticQuery, minAmount, maxAmount, currency, fromDate, toDate, paid, updated, supplier, city, limit`.
