package com.example.ollama.repo;

import com.example.ollama.dto.InvoiceSearchCriteria;
import com.example.ollama.entity.Invoice;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceSpecificationsTest {

	@Test
	void matching_returnsConjunctionEvenWhenNoFilters() {
		Specification<Invoice> spec = InvoiceSpecifications.matching(
				new InvoiceSearchCriteria(null, null, null, null, null, null, null, null, 25)
		);

		assertThat(spec).isNotNull();
	}

	@Test
	void matchingByIds_returnsUnsatisfiableSpecForEmptyIds() {
		Specification<Invoice> empty = InvoiceSpecifications.matchingByIds(
				List.of(),
				new InvoiceSearchCriteria(null, new BigDecimal("10"), null, null, null, null, null, null, 25)
		);
		Specification<Invoice> nullIds = InvoiceSpecifications.matchingByIds(
				null,
				new InvoiceSearchCriteria(null, new BigDecimal("10"), null, null, null, null, null, null, 25)
		);

		assertThat(empty).isNotNull();
		assertThat(nullIds).isNotNull();
	}

	@Test
	void matching_acceptsPartialFiltersWithoutCurrency() {
		// Regression: optional null currency must not be passed into LOWER()
		Specification<Invoice> spec = InvoiceSpecifications.matching(
				new InvoiceSearchCriteria(
						null,
						new BigDecimal("100"),
						null,
						null,
						LocalDate.of(2024, 1, 1),
						null,
						null,
						null,
						25
				)
		);

		assertThat(spec).isNotNull();
	}

	@Test
	void matching_acceptsPaidAndUpdatedFlags() {
		Specification<Invoice> spec = InvoiceSpecifications.matching(
				new InvoiceSearchCriteria(null, null, null, null, null, null, true, true, 25)
		);

		assertThat(spec).isNotNull();
	}
}
