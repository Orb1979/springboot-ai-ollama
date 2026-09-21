package com.example.ollama.service;

import com.example.ollama.dto.InvoiceQueryInterpretation;
import com.example.ollama.dto.InvoiceSearchCriteria;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceSearchCriteriaMergerTest {

	@Test
	void uiAmount_overridesLlmAmount() {
		var ui = new InvoiceSearchCriteria("raw", new BigDecimal("100"), null, null, null, null, null, null, null, null, 25);
		var llm = new InvoiceQueryInterpretation("Acme", "Amsterdam", new BigDecimal("500"), null, "EUR", null, null, null, null, "Acme Amsterdam");
		InvoiceSearchCriteria merged = InvoiceSearchCriteriaMerger.merge(ui, llm);
		assertThat(merged.minAmount()).isEqualByComparingTo("100");
		assertThat(merged.supplier()).isEqualTo("Acme");
		assertThat(merged.city()).isEqualTo("Amsterdam");
		assertThat(merged.currency()).isEqualTo("EUR");
		assertThat(merged.semanticQuery()).isEqualTo("Acme Amsterdam");
	}

	@Test
	void blankLlmSemanticQuery_fallsBackToUiQuery() {
		var ui = new InvoiceSearchCriteria("invoices from Acme", null, null, null, null, null, null, null, null, null, 25);
		var llm = new InvoiceQueryInterpretation("Acme", null, null, null, null, null, null, null, null, "  ");
		assertThat(InvoiceSearchCriteriaMerger.merge(ui, llm).semanticQuery()).isEqualTo("invoices from Acme");
	}

	@Test
	void nullInterpretation_returnsUiUnchanged() {
		var ui = new InvoiceSearchCriteria("q", null, null, "EUR", null, null, null, null, null, null, 25);
		assertThat(InvoiceSearchCriteriaMerger.merge(ui, null)).isEqualTo(ui);
	}
}
