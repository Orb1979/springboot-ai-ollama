package com.example.ollama.service;

import com.example.ollama.dto.InvoiceSearchCriteria;
import com.example.ollama.dto.InvoiceSearchHit;
import com.example.ollama.entity.Invoice;
import com.example.ollama.exception.InvoiceAnalyzeException;
import com.example.ollama.repo.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceEmbeddingServiceTest {

	@Mock private VectorStore vectorStore;
	@Mock private InvoiceRepository invoiceRepository;
	InvoiceEmbeddingService service;

	@BeforeEach
	void setUp() {
		service = new InvoiceEmbeddingService(vectorStore,invoiceRepository, 0.3);
	}

	@Test
	void toSummary_includesStructuredFields() {
		Invoice invoice = sample(1L);

		assertThat(InvoiceEmbeddingService.toSummary(invoice))
				.contains("Acme Corp")
				.contains("Main Street 42A")
				.contains("INV-001")
				.contains("99.90")
				.contains("EUR");
	}

	@Test
	void indexInvoice_deletesThenAddsDocumentWithInvoiceMetadata() {
		Invoice invoice = sample(42L);

		service.indexInvoice(invoice);

		ArgumentCaptor<List<Document>> docs = ArgumentCaptor.captor();
		verify(vectorStore).delete(List.of(InvoiceEmbeddingService.createDocumentId(42L)));
		verify(vectorStore).add(docs.capture());
		Document document = docs.getValue().getFirst();
		assertThat(document.getId()).isEqualTo(InvoiceEmbeddingService.createDocumentId(42L));
		assertThat(document.getText()).contains("Acme Corp");
		assertThat(document.getMetadata().get(InvoiceEmbeddingService.METADATA_INVOICE_ID))
				.isEqualTo("42");
	}

	@Test
	void indexInvoice_wrapsVectorStoreFailures() {
		Invoice invoice = sample(1L);
		org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(vectorStore).add(anyList());

		assertThatThrownBy(() -> service.indexInvoice(invoice))
				.isInstanceOf(InvoiceAnalyzeException.class)
				.hasMessageContaining("Failed to index invoice 1");
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

		String query = "acme corp";

		List<InvoiceSearchHit> results = service.search(
				new InvoiceSearchCriteria(query, null, null, null, null, null, null, null, null, null, 25)
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
		verify(invoiceRepository, never()).findAll();
		verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
	}

	@Test
	void search_withFiltersOnly_usesSqlFiltersAndSkipsVectorStore() {
		Invoice eur = sample(1L, new BigDecimal("150.00"), "EUR", LocalDate.of(2024, 6, 1));
		var criteria = new InvoiceSearchCriteria(
				null,
				new BigDecimal("100"),
				null,
				"EUR",
				LocalDate.of(2024, 1, 1),
				LocalDate.of(2024, 12, 31),
				null,
				null,
				null,
				null,
				25
		);

		when(invoiceRepository.findMatching(criteria)).thenReturn(List.of(eur));

		List<InvoiceSearchHit> results = service.search(criteria);

		assertThat(results)
				.extracting(hit -> hit.invoice().getId())
				.containsExactly(1L);
		assertThat(results.getFirst().similarityScore()).isNull();
		verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
		verify(invoiceRepository, never()).findAll();
	}

	@Test
	void search_withQueryAndFilters_keepsScoreOrderButDropsNonMatching() {
		Document first = Document.builder()
				.id(InvoiceEmbeddingService.createDocumentId(1L))
				.text("Supplier: Acme Corp")
				.metadata(InvoiceEmbeddingService.METADATA_INVOICE_ID, "1")
				.score(0.9)
				.build();
		Document second = Document.builder()
				.id(InvoiceEmbeddingService.createDocumentId(2L))
				.text("Supplier: Bright Office Supplies Ltd")
				.metadata(InvoiceEmbeddingService.METADATA_INVOICE_ID, "2")
				.score(0.8)
				.build();

		when(vectorStore.similaritySearch(any(SearchRequest.class)))
				.thenReturn(List.of(first, second));

		Invoice matching = sample(1L, new BigDecimal("150.00"), "EUR", LocalDate.of(2024, 6, 1));
		var criteria = new InvoiceSearchCriteria(
				"office supplies",
				new BigDecimal("100"),
				null,
				"EUR",
				null,
				null,
				null,
				null,
				null,
				null,
				25
		);

		when(invoiceRepository.findMatchingByIds(List.of(1L, 2L), criteria))
				.thenReturn(List.of(matching));

		List<InvoiceSearchHit> results = service.search(criteria);

		assertThat(results).hasSize(1);
		assertThat(results.getFirst().invoice().getId()).isEqualTo(1L);
		assertThat(results.getFirst().similarityScore()).isEqualTo(0.9);
	}

	@Test
	void search_withQueryAndFilters_fallsBackToSqlMatchesWhenSemanticIntersectionIsEmpty() {
		Document semanticMatch = Document.builder()
				.id(InvoiceEmbeddingService.createDocumentId(2L))
				.text("Supplier: Bright Office Supplies Ltd")
				.metadata(InvoiceEmbeddingService.METADATA_INVOICE_ID, "2")
				.score(0.8)
				.build();
		var criteria = new InvoiceSearchCriteria(
				"office supplies",
				new BigDecimal("100"),
				null,
				"EUR",
				null,
				null,
				null,
				null,
				null,
				null,
				25
		);
		Invoice sqlMatch = sample(1L, new BigDecimal("150.00"), "EUR", LocalDate.of(2024, 6, 1));

		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(semanticMatch));
		when(invoiceRepository.findMatchingByIds(List.of(2L), criteria)).thenReturn(List.of());
		when(invoiceRepository.findMatching(criteria)).thenReturn(List.of(sqlMatch));

		List<InvoiceSearchHit> results = service.search(criteria);

		assertThat(results).singleElement().satisfies(hit -> {
			assertThat(hit.invoice().getId()).isEqualTo(1L);
			assertThat(hit.similarityScore()).isNull();
		});
		verify(invoiceRepository).findMatching(criteria);
	}

	private Invoice sample(Long id) {
		return sample(id, "Acme Corp", new BigDecimal("99.90"), "EUR", LocalDate.of(2024, 3, 12));
	}

	private Invoice sample(Long id, BigDecimal amount, String currency, LocalDate invoiceDate) {
		return sample(id, "Acme Corp", amount, currency, invoiceDate);
	}

	private Invoice sample(Long id, String supplier, BigDecimal amount, String currency, LocalDate invoiceDate) {
		return new Invoice(
				id,
				supplier,
				"Main Street",
				"42A",
				"Amsterdam",
				"Netherlands",
				"1012 AB",
				"INV-001",
				invoiceDate,
				amount,
				currency,
				Instant.parse("2026-09-15T10:00:00Z"),
				null,
				null
		);
	}
}
