package com.example.ollama.service;

import com.example.ollama.entity.Invoice;
import com.example.ollama.exception.InvoiceEmbedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InvoiceEmbeddingServiceTest {

	@Mock private VectorStore vectorStore;
	InvoiceEmbeddingService service;

	@BeforeEach
	void setUp() {
		service = new InvoiceEmbeddingService(vectorStore);
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
				.isInstanceOf(InvoiceEmbedException.class)
				.hasMessageContaining("Failed to index invoice 1");
	}

	private Invoice sample(Long id) {
		return new Invoice(
				id,
				"Acme Corp",
				"Main Street",
				"42A",
				"Amsterdam",
				"Netherlands",
				"1012 AB",
				"INV-001",
				LocalDate.of(2024, 3, 12),
				new BigDecimal("99.90"),
				"EUR",
				Instant.parse("2026-09-15T10:00:00Z"),
				null,
				null
		);
	}
}
