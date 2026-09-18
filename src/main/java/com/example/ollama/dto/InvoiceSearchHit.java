package com.example.ollama.dto;

import com.example.ollama.entity.Invoice;

public record InvoiceSearchHit(Invoice invoice, Double similarityScore) {

	public InvoiceSearchHit(Invoice invoice) {
		this(invoice, null);
	}
}
