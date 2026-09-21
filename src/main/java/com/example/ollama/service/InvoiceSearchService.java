package com.example.ollama.service;

import com.example.ollama.dto.InvoiceSearchCriteria;
import com.example.ollama.dto.InvoiceSearchHit;
import com.example.ollama.entity.Invoice;
import com.example.ollama.repo.InvoiceRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
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
			return withoutScores(invoiceRepository.findMatching(criteria));
		}
		return rankBySimilarity(criteria);
	}

	/**
	 * When hard filters are present: SQL candidates first, then vector-rank only those ids.
	 * Otherwise: global vector search, then load matching invoices by id.
	 */
	private List<InvoiceSearchHit> rankBySimilarity(InvoiceSearchCriteria criteria) {
		if (criteria.hasFilters()) {
			return rankWithinSqlMatches(criteria);
		}
		return rankGlobally(criteria);
	}

	/**
	 * Hard SQL first, then similarity-rank only within that candidate set
	 * (avoids missing filter matches that fall outside the global vector top-K).
	 * Falls back to SQL order when no vector hits survive the threshold.
	 */
	private List<InvoiceSearchHit> rankWithinSqlMatches(InvoiceSearchCriteria criteria) {
		List<Invoice> candidates = invoiceRepository.findMatchingCandidates(criteria);
		if (candidates.isEmpty()) {
			return List.of();
		}

		Map<Long, Invoice> invoicesById = candidates.stream()
				.collect(Collectors.toMap(Invoice::getId, invoice -> invoice));

		List<Document> documents = similaritySearch(criteria, criteria.limit(), invoiceIdIn(candidates));
		List<InvoiceSearchHit> hits = toHits(documents, invoicesById, criteria.limit());
		if (hits.isEmpty()) {
			return withoutScores(candidates, criteria.limit());
		}
		return hits;
	}

	/** Global vector ranking when there are no hard filters. */
	private List<InvoiceSearchHit> rankGlobally(InvoiceSearchCriteria criteria) {
		List<Document> documents = similaritySearch(criteria, InvoiceSearchCriteria.MAX_LIMIT, null);

		List<Long> invoiceIds = documents.stream()
				.map(InvoiceSearchService::parseInvoiceId)
				.filter(Objects::nonNull)
				.toList();
		Map<Long, Invoice> invoicesById = invoiceRepository.findMatchingByIds(invoiceIds, criteria)
				.stream()
				.collect(Collectors.toMap(Invoice::getId, invoice -> invoice));

		return toHits(documents, invoicesById, criteria.limit());
	}

	private List<Document> similaritySearch(
			InvoiceSearchCriteria criteria, int topK, Filter.Expression filterExpression) {
		SearchRequest.Builder request = SearchRequest.builder()
				.query(criteria.semanticQuery())
				.topK(topK)
				.similarityThreshold(similarityThreshold);
		if (filterExpression != null) {
			request.filterExpression(filterExpression);
		}
		return vectorStore.similaritySearch(request.build());
	}

	private static Filter.Expression invoiceIdIn(List<Invoice> candidates) {
		List<Object> ids = new ArrayList<>(candidates.stream()
				.map(invoice -> String.valueOf(invoice.getId()))
				.toList());
		return new FilterExpressionBuilder()
				.in(InvoiceEmbeddingService.METADATA_INVOICE_ID, ids)
				.build();
	}

	private static List<InvoiceSearchHit> toHits(
			List<Document> documents,
			Map<Long, Invoice> invoicesById,
			int limit) {
		return documents.stream()
				.map(document -> toSearchHit(document, invoicesById))
				.flatMap(Optional::stream)
				.limit(limit)
				.toList();
	}

	private static List<InvoiceSearchHit> withoutScores(List<Invoice> invoices) {
		return withoutScores(invoices, invoices.size());
	}

	private static List<InvoiceSearchHit> withoutScores(List<Invoice> invoices, int limit) {
		return invoices.stream()
				.limit(limit)
				.map(InvoiceSearchHit::new)
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
			// Orphaned — invoice deleted (or never existed) while the embedding was left behind,
			// or (unfiltered path) not loaded from the DB.
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
