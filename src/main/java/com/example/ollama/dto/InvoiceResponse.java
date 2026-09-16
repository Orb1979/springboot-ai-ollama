package com.example.ollama.dto;

import com.example.ollama.entity.Invoice;

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
) {

	public static InvoiceResponse from(InvoiceExtractionResponse extraction, Instant uploadedDate) {
		return new InvoiceResponse(
				null,
				extraction.supplier(),
				extraction.supplierStreet(),
				extraction.supplierStreetNumber(),
				extraction.supplierCity(),
				extraction.supplierPostalCode(),
				extraction.invoiceNumber(),
				extraction.invoiceDate(),
				extraction.amount(),
				extraction.currency(),
				uploadedDate,
				null,
				null
		);
	}

	public static InvoiceResponse from(Invoice invoice) {
		return new InvoiceResponse(
				invoice.getId(),
				invoice.getSupplier(),
				invoice.getSupplierStreet(),
				invoice.getSupplierStreetNumber(),
				invoice.getSupplierCity(),
				invoice.getSupplierPostalCode(),
				invoice.getInvoiceNumber(),
				invoice.getInvoiceDate(),
				invoice.getAmount(),
				invoice.getCurrency(),
				invoice.getUploadedDate(),
				invoice.getPaymentReceivedDate(),
				invoice.getUpdatedDate()
		);
	}
}
