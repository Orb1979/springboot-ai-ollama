package com.example.ollama.mapper;

import com.example.ollama.dto.InvoiceResponse;
import com.example.ollama.dto.InvoiceSearchHit;
import com.example.ollama.entity.Invoice;

public final class InvoiceMapper {

	private InvoiceMapper() {}

	public static InvoiceResponse toResponse(Invoice invoice) {
		return toResponse(invoice, null);
	}

	public static InvoiceResponse toResponse(InvoiceSearchHit hit) {
		return toResponse(hit.invoice(), hit.similarityScore());
	}

	private static InvoiceResponse toResponse(Invoice invoice, Double similarityScore) {
		return new InvoiceResponse(
				invoice.getId(),
				invoice.getSupplier(),
				invoice.getSupplierStreet(),
				invoice.getSupplierCity(),
				invoice.getSupplierStreetNumber(),
				invoice.getSupplierCountry(),
				invoice.getSupplierPostalCode(),
				invoice.getInvoiceNumber(),
				invoice.getInvoiceDate(),
				invoice.getAmount(),
				invoice.getCurrency(),
				invoice.getUploadedDate(),
				invoice.getPaymentReceivedDate(),
				invoice.getUpdatedDate(),
				similarityScore
		);
	}
}
