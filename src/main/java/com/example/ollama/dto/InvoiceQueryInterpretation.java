package com.example.ollama.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceQueryInterpretation(
		String supplier,
		String city,
		BigDecimal minAmount,
		BigDecimal maxAmount,
		String currency,
		LocalDate fromDate,
		LocalDate toDate,
		Boolean paid,
		Boolean updated,
		String semanticQuery
) {
}
