package com.example.ollama.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceExtractionResponse(
		String supplier,
		String supplierStreet,
		String supplierStreetNumber,
		String supplierCity,
		String supplierPostalCode,
		String invoiceNumber,
		LocalDate invoiceDate,
		BigDecimal amount,
		String currency
) {}
