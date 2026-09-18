package com.example.ollama.service;

import com.example.ollama.dto.InvoiceSearchCriteria;
import com.example.ollama.dto.InvoiceSearchHit;
import com.example.ollama.entity.Invoice;
import com.example.ollama.exception.InvoiceAnalyzeException;
import com.example.ollama.exception.InvoiceEmbedException;
import com.example.ollama.repo.InvoiceRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
@Service
public class InvoiceEmbeddingService {
	static final String METADATA_INVOICE_ID = "invoiceId";
	private final VectorStore vectorStore;
	private final InvoiceRepository invoiceRepository;
	private final double similarityThreshold;

	public InvoiceEmbeddingService(
			VectorStore vectorStore,
			InvoiceRepository invoiceRepository,
			@Value("${app.ai.search.similarity-threshold}")
			double similarityThreshold) {
		this.vectorStore = vectorStore;
		this.invoiceRepository = invoiceRepository;
		this.similarityThreshold = similarityThreshold;
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
			throw new InvoiceAnalyzeException("Failed to index invoice %s in vector store".formatted(invoice.getId()), ex);
		}
	}

	public void removeInvoice(Long invoiceId) {
		try {
			vectorStore.delete(List.of(createDocumentId(invoiceId)));
		} catch (RuntimeException ex) {
			log.warn("Failed to remove invoice {} from vector store: {}",  invoiceId, ex.getMessage());
		}
	}

	public List<InvoiceSearchHit> search(InvoiceSearchCriteria criteria) {
		if (!criteria.hasSemanticQuery()) {
			return invoiceRepository.findMatching(criteria)
					.stream()
					.map(InvoiceSearchHit::new)
					.toList();
		}
		return semanticSearch(criteria);
	}

	private List<InvoiceSearchHit> semanticSearch(InvoiceSearchCriteria criteria) {
		List<Document> documents = vectorStore.similaritySearch(
				SearchRequest.builder()
						.query(criteria.semanticQuery())
						.topK(InvoiceSearchCriteria.MAX_LIMIT)
						.similarityThreshold(similarityThreshold)
						.build()
		);

		List<Long> invoiceIds = documents.stream()
          .map(InvoiceEmbeddingService::parseInvoiceId)
          .filter(Objects::nonNull)
          .toList();

		Map<Long, Invoice> invoicesById = invoiceRepository.findMatchingByIds(invoiceIds, criteria)
          .stream()
          .collect(Collectors.toMap(Invoice::getId, invoice -> invoice));

		return documents.stream()
				       .map(document -> toSearchHit(document, invoicesById))
				       .flatMap(Optional::stream)
				       .limit(criteria.limit())
				       .toList();
	}

  // Maps a vector-store document to a search hit if its invoice exists in invoicesById;
	private static Optional<InvoiceSearchHit> toSearchHit(Document document, Map<Long, Invoice> invoicesById) {
		Long invoiceId = parseInvoiceId(document);
		if (invoiceId == null) {
			// filtered out (by the SQL filters on InvoiceSearchCriteria)
			return Optional.empty();
		}
		Invoice invoice = invoicesById.get(invoiceId);
		if (invoice == null) {
			// orphaned (present in vector table but missing in invoice table)
			return Optional.empty();
		}
		return Optional.of(new InvoiceSearchHit(invoice, document.getScore()));
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
				invoice.getInvoiceDate() == null ? "": invoice.getInvoiceDate(),
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

	private static Long parseInvoiceId(Document document) {
		Object raw = document.getMetadata().get(METADATA_INVOICE_ID);
		if (raw == null) {
			return null;
		}
		try {
			return Long.valueOf(String.valueOf(raw));
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}