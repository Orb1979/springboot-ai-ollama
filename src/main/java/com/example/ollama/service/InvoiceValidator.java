package com.example.ollama.service;

import com.example.ollama.entity.Invoice;
import com.example.ollama.exception.InvoiceValidationException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Log4j2
@Component
public class InvoiceValidator {

	private static final Set<String> VALID_CURRENCIES = Set.of(
			"EUR", "USD"
			// if you want to except any currency format, swap for java.util.Currency lookup
	);

	public void validate(Invoice invoice) {
		List<String> errors = new ArrayList<>();

		if (isBlank(invoice.getSupplier())) {
			errors.add("supplier is missing");
		}

		if (isBlank(invoice.getSupplierStreet())) {
			errors.add("supplier street is missing");
		}

		if (isBlank(invoice.getSupplierStreetNumber())) {
			errors.add("supplier street number is missing");
		}

		if (isBlank(invoice.getSupplierCity())) {
			errors.add("supplier city is missing");
		}

		if (isBlank(invoice.getSupplierPostalCode())) {
			errors.add("supplier postal code is missing");
		}

		if (isBlank(invoice.getInvoiceNumber())) {
			errors.add("invoice number is missing");
		}

		if (invoice.getInvoiceDate() == null) {
			errors.add("invoice date is missing");
		}

		if (invoice.getAmount() == null) {
			errors.add("amount is missing");
		} else if (invoice.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
			errors.add("amount must be greater than zero, got: " + invoice.getAmount());
		}

		if (isBlank(invoice.getCurrency())) {
			errors.add("currency is missing");
		} else if (!VALID_CURRENCIES.contains(invoice.getCurrency().toUpperCase())) {
			errors.add("unrecognized currency: " + invoice.getCurrency());
		}

		if (invoice.getUploadedDate() == null) {
			errors.add("uploaded date is missing");
		}

		if (!errors.isEmpty()) {
			log.warn("Invoice validation failed: {}", errors);
			throw new InvoiceValidationException(errors);
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
