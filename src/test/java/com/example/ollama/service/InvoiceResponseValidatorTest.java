package com.example.ollama.service;

import com.example.ollama.dto.InvoiceResponse;
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
class InvoiceResponseValidatorTest {

	private final InvoiceResponseValidator validator = new InvoiceResponseValidator();

	@Test
	void validResponse_doesNotThrow() {
		InvoiceResponse response = validResponse();

		validator.validate(response);

	}

	@Test
	void missingSupplier_throwsWithExpectedError() {
		InvoiceResponse response = response(
				null, "INV-001", new BigDecimal("100.00"), "EUR");

		assertThatThrownBy(() -> validator.validate(response))
				.isInstanceOf(InvoiceValidationException.class)
				.satisfies(ex -> assertThat(((InvoiceValidationException) ex).getErrors())
						                 .contains("supplier is missing"));
	}

	@Test
	void blankInvoiceNumber_throwsWithExpectedError() {
		InvoiceResponse response = response(
				"Acme Corp", "   ", new BigDecimal("100.00"), "EUR");

		assertThatThrownBy(() -> validator.validate(response))
				.isInstanceOf(InvoiceValidationException.class)
				.satisfies(ex -> assertThat(((InvoiceValidationException) ex).getErrors())
						                 .contains("invoice number is missing"));
	}

	@Test
	void nullAmount_throwsWithExpectedError() {
		InvoiceResponse response = response(
				"Acme Corp", "INV-001", null, "EUR");

		assertThatThrownBy(() -> validator.validate(response))
				.isInstanceOf(InvoiceValidationException.class)
				.satisfies(ex -> assertThat(((InvoiceValidationException) ex).getErrors())
						                 .contains("amount is missing"));
	}

	@ParameterizedTest
	@CsvSource({"0", "-50.00"})
	void nonPositiveAmount_throwsWithExpectedError(String amount) {
		InvoiceResponse response = response(
				"Acme Corp", "INV-001", new BigDecimal(amount), "EUR");

		assertThatThrownBy(() -> validator.validate(response))
				.isInstanceOf(InvoiceValidationException.class)
				.satisfies(ex -> assertThat(((InvoiceValidationException) ex).getErrors())
						                 .anyMatch(msg -> msg.startsWith("amount must be greater than zero")));
	}

	@Test
	void unrecognizedCurrency_throwsWithExpectedError() {
		InvoiceResponse response = response(
				"Acme Corp", "INV-001", new BigDecimal("100.00"), "GBP");

		assertThatThrownBy(() -> validator.validate(response))
				.isInstanceOf(InvoiceValidationException.class)
				.satisfies(ex -> assertThat(((InvoiceValidationException) ex).getErrors())
						                 .contains("unrecognized currency: GBP"));
	}

	@Test
	void currencyCheck_isCaseInsensitive() {
		InvoiceResponse response = response(
				"Acme Corp", "INV-001", new BigDecimal("100.00"), "eur");

		validator.validate(response);
		// no exception = pass, proves toUpperCase() normalization works
	}

	@Test
	void missingRequiredInvoiceDetails_collectsExpectedErrors() {
		InvoiceResponse response = new InvoiceResponse(
				1L, "Acme Corp", null, "   ", null, null,
				"INV-001", null, new BigDecimal("100.00"), "EUR", null, null, null);

		assertThatThrownBy(() -> validator.validate(response))
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
		InvoiceResponse response = new InvoiceResponse(
				null, null, null, null, null, null, null, null, null, null, null, null, null);

		assertThatThrownBy(() -> validator.validate(response))
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

	private InvoiceResponse validResponse() {
		return response("Acme Corp", "INV-001", new BigDecimal("100.00"), "EUR");
	}

	private InvoiceResponse response(
			String supplier,
			String invoiceNumber,
			BigDecimal amount,
			String currency) {
		return new InvoiceResponse(
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
