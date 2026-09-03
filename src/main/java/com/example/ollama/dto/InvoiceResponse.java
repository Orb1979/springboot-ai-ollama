package com.example.ollama.dto;

import java.math.BigDecimal;

public record InvoiceResponse(
		String supplier,
		String invoiceNumber,
		BigDecimal amount,
		String currency
) {}
