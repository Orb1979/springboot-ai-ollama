package com.example.ollama.service;

import com.example.ollama.dto.InvoiceQueryInterpretation;
import com.example.ollama.dto.InvoiceSearchCriteria;

/**
 * Merges UI search criteria with LLM-extracted filters (UI values win when both are set).
 */
public final class InvoiceSearchCriteriaMerger {
	private InvoiceSearchCriteriaMerger() {}

	public static InvoiceSearchCriteria merge(InvoiceSearchCriteria ui, InvoiceQueryInterpretation llm) {
		if (llm == null) {
			return ui;
		}
		String semantic = blankToNull(llm.semanticQuery());
		if (semantic == null) {
			semantic = ui.semanticQuery();
		}
		return new InvoiceSearchCriteria(
				semantic,
				firstNonNull(ui.minAmount(), llm.minAmount()),
				firstNonNull(ui.maxAmount(), llm.maxAmount()),
				firstNonNull(blankToNull(ui.currency()), blankToNull(llm.currency())),
				firstNonNull(ui.fromDate(), llm.fromDate()),
				firstNonNull(ui.toDate(), llm.toDate()),
				firstNonNull(ui.paid(), llm.paid()),
				firstNonNull(ui.updated(), llm.updated()),
				firstNonNull(blankToNull(ui.supplier()), blankToNull(llm.supplier())),
				firstNonNull(blankToNull(ui.city()), blankToNull(llm.city())),
				ui.limit()
		);
	}

	private static <T> T firstNonNull(T ui, T llm) {
		return ui != null ? ui : llm;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
