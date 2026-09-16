package com.example.ollama.service;

import com.example.ollama.entity.Invoice;
import com.example.ollama.exception.InvoiceValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests - no Spring context, no mocks needed since the validator has zero dependencies.
 */
class InvoiceValidatorTest {

	private final InvoiceValidator validator = new InvoiceValidator();

	@Test
	void validInvoice_doesNotThrow() {
		Invoice invoice = validInvoice();

		validator.validate(invoice);
	}

	@Test
	void missingSupplier_throwsWithExpectedError() {
		Invoice invoice = invoice(
				null, "INV-001", new BigDecimal("100.00"), "EUR");

		assertThatThrownBy(() -> validator.validate(invoice))
				.isInstanceOf(InvoiceValidationException.class)
				.satisfies(ex -> assertThat(((InvoiceValidationException) ex).getErrors())
						                 .contains("supplier is missing"));
	}

	@Test
	void blankInvoiceNumber_throwsWithExpectedError() {
		Invoice invoice = invoice(
				"Acme Corp", "   ", new BigDecimal("100.00"), "EUR");

		assertThatThrownBy(() -> validator.validate(invoice))
				.isInstanceOf(InvoiceValidationException.class)
				.satisfies(ex -> assertThat(((InvoiceValidationException) ex).getErrors())
						                 .contains("invoice number is missing"));
	}

	@Test
	void nullAmount_throwsWithExpectedError() {
		Invoice invoice = invoice(
				"Acme Corp", "INV-001", null, "EUR");

		assertThatThrownBy(() -> validator.validate(invoice))
				.isInstanceOf(InvoiceValidationException.class)
				.satisfies(ex -> assertThat(((InvoiceValidationException) ex).getErrors())
						                 .contains("amount is missing"));
	}

	@ParameterizedTest
	@CsvSource({"0", "-50.00"})
	void nonPositiveAmount_throwsWithExpectedError(String amount) {
		Invoice invoice = invoice(
				"Acme Corp", "INV-001", new BigDecimal(amount), "EUR");

		assertThatThrownBy(() -> validator.validate(invoice))
				.isInstanceOf(InvoiceValidationException.class)
				.satisfies(ex -> assertThat(((InvoiceValidationException) ex).getErrors())
						                 .anyMatch(msg -> msg.startsWith("amount must be greater than zero")));
	}

	@Test
	void unrecognizedCurrency_throwsWithExpectedError() {
		Invoice invoice = invoice(
				"Acme Corp", "INV-001", new BigDecimal("100.00"), "GBP");

		assertThatThrownBy(() -> validator.validate(invoice))
				.isInstanceOf(InvoiceValidationException.class)
				.satisfies(ex -> assertThat(((InvoiceValidationException) ex).getErrors())
						                 .contains("unrecognized currency: GBP"));
	}

	@Test
	void currencyCheck_isCaseInsensitive() {
		Invoice invoice = invoice(
				"Acme Corp", "INV-001", new BigDecimal("100.00"), "eur");

		validator.validate(invoice);
	}

	@Test
	void missingRequiredInvoiceDetails_collectsExpectedErrors() {
		Invoice invoice = new Invoice(
				1L, "Acme Corp", null, "   ", null, null,
				"INV-001", null, new BigDecimal("100.00"), "EUR", null, null, null);

		assertThatThrownBy(() -> validator.validate(invoice))
				.isInstanceOf(InvoiceValidationException.class)
				.satisfies(ex -> assertThat(((InvoiceValidationException) ex).getErrors())
						                 .hasSize(6)
						                 .containsExactlyInAnyOrder(
								                 "supplier street is missing",
								                 "supplier street number is missing",
								                 "supplier city is missing",
								                 "supplier postal code is missing",
								                 "invoice date is missing",
								                 "uploaded date is missing"));
	}

	@Test
	void allFieldsMissing_collectsAllRequiredErrors() {
		Invoice invoice = new Invoice(
				null, null, null, null, null, null, null, null, null, null, null, null, null);

		assertThatThrownBy(() -> validator.validate(invoice))
				.isInstanceOf(InvoiceValidationException.class)
				.satisfies(ex -> assertThat(((InvoiceValidationException) ex).getErrors())
						                 .hasSize(10)
						                 .contains(
								                 "supplier is missing",
								                 "supplier street is missing",
								                 "supplier street number is missing",
								                 "supplier city is missing",
								                 "supplier postal code is missing",
								                 "invoice number is missing",
								                 "invoice date is missing",
								                 "amount is missing",
								                 "currency is missing",
								                 "uploaded date is missing"));
	}

	private Invoice validInvoice() {
		return invoice("Acme Corp", "INV-001", new BigDecimal("100.00"), "EUR");
	}

	private Invoice invoice(
			String supplier,
			String invoiceNumber,
			BigDecimal amount,
			String currency) {
		return new Invoice(
				1L,
				supplier,
				"Main Street",
				"42A",
				"Amsterdam",
				"1012 AB",
				invoiceNumber,
				LocalDate.of(2024, 3, 12),
				amount,
				currency,
				Instant.parse("2026-09-15T10:00:00Z"),
				null,
				null
		);
	}
}
