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
		verify(vectorStore).delete(List.of(InvoiceEmbeddingService.documentIdFor(42L)));
		verify(vectorStore).add(docs.capture());
		Document document = docs.getValue().getFirst();
		assertThat(document.getId()).isEqualTo(InvoiceEmbeddingService.documentIdFor(42L));
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
	void search_withoutQuery_filtersAllInvoicesWithNullScores() {
		when(invoiceRepository.findAll()).thenReturn(List.of(
				sample(1L),
				sample(2L, new BigDecimal("500.00"), "USD", LocalDate.of(2023, 1, 1))
		));

		List<InvoiceSearchHit> results = service.search(new InvoiceSearchCriteria(
				null, new BigDecimal("50"), new BigDecimal("200"), "EUR",
				LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), 20));

		assertThat(results).extracting(hit -> hit.invoice().getId()).containsExactly(1L);
		assertThat(results).extracting(InvoiceSearchHit::similarityScore).containsExactly((Double) null);
		verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
	}

	@Test
	void search_withQuery_ordersByScoreDescendingAndExposesScores() {
		Document lowerScoreFirstInList = Document.builder()
				.id(InvoiceEmbeddingService.documentIdFor(2L))
				.text("Supplier: Bright Office Supplies Ltd")
				.metadata(InvoiceEmbeddingService.METADATA_INVOICE_ID, "2")
				.score(0.42)
				.build();
		Document higherScoreSecondInList = Document.builder()
				.id(InvoiceEmbeddingService.documentIdFor(1L))
				.text("Supplier: Acme Corp")
				.metadata(InvoiceEmbeddingService.METADATA_INVOICE_ID, "1")
				.score(0.81)
				.build();
		when(vectorStore.similaritySearch(any(SearchRequest.class)))
				.thenReturn(List.of(lowerScoreFirstInList, higherScoreSecondInList));
		when(invoiceRepository.findAllById(any())).thenReturn(List.of(sample(1L), sample(2L)));

		String query = "show me all suppliers with name acme corp";
		List<InvoiceSearchHit> results = service.search(new InvoiceSearchCriteria(
				query, null, null, null, null, null, 20));

		assertThat(results).extracting(hit -> hit.invoice().getId()).containsExactly(1L, 2L);
		assertThat(results.get(0).similarityScore())
				.isEqualTo(InvoiceEmbeddingService.rankingScore(higherScoreSecondInList, query));
		assertThat(results.get(1).similarityScore())
				.isEqualTo(InvoiceEmbeddingService.rankingScore(lowerScoreFirstInList, query));
		assertThat(results.get(0).similarityScore()).isGreaterThan(results.get(1).similarityScore());
	}

	@Test
	void search_withQuery_prefersStrongLexicalSupplierMatchOverWeakerNeighbor() {
		Document weakNeighbor = Document.builder()
				.id(InvoiceEmbeddingService.documentIdFor(2L))
				.text("Supplier: Bright Office Supplies Ltd\nAddress: High Street 1, London")
				.metadata(InvoiceEmbeddingService.METADATA_INVOICE_ID, "2")
				.score(0.58)
				.build();
		Document nameMatch = Document.builder()
				.id(InvoiceEmbeddingService.documentIdFor(1L))
				.text("Supplier: Acme Corp\nAddress: Main Street 42A, Amsterdam")
				.metadata(InvoiceEmbeddingService.METADATA_INVOICE_ID, "1")
				.score(0.51)
				.build();
		when(vectorStore.similaritySearch(any(SearchRequest.class)))
				.thenReturn(List.of(weakNeighbor, nameMatch));
		when(invoiceRepository.findAllById(any())).thenReturn(List.of(
				sample(1L),
				sample(2L, "Bright Office Supplies Ltd", new BigDecimal("99.90"), "EUR", LocalDate.of(2024, 3, 12))));

		String query = "show me all suppliers with name acme corp";
		List<InvoiceSearchHit> results = service.search(new InvoiceSearchCriteria(
				query, null, null, null, null, null, 20));

		assertThat(results).extracting(hit -> hit.invoice().getId()).containsExactly(1L, 2L);
		assertThat(results.get(0).similarityScore()).isGreaterThan(results.get(1).similarityScore());
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
