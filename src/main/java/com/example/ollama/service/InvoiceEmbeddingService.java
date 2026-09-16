package com.example.ollama.service;

import com.example.ollama.dto.InvoiceSearchCriteria;
import com.example.ollama.entity.Invoice;
import com.example.ollama.exception.InvoiceAnalyzeException;
import com.example.ollama.repo.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class InvoiceEmbeddingService {

	static final String METADATA_INVOICE_ID = "invoiceId";

	private final VectorStore vectorStore;
	private final InvoiceRepository invoiceRepository;

	public void indexInvoice(Invoice invoice) {
		Objects.requireNonNull(invoice.getId(), "invoice id is required for indexing");
		String documentId = documentIdFor(invoice.getId());
		Document document = new Document(
				documentId,
				toSummary(invoice),
				Map.of(METADATA_INVOICE_ID, String.valueOf(invoice.getId()))
		);
		try {
			vectorStore.delete(List.of(documentId));
			vectorStore.add(List.of(document));
		} catch (RuntimeException ex) {
			throw new InvoiceAnalyzeException(
					"Failed to index invoice %s in the vector store".formatted(invoice.getId()), ex);
		}
	}

	public void removeInvoice(Long invoiceId) {
		try {
			vectorStore.delete(List.of(documentIdFor(invoiceId)));
		} catch (RuntimeException ex) {
			log.warn("Failed to remove invoice {} from vector store: {}", invoiceId, ex.getMessage());
		}
	}

	public List<Invoice> search(InvoiceSearchCriteria criteria) {
		List<Invoice> candidates;
		if (criteria.hasQuery()) {
			candidates = similaritySearch(criteria);
		} else {
			candidates = invoiceRepository.findAll();
		}
		return candidates.stream()
				.filter(invoice -> matchesFilters(invoice, criteria))
				.limit(criteria.limit())
				.toList();
	}

	static String toSummary(Invoice invoice) {
		return """
				Supplier: %s
				Address: %s %s, %s %s
				Invoice number: %s
				Invoice date: %s
				Amount: %s %s
				""".formatted(
				nullToEmpty(invoice.getSupplier()),
				nullToEmpty(invoice.getSupplierStreet()),
				nullToEmpty(invoice.getSupplierStreetNumber()),
				nullToEmpty(invoice.getSupplierPostalCode()),
				nullToEmpty(invoice.getSupplierCity()),
				nullToEmpty(invoice.getInvoiceNumber()),
				invoice.getInvoiceDate() == null ? "" : invoice.getInvoiceDate(),
				invoice.getAmount() == null ? "" : invoice.getAmount().toPlainString(),
				nullToEmpty(invoice.getCurrency())
		).trim();
	}

	static String documentIdFor(Long invoiceId) {
		return UUID.nameUUIDFromBytes(("invoice-" + invoiceId).getBytes(StandardCharsets.UTF_8)).toString();
	}

	private List<Invoice> similaritySearch(InvoiceSearchCriteria criteria) {
		int fetchSize = Math.min(InvoiceSearchCriteria.MAX_LIMIT, Math.max(criteria.limit() * 3, criteria.limit()));
		List<Document> documents = vectorStore.similaritySearch(
				SearchRequest.builder()
						.query(criteria.query())
						.topK(fetchSize)
						.build()
		);
		if (documents == null || documents.isEmpty()) {
			return List.of();
		}

		Map<Long, Integer> rankById = new LinkedHashMap<>();
		for (int i = 0; i < documents.size(); i++) {
			Long invoiceId = parseInvoiceId(documents.get(i));
			if (invoiceId != null) {
				rankById.putIfAbsent(invoiceId, i);
			}
		}
		if (rankById.isEmpty()) {
			return List.of();
		}

		Map<Long, Invoice> byId = invoiceRepository.findAllById(rankById.keySet()).stream()
				.collect(Collectors.toMap(Invoice::getId, invoice -> invoice));

		List<Invoice> ordered = new ArrayList<>();
		rankById.keySet().stream()
				.sorted(Comparator.comparingInt(rankById::get))
				.forEach(id -> {
					Invoice invoice = byId.get(id);
					if (invoice != null) {
						ordered.add(invoice);
					}
				});
		return ordered;
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

	private static boolean matchesFilters(Invoice invoice, InvoiceSearchCriteria criteria) {
		if (criteria.minAmount() != null && compareAmount(invoice.getAmount(), criteria.minAmount()) < 0) {
			return false;
		}
		if (criteria.maxAmount() != null && compareAmount(invoice.getAmount(), criteria.maxAmount()) > 0) {
			return false;
		}
		if (criteria.currency() != null
				&& (invoice.getCurrency() == null
				|| !invoice.getCurrency().equalsIgnoreCase(criteria.currency()))) {
			return false;
		}
		LocalDate invoiceDate = invoice.getInvoiceDate();
		if (criteria.fromDate() != null && (invoiceDate == null || invoiceDate.isBefore(criteria.fromDate()))) {
			return false;
		}
		if (criteria.toDate() != null && (invoiceDate == null || invoiceDate.isAfter(criteria.toDate()))) {
			return false;
		}
		return true;
	}

	private static int compareAmount(BigDecimal amount, BigDecimal bound) {
		if (amount == null) {
			return -1;
		}
		return amount.compareTo(bound);
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
