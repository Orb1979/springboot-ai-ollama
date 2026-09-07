package com.example.ollama.service;

import com.example.ollama.dto.InvoiceResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REAL Ollama integration tests - these actually call the local model.
 *
 * Why "IT" and not "Test":
 * - Excluded from `./gradlew test` (only *Test classes match by default),
 *   so your normal build stays fast and deterministic.
 *
 *   Don't use strict comparison because AI might not always respond in exactly the same way
 *   e.g. use:.isEqualByComparingTo() or isEqualToIgnoringCase
 *
 * Put sample invoices in src/test/resources/invoices/*.txt so this file
 * stays readable and the fixtures are easy to add to.
 */
@SpringBootTest
class InvoiceAnalyzerIT {

	@Autowired
	private InvoiceAnalyzer invoiceAnalyzer;

	@ParameterizedTest
	@CsvSource({
			// filename,            expectedSupplierContains, expectedInvoiceNumber, expectedAmount, expectedCurrency
			"simple-invoice.txt,    Acme,                     INV-001,               99.90,          EUR",
			"dutch-invoice.txt,     Jansen,                   FACT-2024-0088,        249.00,         EUR"
	})
	void analyzeInvoice_realModel(
			String fileName,
			String expectedSupplierContains,
			String expectedInvoiceNumber,
			String expectedAmount,
			String expectedCurrency) throws IOException {

		byte[] bytes = Files.readAllBytes(Path.of("src/test/resources/invoices/" + fileName));
		MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain", bytes);

		InvoiceResponse response = invoiceAnalyzer.analyzeInvoice(file);

		assertThat(response.supplier()).containsIgnoringCase(expectedSupplierContains);
		assertThat(response.invoiceNumber()).isEqualToIgnoringCase(expectedInvoiceNumber);
		assertThat(response.amount()).isEqualByComparingTo(new BigDecimal(expectedAmount));
		assertThat(response.currency()).isEqualToIgnoringCase(expectedCurrency);
	}

	@Test
	void analyzeInvoice_image_realModel() throws IOException {
		byte[] bytes = Files.readAllBytes(Path.of("src/test/resources/invoices/dutch-invoice.png"));
		MockMultipartFile file = new MockMultipartFile("file", "dutch-invoice.png", "image/png", bytes);

		InvoiceResponse response = invoiceAnalyzer.analyzeInvoice(file);

		assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("249.00"));
	}

	@Test
	void analyzeInvoice_realModel_ignoresSubtotalAndUsesFinalTotal() throws IOException {
		byte[] bytes = Files.readAllBytes(Path.of("src/test/resources/invoices/invoice-with-subtotal.txt"));
		MockMultipartFile file = new MockMultipartFile("file", "invoice-with-subtotal.txt", "text/plain", bytes);

		InvoiceResponse response = invoiceAnalyzer.analyzeInvoice(file);

		assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("121.00"));
	}


}
