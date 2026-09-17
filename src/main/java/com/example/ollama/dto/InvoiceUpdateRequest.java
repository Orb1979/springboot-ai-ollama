package com.example.ollama.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record InvoiceUpdateRequest(
		String supplier,
		String supplierStreet,
		String supplierStreetNumber,
		String supplierPostalCode,
		String supplierCity,
		String supplierCountry,
		String invoiceNumber,
		LocalDate invoiceDate,
		BigDecimal amount,
		String currency,
		Instant paymentReceivedDate
) {}
