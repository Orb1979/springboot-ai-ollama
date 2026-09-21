package com.example.ollama.service;

import com.example.ollama.dto.InvoiceSearchCriteria;
import com.example.ollama.dto.InvoiceSearchHit;
import com.example.ollama.entity.Invoice;
import com.example.ollama.repo.InvoiceRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Searches invoices: optional LLM interpretation of natural-language queries,
 * hard SQL filters, and vector similarity ranking.
 */
@Log4j2
@Service
public class InvoiceSearchService {

	private final VectorStore vectorStore;
	private final InvoiceRepository invoiceRepository;
	private final InvoiceQueryInterpreter invoiceQueryInterpreter;
	private final double similarityThreshold;

	public InvoiceSearchService(
			VectorStore vectorStore,
			InvoiceRepository invoiceRepository,
			InvoiceQueryInterpreter invoiceQueryInterpreter,
			@Value("${app.ai.search.similarity-threshold}")
			double similarityThreshold) {
		this.vectorStore = vectorStore;
		this.invoiceRepository = invoiceRepository;
		this.invoiceQueryInterpreter = invoiceQueryInterpreter;
		this.similarityThreshold = similarityThreshold;
	}

	/**
	 * Entry point for search: if semanticQuery is present, interprets it via the LLM,
	 * merges with UI criteria (UI wins), then runs SQL and/or semantic search.
	 */
	public List<InvoiceSearchHit> searchInvoices(InvoiceSearchCriteria uiCriteria) {
		if (!uiCriteria.hasSemanticQuery()) {
			return search(uiCriteria);
		}
		InvoiceSearchCriteria merged = invoiceQueryInterpreter.interpret(uiCriteria.semanticQuery())
				.map(interpretation -> InvoiceSearchCriteriaMerger.merge(uiCriteria, interpretation))
				.orElse(uiCriteria);
		return search(merged);
	}

	/**
	 * Routes to SQL-only listing when there is no semantic query; otherwise vector search.
	 */
	List<InvoiceSearchHit> search(InvoiceSearchCriteria criteria) {
		if (!criteria.hasSemanticQuery()) {
			return invoiceRepository.findMatching(criteria)
					.stream()
					.map(InvoiceSearchHit::new)
					.toList();
		}
		return semanticSearch(criteria);
	}

	/**
	 * When hard filters are present: SQL candidates first, then vector-rank only those ids.
	 * Otherwise: global vector search, then load matching invoices by id.
	 */
	private List<InvoiceSearchHit> semanticSearch(InvoiceSearchCriteria criteria) {
		if (criteria.hasFilters()) {
			return semanticSearchWithinFilters(criteria);
		}
		return semanticSearchUnfiltered(criteria);
	}

	/**
	 * Hard SQL first, then similarity-rank only within that candidate set
	 * (avoids missing filter matches that fall outside the global vector top-K).
	 * Falls back to SQL order when no vector hits survive the threshold.
	 */
	private List<InvoiceSearchHit> semanticSearchWithinFilters(InvoiceSearchCriteria criteria) {
		List<Invoice> candidates = invoiceRepository.findMatchingCandidates(criteria);
		if (candidates.isEmpty()) {
			return List.of();
		}

		Map<Long, Invoice> invoicesById = candidates.stream()
				.collect(Collectors.toMap(Invoice::getId, invoice -> invoice));

		List<Object> candidateIds = new ArrayList<>(candidates.stream()
				.map(invoice -> String.valueOf(invoice.getId()))
				.toList());

		FilterExpressionBuilder filter = new FilterExpressionBuilder();
		List<Document> documents = vectorStore.similaritySearch(
				SearchRequest.builder()
						.query(criteria.semanticQuery())
						.topK(criteria.limit())
						.similarityThreshold(similarityThreshold)
						.filterExpression(filter.in(
								InvoiceEmbeddingService.METADATA_INVOICE_ID,
								candidateIds).build())
						.build()
		);

		List<InvoiceSearchHit> hits = documents.stream()
				.map(document -> toSearchHit(document, invoicesById))
				.flatMap(Optional::stream)
				.limit(criteria.limit())
				.toList();
		if (hits.isEmpty()) {
			return candidates.stream()
					.limit(criteria.limit())
					.map(InvoiceSearchHit::new)
					.toList();
		}
		return hits;
	}

	/** Global vector ranking when there are no hard filters. */
	private List<InvoiceSearchHit> semanticSearchUnfiltered(InvoiceSearchCriteria criteria) {
		List<Document> documents = vectorStore.similaritySearch(
				SearchRequest.builder()
						.query(criteria.semanticQuery())
						.topK(InvoiceSearchCriteria.MAX_LIMIT)
						.similarityThreshold(similarityThreshold)
						.build()
		);

		List<Long> invoiceIds = documents.stream()
				.map(InvoiceSearchService::parseInvoiceId)
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

	/** Maps a vector document to a hit when the invoice exists. */
	private static Optional<InvoiceSearchHit> toSearchHit(Document document, Map<Long, Invoice> invoicesById) {
		Long invoiceId = parseInvoiceId(document);
		if (invoiceId == null) {
			// missing or unparseable invoice ID metadata
			return Optional.empty();
		}
		Invoice invoice = invoicesById.get(invoiceId);
		if (invoice == null) {
			// The vector points at an invoice id that is not in the SQL result set. That can mean
			// Filtered out — the invoice exists, but failed hard filters (amount, currency, city, etc.), or
			// Orphaned — the invoice was deleted (or never existed) while the embedding was left behind.
			return Optional.empty();
		}
		return Optional.of(new InvoiceSearchHit(invoice, document.getScore()));
	}

	/** Returns the invoice id from vector document or null if missing/invalid. */
	private static Long parseInvoiceId(Document document) {
		Object raw = document.getMetadata().get(InvoiceEmbeddingService.METADATA_INVOICE_ID);
		if (raw == null) {
			return null;
		}
		try {
			return Long.valueOf(String.valueOf(raw));
		} catch (NumberFormatException ex) {
			return null;
		}
	}
}
