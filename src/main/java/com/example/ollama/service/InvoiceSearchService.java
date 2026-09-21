package com.example.ollama.service;

import com.example.ollama.dto.InvoiceSearchCriteria;
import com.example.ollama.dto.InvoiceSearchHit;
import com.example.ollama.entity.Invoice;
import com.example.ollama.repo.InvoiceRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

	public List<InvoiceSearchHit> searchInvoices(InvoiceSearchCriteria uiCriteria) {
		if (!uiCriteria.hasSemanticQuery()) {
			return search(uiCriteria);
		}
		InvoiceSearchCriteria merged = invoiceQueryInterpreter.interpret(uiCriteria.semanticQuery())
				.map(interpretation -> InvoiceSearchCriteriaMerger.merge(uiCriteria, interpretation))
				.orElse(uiCriteria);
		return search(merged);
	}

	List<InvoiceSearchHit> search(InvoiceSearchCriteria criteria) {
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
				.map(InvoiceSearchService::parseInvoiceId)
				.filter(Objects::nonNull)
				.toList();

		Map<Long, Invoice> invoicesById = invoiceRepository.findMatchingByIds(invoiceIds, criteria)
				.stream()
				.collect(Collectors.toMap(Invoice::getId, invoice -> invoice));

		List<InvoiceSearchHit> hits = documents.stream()
				.map(document -> toSearchHit(document, invoicesById))
				.flatMap(Optional::stream)
				.limit(criteria.limit())
				.toList();
		if (hits.isEmpty() && criteria.hasFilters()) {
			return invoiceRepository.findMatching(criteria)
					.stream()
					.map(InvoiceSearchHit::new)
					.toList();
		}
		return hits;
	}

	/** Maps a vector document to a hit when the invoice exists in {@code invoicesById}. */
	private static Optional<InvoiceSearchHit> toSearchHit(Document document, Map<Long, Invoice> invoicesById) {
		Long invoiceId = parseInvoiceId(document);
		if (invoiceId == null) {
			// missing or unparseable invoice ID metadata
			return Optional.empty();
		}
		Invoice invoice = invoicesById.get(invoiceId);
		if (invoice == null) {
			// filtered out by SQL criteria and/or orphaned vector document
			return Optional.empty();
		}
		return Optional.of(new InvoiceSearchHit(invoice, document.getScore()));
	}

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
