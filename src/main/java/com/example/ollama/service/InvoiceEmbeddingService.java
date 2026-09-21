package com.example.ollama.service;

import com.example.ollama.entity.Invoice;
import com.example.ollama.exception.InvoiceEmbedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages invoice embeddings in the vector store
 */
@Log4j2
@Service
public class InvoiceEmbeddingService {
	static final String METADATA_INVOICE_ID = "invoiceId";
	private final VectorStore vectorStore;

	public InvoiceEmbeddingService(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
	}

	public void indexInvoice(Invoice invoice) {
		String documentId = createDocumentId(invoice.getId());

		Document document = new Document(
				documentId,
				toSummary(invoice),
				Map.of(METADATA_INVOICE_ID, String.valueOf(invoice.getId()))
		);

		try {
			vectorStore.delete(List.of(documentId));
			vectorStore.add(List.of(document));
		} catch (RuntimeException ex) {
			throw new InvoiceEmbedException("Failed to index invoice %s in vector store".formatted(invoice.getId()), ex);
		}
	}

	public void removeInvoice(Long invoiceId) {
		try {
			vectorStore.delete(List.of(createDocumentId(invoiceId)));
		} catch (RuntimeException ex) {
			log.warn("Failed to remove invoice {} from vector store: {}", invoiceId, ex.getMessage());
		}
	}

	static String toSummary(Invoice invoice) {
		return """
      Supplier: %s
      Address: %s %s, %s %s, %s
      Invoice number: %s
      Invoice date: %s
      Amount: %s %s
      """.formatted(
				nullToEmpty(invoice.getSupplier()),
				nullToEmpty(invoice.getSupplierStreet()),
				nullToEmpty(invoice.getSupplierStreetNumber()),
				nullToEmpty(invoice.getSupplierPostalCode()),
				nullToEmpty(invoice.getSupplierCity()),
				nullToEmpty(invoice.getSupplierCountry()),
				nullToEmpty(invoice.getInvoiceNumber()),
				invoice.getInvoiceDate() == null ? "" : invoice.getInvoiceDate(),
				invoice.getAmount() == null ? "" : invoice.getAmount().toPlainString(),
				nullToEmpty(invoice.getCurrency())
		).trim();
	}

	static String createDocumentId(Long invoiceId) {
		if (invoiceId == null) {
			throw new InvoiceEmbedException("invoiceId is null");
		}
		return UUID.nameUUIDFromBytes(("invoice-" + invoiceId).getBytes(StandardCharsets.UTF_8)).toString();
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
