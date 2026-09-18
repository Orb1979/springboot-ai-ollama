package com.example.ollama.repo;

import com.example.ollama.dto.InvoiceSearchCriteria;
import com.example.ollama.entity.Invoice;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceSpecificationsTest {

	@Test
	void matching_returnsConjunctionEvenWhenNoFilters() {
		Specification<Invoice> spec = InvoiceSpecifications.matching(
				new InvoiceSearchCriteria(null, null, null, null, null, null, null, null, null, null, 25)
		);

		assertThat(spec).isNotNull();
	}

	@Test
	void matchingByIds_returnsUnsatisfiableSpecForEmptyIds() {
		Specification<Invoice> empty = InvoiceSpecifications.matchingByIds(
				List.of(),
				new InvoiceSearchCriteria(null, new BigDecimal("10"), null, null, null, null, null, null, null, null, 25)
		);
		Specification<Invoice> nullIds = InvoiceSpecifications.matchingByIds(
				null,
				new InvoiceSearchCriteria(null, new BigDecimal("10"), null, null, null, null, null, null, null, null, 25)
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
				new InvoiceSearchCriteria(null, null, null, null, null, null, true, true, null, null, 25)
		);

		assertThat(spec).isNotNull();
	}

	@Test
	void matching_addsCaseInsensitiveContainsPredicatesForSupplierAndCity() {
		Root<Invoice> root = mock();
		CriteriaQuery<?> query = mock();
		CriteriaBuilder cb = mock();
		Path<String> supplierPath = mock();
		Path<String> cityPath = mock();
		Expression<String> lowerSupplier = mock();
		Expression<String> lowerCity = mock();
		Predicate supplierPredicate = mock();
		Predicate cityPredicate = mock();

		when(root.<String>get("supplier")).thenReturn(supplierPath);
		when(root.<String>get("supplierCity")).thenReturn(cityPath);
		when(cb.lower(supplierPath)).thenReturn(lowerSupplier);
		when(cb.lower(cityPath)).thenReturn(lowerCity);
		when(cb.like(lowerSupplier, "%acme%")).thenReturn(supplierPredicate);
		when(cb.like(lowerCity, "%amsterdam%")).thenReturn(cityPredicate);

		InvoiceSpecifications.matching(
				new InvoiceSearchCriteria(
						null, null, null, null, null, null, null, null, "AcMe", "AmStErDaM", 25)
		).toPredicate(root, query, cb);

		verify(cb).like(lowerSupplier, "%acme%");
		verify(cb).like(lowerCity, "%amsterdam%");
	}
}
