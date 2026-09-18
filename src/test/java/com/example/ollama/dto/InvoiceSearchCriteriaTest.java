package com.example.ollama.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceSearchCriteriaTest {

	@Test
	void hasFilters_isFalseWhenOnlyQueryPresent() {
		var criteria = new InvoiceSearchCriteria("acme", null, null, null, null, null, 20);

		assertThat(criteria.hasFilters()).isFalse();
	}

	@Test
	void hasFilters_isTrueForAnyStructuredFilter() {
		assertThat(new InvoiceSearchCriteria(null, new BigDecimal("10"), null, null, null, null, 20).hasFilters())
				.isTrue();
		assertThat(new InvoiceSearchCriteria(null, null, new BigDecimal("100"), null, null, null, 20).hasFilters())
				.isTrue();
		assertThat(new InvoiceSearchCriteria(null, null, null, "EUR", null, null, 20).hasFilters())
				.isTrue();
		assertThat(new InvoiceSearchCriteria(null, null, null, null, LocalDate.of(2024, 1, 1), null, 20).hasFilters())
				.isTrue();
		assertThat(new InvoiceSearchCriteria(null, null, null, null, null, LocalDate.of(2024, 12, 31), 20).hasFilters())
				.isTrue();
	}

	@Test
	void blankCurrency_isTreatedAsAbsent() {
		var criteria = new InvoiceSearchCriteria(null, null, null, "  ", null, null, 20);

		assertThat(criteria.currency()).isNull();
		assertThat(criteria.hasFilters()).isFalse();
	}
}
