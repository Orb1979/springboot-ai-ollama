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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Log4j2
@Service
public class InvoiceEmbeddingService {

	static final String METADATA_INVOICE_ID = "invoiceId";
	/** How strongly query-token overlap can boost vector similarity when ranking. */
	static final double LEXICAL_BOOST_WEIGHT = 0.35;

	private static final Set<String> QUERY_STOPWORDS = Set.of(
			"a", "an", "the", "all", "me", "my", "show", "find", "get", "list", "with", "name",
			"named", "called", "for", "of", "and", "or", "to", "from", "in", "on", "by", "please",
			"invoice", "invoices", "supplier", "suppliers", "company", "companies");

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
			throw new InvoiceAnalyzeException(
					"Failed to index invoice %s in the vector store".formatted(invoice.getId()), ex);
		}
	}

	public void removeInvoice(Long invoiceId) {
		try {
			vectorStore.delete(List.of(createDocumentId(invoiceId)));
		} catch (RuntimeException ex) {
			log.warn("Failed to remove invoice {} from vector store: {}", invoiceId, ex.getMessage());
		}
	}

	public List<InvoiceSearchHit> search(InvoiceSearchCriteria criteria) {
		List<InvoiceSearchHit> candidates;
		if (criteria.hasQuery()) {
			candidates = similaritySearch(criteria);
		} else {
			candidates = invoiceRepository.findAll().stream()
					.map(invoice -> new InvoiceSearchHit(invoice, null))
					.toList();
		}
		return candidates.stream()
				.filter(hit -> matchesFilters(hit.invoice(), criteria))
				.limit(criteria.limit())
				.toList();
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

	private List<InvoiceSearchHit> similaritySearch(InvoiceSearchCriteria criteria) {
		int fetchSize = Math.min(InvoiceSearchCriteria.MAX_LIMIT, Math.max(criteria.limit() * 3, criteria.limit()));
		List<Document> documents = vectorStore.similaritySearch(
				SearchRequest.builder()
						.query(criteria.query())
						.topK(fetchSize)
						.similarityThreshold(similarityThreshold)
						.build()
		);
		if (documents == null || documents.isEmpty()) {
			return List.of();
		}

		List<Document> ranked = documents.stream()
				.sorted(Comparator.comparingDouble((Document document) -> rankingScore(document, criteria.query()))
						.reversed())
				.toList();

		Map<Long, Integer> rankById = new LinkedHashMap<>();
		Map<Long, Double> scoreById = new LinkedHashMap<>();
		for (int i = 0; i < ranked.size(); i++) {
			Document document = ranked.get(i);
			Long invoiceId = parseInvoiceId(document);
			if (invoiceId != null) {
				rankById.putIfAbsent(invoiceId, i);
				scoreById.putIfAbsent(invoiceId, rankingScore(document, criteria.query()));
			}
		}
		if (rankById.isEmpty()) {
			return List.of();
		}

		Map<Long, Invoice> byId = invoiceRepository.findAllById(rankById.keySet()).stream()
				.collect(Collectors.toMap(Invoice::getId, invoice -> invoice));

		List<InvoiceSearchHit> ordered = new ArrayList<>();
		rankById.keySet().stream()
				.sorted(Comparator.comparingInt(rankById::get))
				.forEach(id -> {
					Invoice invoice = byId.get(id);
					if (invoice != null) {
						ordered.add(new InvoiceSearchHit(invoice, scoreById.get(id)));
					}
				});
		return ordered;
	}

	static double rankingScore(Document document, String query) {
		double vectorScore = document.getScore() != null ? document.getScore() : 0.0;
		return vectorScore + (LEXICAL_BOOST_WEIGHT * lexicalOverlap(query, document.getText()));
	}

	static double lexicalOverlap(String query, String documentText) {
		List<String> tokens = significantTokens(query);
		if (tokens.isEmpty()) {
			return 0.0;
		}
		String haystack = documentText == null ? "" : documentText.toLowerCase(Locale.ROOT);
		long hits = tokens.stream().filter(haystack::contains).count();
		return (double) hits / tokens.size();
	}

	static List<String> significantTokens(String query) {
		if (query == null || query.isBlank()) {
			return List.of();
		}
		return Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
				.filter(token -> token.length() >= 3)
				.filter(token -> !QUERY_STOPWORDS.contains(token))
				.distinct()
				.toList();
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
