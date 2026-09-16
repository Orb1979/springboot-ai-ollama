package com.example.ollama.mapper;

import com.example.ollama.dto.InvoiceResponse;
import com.example.ollama.entity.Invoice;

public final class InvoiceMapper {

	private InvoiceMapper() {}

	public static InvoiceResponse toResponse(Invoice invoice) {
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
