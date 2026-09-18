package com.example.ollama.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceSearchCriteria(
		String query,
		BigDecimal minAmount,
		BigDecimal maxAmount,
		String currency,
		LocalDate fromDate,
		LocalDate toDate,
		Boolean paid,
		Boolean updated,
		int limit
) {
	public static final int DEFAULT_LIMIT = 25;
	public static final int MAX_LIMIT = 100;

	public InvoiceSearchCriteria {
		if (limit <= 0) {
			limit = DEFAULT_LIMIT;
		}
		if (limit > MAX_LIMIT) {
			limit = MAX_LIMIT;
		}
		if (currency != null && currency.isBlank()) {
			currency = null;
		}
		if (query != null && query.isBlank()) {
			query = null;
		}
		if (!Boolean.TRUE.equals(paid)) {
			paid = null;
		}
		if (!Boolean.TRUE.equals(updated)) {
			updated = null;
		}
	}

	public boolean hasQuery() {
		return query != null && !query.isBlank();
	}

	public boolean hasFilters() {
		return minAmount != null
				|| maxAmount != null
				|| currency != null
				|| fromDate != null
				|| toDate != null
				|| Boolean.TRUE.equals(paid)
				|| Boolean.TRUE.equals(updated);
	}
}
