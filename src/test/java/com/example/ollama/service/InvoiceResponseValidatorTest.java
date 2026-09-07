package com.example.ollama.service;

import com.example.ollama.dto.InvoiceResponse;
import com.example.ollama.exception.InvoiceValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests - no Spring context, no mocks needed since the validator has zero dependencies.
 */
class InvoiceResponseValidatorTest {

	private final InvoiceResponseValidator validator = new InvoiceResponseValidator();

	@Test
	void validResponse_doesNotThrow() {
		InvoiceResponse response = new InvoiceResponse(
				"Acme Corp", "INV-001", new BigDecimal("100.00"), "EUR");

		validator.validate(response);

	}

	@Test
	void missingSupplier_throwsWithExpectedError() {
		InvoiceResponse response = new InvoiceResponse(
				null, "INV-001", new BigDecimal("100.00"), "EUR");

		assertThatThrownBy(() -> validator.validate(response))
				.isInstanceOf(InvoiceValidationException.class)
				.satisfies(ex -> assertThat(((InvoiceValidationException) ex).getErrors())
						                 .contains("supplier is missing"));
	}

	@Test
	void blankInvoiceNumber_throwsWithExpectedError() {
		InvoiceResponse response = new InvoiceResponse(
				"Acme Corp", "   ", new BigDecimal("100.00"), "EUR");

		assertThatThrownBy(() -> validator.validate(response))
				.isInstanceOf(InvoiceValidationException.class)
				.satisfies(ex -> assertThat(((InvoiceValidationException) ex).getErrors())
						                 .contains("invoice number is missing"));
	}

	@Test
	void nullAmount_throwsWithExpectedError() {
		InvoiceResponse response = new InvoiceResponse(
				"Acme Corp", "INV-001", null, "EUR");

		assertThatThrownBy(() -> validator.validate(response))
				.isInstanceOf(InvoiceValidationException.class)
				.satisfies(ex -> assertThat(((InvoiceValidationException) ex).getErrors())
						                 .contains("amount is missing"));
	}

	@ParameterizedTest
	@CsvSource({"0", "-50.00"})
	void nonPositiveAmount_throwsWithExpectedError(String amount) {
		InvoiceResponse response = new InvoiceResponse(
				"Acme Corp", "INV-001", new BigDecimal(amount), "EUR");

		assertThatThrownBy(() -> validator.validate(response))
				.isInstanceOf(InvoiceValidationException.class)
				.satisfies(ex -> assertThat(((InvoiceValidationException) ex).getErrors())
						                 .anyMatch(msg -> msg.startsWith("amount must be greater than zero")));
	}

	@Test
	void unrecognizedCurrency_throwsWithExpectedError() {
		InvoiceResponse response = new InvoiceResponse(
				"Acme Corp", "INV-001", new BigDecimal("100.00"), "GBP");

		assertThatThrownBy(() -> validator.validate(response))
				.isInstanceOf(InvoiceValidationException.class)
				.satisfies(ex -> assertThat(((InvoiceValidationException) ex).getErrors())
						                 .contains("unrecognized currency: GBP"));
	}

	@Test
	void currencyCheck_isCaseInsensitive() {
		InvoiceResponse response = new InvoiceResponse(
				"Acme Corp", "INV-001", new BigDecimal("100.00"), "eur");

		validator.validate(response);
		// no exception = pass, proves toUpperCase() normalization works
	}

	@Test
	void allFieldsMissing_collectsAllFourErrors() {
		InvoiceResponse response = new InvoiceResponse(null, null, null, null);

		assertThatThrownBy(() -> validator.validate(response))
				.isInstanceOf(InvoiceValidationException.class)
				.satisfies(ex -> assertThat(((InvoiceValidationException) ex).getErrors())
						                 .hasSize(4)
						                 .containsExactlyInAnyOrder(
								                 "supplier is missing",
								                 "invoice number is missing",
								                 "amount is missing",
								                 "currency is missing"));
	}
}
