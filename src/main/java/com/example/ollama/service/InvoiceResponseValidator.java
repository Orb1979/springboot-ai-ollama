package com.example.ollama.service;

import com.example.ollama.dto.InvoiceResponse;
import com.example.ollama.exception.InvoiceValidationException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Log4j2
@Component
public class InvoiceResponseValidator {

	private static final Set<String> VALID_CURRENCIES = Set.of(
			"EUR", "USD"
			// if you want to except any currency format, swap for java.util.Currency lookup
	);

	public void validate(InvoiceResponse response) {
		List<String> errors = new ArrayList<>();

		if (isBlank(response.supplier())) {
			errors.add("supplier is missing");
		}

		if (isBlank(response.supplierStreet())) {
			errors.add("supplier street is missing");
		}

		if (isBlank(response.supplierStreetNumber())) {
			errors.add("supplier street number is missing");
		}

		if (isBlank(response.supplierCity())) {
			errors.add("supplier city is missing");
		}

		if (isBlank(response.supplierPostalCode())) {
			errors.add("supplier postal code is missing");
		}

		if (isBlank(response.invoiceNumber())) {
			errors.add("invoice number is missing");
		}

		if (response.invoiceDate() == null) {
			errors.add("invoice date is missing");
		}

		if (response.amount() == null) {
			errors.add("amount is missing");
		} else if (response.amount().compareTo(BigDecimal.ZERO) <= 0) {
			errors.add("amount must be greater than zero, got: " + response.amount());
		}

		if (isBlank(response.currency())) {
			errors.add("currency is missing");
		} else if (!VALID_CURRENCIES.contains(response.currency().toUpperCase())) {
			errors.add("unrecognized currency: " + response.currency());
		}

		if (response.uploadedDate() == null) {
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