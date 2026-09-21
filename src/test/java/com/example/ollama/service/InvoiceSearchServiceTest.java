package com.example.ollama.service;

import com.example.ollama.dto.InvoiceQueryInterpretation;
import com.example.ollama.dto.InvoiceSearchCriteria;
import com.example.ollama.dto.InvoiceSearchHit;
import com.example.ollama.entity.Invoice;
import com.example.ollama.repo.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceSearchServiceTest {

	@Mock private VectorStore vectorStore;
	@Mock private InvoiceRepository invoiceRepository;
	@Mock private InvoiceQueryInterpreter invoiceQueryInterpreter;
	private InvoiceSearchService service;

	@BeforeEach
	void setUp() {
		service = new InvoiceSearchService(vectorStore, invoiceRepository, invoiceQueryInterpreter, 0.3);
	}

	@Test
	void searchInvoices_interpretsQuery_thenMerges_thenSearches() {
		var ui = new InvoiceSearchCriteria("invoices from Acme in Amsterdam over 500 euro",
				null, null, null, null, null, null, null, null, null, 25);
		var interpretation = new InvoiceQueryInterpretation(
				"Acme", "Amsterdam", new BigDecimal("500"), null, "EUR",
				null, null, null, null, "Acme Amsterdam");
		when(invoiceQueryInterpreter.interpret(ui.semanticQuery())).thenReturn(Optional.of(interpretation));

		var expectedMerged = InvoiceSearchCriteriaMerger.merge(ui, interpretation);
		when(invoiceRepository.findMatchingCandidates(any())).thenReturn(List.of());

		service.searchInvoices(ui);

		verify(invoiceQueryInterpreter).interpret(ui.semanticQuery());
		verify(invoiceRepository).findMatchingCandidates(expectedMerged);
		verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
		assertThat(expectedMerged.supplier()).isEqualTo("Acme");
	}

	@Test
	void searchInvoices_onInterpretFailure_searchesWithUiCriteria() {
		var ui = new InvoiceSearchCriteria("acme", null, null, null, null, null, null, null, null, null, 25);
		when(invoiceQueryInterpreter.interpret("acme")).thenReturn(Optional.empty());
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		service.searchInvoices(ui);

		verify(invoiceQueryInterpreter).interpret("acme");
		verify(vectorStore).similaritySearch(any(SearchRequest.class));
	}

	@Test
	void searchInvoices_blankQuery_skipsInterpreter() {
		var ui = new InvoiceSearchCriteria(null, new BigDecimal("10"), null, "EUR",
				null, null, null, null, null, null, 25);
		when(invoiceRepository.findMatching(ui)).thenReturn(List.of());

		service.searchInvoices(ui);

		verify(invoiceQueryInterpreter, never()).interpret(any());
		verify(invoiceRepository).findMatching(ui);
		verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
	}

	@Test
	void search_withQuery_returnsResultsInScoreOrder() {
		Document lowerScore = Document.builder()
				.id(InvoiceEmbeddingService.createDocumentId(2L))
				.text("Supplier: Bright Office Supplies Ltd")
				.metadata(InvoiceEmbeddingService.METADATA_INVOICE_ID, "2")
				.score(0.42)
				.build();
		Document higherScore = Document.builder()
				.id(InvoiceEmbeddingService.createDocumentId(1L))
				.text("Supplier: Acme Corp")
				.metadata(InvoiceEmbeddingService.METADATA_INVOICE_ID, "1")
				.score(0.81)
				.build();

		when(vectorStore.similaritySearch(any(SearchRequest.class)))
				.thenReturn(List.of(higherScore, lowerScore));
		when(invoiceRepository.findMatchingByIds(any(), any()))
				.thenReturn(List.of(sample(1L), sample(2L)));

		List<InvoiceSearchHit> results = service.search(
				new InvoiceSearchCriteria("acme corp", null, null, null, null, null, null, null, null, null, 25)
		);

		assertThat(results)
				.extracting(hit -> hit.invoice().getId())
				.containsExactly(1L, 2L);
	}

	@Test
	void search_withoutQuery_usesFindMatchingWithLimit() {
		Invoice invoice = sample(1L);
		var criteria = new InvoiceSearchCriteria(null, null, null, null, null, null, null, null, null, null, 25);
		when(invoiceRepository.findMatching(criteria)).thenReturn(List.of(invoice));

		List<InvoiceSearchHit> results = service.search(criteria);

		assertThat(results).hasSize(1);
		verify(invoiceRepository).findMatching(criteria);
		verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
	}

	@Test
	void search_withFiltersOnly_usesSqlFiltersAndSkipsVectorStore() {
		Invoice eur = sample(1L, new BigDecimal("150.00"), "EUR", LocalDate.of(2024, 6, 1));
		var criteria = new InvoiceSearchCriteria(
				null, new BigDecimal("100"), null, "EUR",
				LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31),
				null, null, null, null, 25);
		when(invoiceRepository.findMatching(criteria)).thenReturn(List.of(eur));

		List<InvoiceSearchHit> results = service.search(criteria);

		assertThat(results).extracting(hit -> hit.invoice().getId()).containsExactly(1L);
		assertThat(results.getFirst().similarityScore()).isNull();
		verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
	}

	@Test
	void search_withQueryAndFilters_ranksOnlySqlCandidates() {
		Document first = Document.builder()
				.id(InvoiceEmbeddingService.createDocumentId(1L))
				.text("Supplier: Acme Corp")
				.metadata(InvoiceEmbeddingService.METADATA_INVOICE_ID, "1")
				.score(0.9)
				.build();
		Invoice matching = sample(1L, new BigDecimal("150.00"), "EUR", LocalDate.of(2024, 6, 1));
		var criteria = new InvoiceSearchCriteria(
				"office supplies", new BigDecimal("100"), null, "EUR",
				null, null, null, null, null, null, 25);
		when(invoiceRepository.findMatchingCandidates(criteria)).thenReturn(List.of(matching));
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(first));

		List<InvoiceSearchHit> results = service.search(criteria);

		assertThat(results).hasSize(1);
		assertThat(results.getFirst().invoice().getId()).isEqualTo(1L);
		assertThat(results.getFirst().similarityScore()).isEqualTo(0.9);

		ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
		verify(vectorStore).similaritySearch(request.capture());
		assertThat(request.getValue().getFilterExpression()).isNotNull();
		assertThat(request.getValue().getTopK()).isEqualTo(25);
	}

	@Test
	void search_withQueryAndFilters_doesNotMissSqlCandidateOutsideGlobalTopK() {
		Invoice eurMatch = sample(1L, new BigDecimal("150.00"), "EUR", LocalDate.of(2024, 6, 1));
		var criteria = new InvoiceSearchCriteria(
				"plumbing supplies", null, null, "EUR",
				null, null, null, null, null, null, 10);
		when(invoiceRepository.findMatchingCandidates(criteria)).thenReturn(List.of(eurMatch));

		Document rankedWithinFilter = Document.builder()
				.id(InvoiceEmbeddingService.createDocumentId(1L))
				.text("Supplier: Acme Plumbing")
				.metadata(InvoiceEmbeddingService.METADATA_INVOICE_ID, "1")
				.score(0.51)
				.build();
		when(vectorStore.similaritySearch(any(SearchRequest.class)))
				.thenReturn(List.of(rankedWithinFilter));

		List<InvoiceSearchHit> results = service.search(criteria);

		assertThat(results).singleElement().satisfies(hit -> {
			assertThat(hit.invoice().getId()).isEqualTo(1L);
			assertThat(hit.similarityScore()).isEqualTo(0.51);
		});
		verify(invoiceRepository).findMatchingCandidates(criteria);
		verify(invoiceRepository, never()).findMatchingByIds(any(), any());

		ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
		verify(vectorStore).similaritySearch(request.capture());
		assertThat(request.getValue().getFilterExpression()).isNotNull();
	}

	@Test
	void search_withQueryAndFilters_fallsBackToSqlMatchesWhenNoVectorHits() {
		var criteria = new InvoiceSearchCriteria(
				"office supplies", new BigDecimal("100"), null, "EUR",
				null, null, null, null, null, null, 25);
		Invoice sqlMatch = sample(1L, new BigDecimal("150.00"), "EUR", LocalDate.of(2024, 6, 1));

		when(invoiceRepository.findMatchingCandidates(criteria)).thenReturn(List.of(sqlMatch));
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		List<InvoiceSearchHit> results = service.search(criteria);

		assertThat(results).singleElement().satisfies(hit -> {
			assertThat(hit.invoice().getId()).isEqualTo(1L);
			assertThat(hit.similarityScore()).isNull();
		});
		verify(invoiceRepository).findMatchingCandidates(criteria);
		verify(invoiceRepository, never()).findMatching(criteria);
	}

	private Invoice sample(Long id) {
		return sample(id, "Acme Corp", new BigDecimal("99.90"), "EUR", LocalDate.of(2024, 3, 12));
	}

	private Invoice sample(Long id, BigDecimal amount, String currency, LocalDate invoiceDate) {
		return sample(id, "Acme Corp", amount, currency, invoiceDate);
	}

	private Invoice sample(Long id, String supplier, BigDecimal amount, String currency, LocalDate invoiceDate) {
		return new Invoice(
				id, supplier, "Main Street", "42A", "Amsterdam", "Netherlands", "1012 AB",
				"INV-001", invoiceDate, amount, currency,
				Instant.parse("2026-09-15T10:00:00Z"), null, null
		);
	}
}
