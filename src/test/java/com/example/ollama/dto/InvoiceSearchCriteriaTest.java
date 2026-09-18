package com.example.ollama.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceSearchCriteriaTest {

	@Test
	void hasFilters_isFalseWhenOnlyQueryPresent() {
		var criteria = new InvoiceSearchCriteria("acme", null, null, null, null, null, null, null, 25);

		assertThat(criteria.hasFilters()).isFalse();
	}

	@Test
	void hasFilters_isTrueForAnyStructuredFilter() {
		assertThat(new InvoiceSearchCriteria(null, new BigDecimal("10"), null, null, null, null, null, null, 25).hasFilters())
				.isTrue();
		assertThat(new InvoiceSearchCriteria(null, null, new BigDecimal("100"), null, null, null, null, null, 25).hasFilters())
				.isTrue();
		assertThat(new InvoiceSearchCriteria(null, null, null, "EUR", null, null, null, null, 25).hasFilters())
				.isTrue();
		assertThat(new InvoiceSearchCriteria(null, null, null, null, LocalDate.of(2024, 1, 1), null, null, null, 25).hasFilters())
				.isTrue();
		assertThat(new InvoiceSearchCriteria(null, null, null, null, null, LocalDate.of(2024, 12, 31), null, null, 25).hasFilters())
				.isTrue();
		assertThat(new InvoiceSearchCriteria(null, null, null, null, null, null, true, null, 25).hasFilters())
				.isTrue();
		assertThat(new InvoiceSearchCriteria(null, null, null, null, null, null, null, true, 25).hasFilters())
				.isTrue();
	}

	@Test
	void blankCurrency_isTreatedAsAbsent() {
		var criteria = new InvoiceSearchCriteria(null, null, null, "  ", null, null, null, null, 25);

		assertThat(criteria.currency()).isNull();
		assertThat(criteria.hasFilters()).isFalse();
	}

	@Test
	void falsePaidAndUpdated_areTreatedAsAbsent() {
		var criteria = new InvoiceSearchCriteria(null, null, null, null, null, null, false, false, 25);

		assertThat(criteria.paid()).isNull();
		assertThat(criteria.updated()).isNull();
		assertThat(criteria.hasFilters()).isFalse();
	}

	@Test
	void defaultLimit_isTwentyFive() {
		var criteria = new InvoiceSearchCriteria(null, null, null, null, null, null, null, null, 0);

		assertThat(criteria.limit()).isEqualTo(InvoiceSearchCriteria.DEFAULT_LIMIT);
		assertThat(InvoiceSearchCriteria.DEFAULT_LIMIT).isEqualTo(25);
	}
}
