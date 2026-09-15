package com.example.ollama.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record InvoiceResponse(
		Long id,
		String supplier,
		String supplierStreet,
		String supplierStreetNumber,
		String supplierCity,
		String supplierPostalCode,
		String invoiceNumber,
		LocalDate invoiceDate,
		BigDecimal amount,
		String currency,
		Instant uploadedDate,
		Instant paymentReceivedDate,
		Instant updatedDate
) {}
