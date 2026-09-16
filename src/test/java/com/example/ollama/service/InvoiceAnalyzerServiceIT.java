package com.example.ollama.service;

import com.example.ollama.entity.Invoice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

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
@ActiveProfiles("test")
class InvoiceAnalyzerServiceIT {

	@Autowired
	private InvoiceAnalyzerService invoiceAnalyzerService;

	@Test
	void testEnvironment() {
		System.out.println("OPENAI_API_KEY = " + System.getenv("OPENAI_API_KEY"));
	}

	@ParameterizedTest
	@CsvSource({
			// filename, expected supplier, street, number, city, postal code, invoice number, invoice date, amount, currency
			"simple-invoice.txt, Acme, Industrial Way, 123, Springfield, 62704, INV-001, 2024-03-12, 99.90, EUR",
			"dutch-invoice.txt, Jansen, Fabrieksstraat, 22, Drachten, 9203 AB, FACT-2024-0088, 2024-05-14, 249.00, EUR"
	})
	void analyzeInvoice_realModel(
			String fileName,
			String expectedSupplierContains,
			String expectedStreetContains,
			String expectedStreetNumber,
			String expectedCity,
			String expectedPostalCode,
			String expectedInvoiceNumber,
			LocalDate expectedInvoiceDate,
			String expectedAmount,
			String expectedCurrency) throws IOException {

		byte[] bytes = Files.readAllBytes(Path.of("src/test/resources/invoices/" + fileName));
		MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain", bytes);

		Invoice invoice = invoiceAnalyzerService.analyzeInvoice(file);

		assertThat(invoice.getSupplier()).containsIgnoringCase(expectedSupplierContains);
		assertThat(invoice.getSupplierStreet()).isEqualToIgnoringCase(expectedStreetContains);
		assertThat(invoice.getSupplierStreetNumber()).isEqualToIgnoringCase(expectedStreetNumber);
		assertThat(invoice.getSupplierCity()).isEqualToIgnoringCase(expectedCity);
		assertThat(invoice.getSupplierPostalCode()).isEqualToIgnoringCase(expectedPostalCode);
		assertThat(invoice.getInvoiceNumber()).isEqualToIgnoringCase(expectedInvoiceNumber);
		assertThat(invoice.getInvoiceDate()).isEqualTo(expectedInvoiceDate);
		assertThat(invoice.getAmount()).isEqualByComparingTo(new BigDecimal(expectedAmount));
		assertThat(invoice.getCurrency()).isEqualToIgnoringCase(expectedCurrency);
		assertThat(invoice.getId()).isNotNull();
		assertThat(invoice.getUploadedDate()).isNotNull();
		assertThat(invoice.getPaymentReceivedDate()).isNull();
		assertThat(invoice.getUpdatedDate()).isNull();
	}

	@Test
	void analyzeInvoice_image_realModel() throws IOException {
		byte[] bytes = Files.readAllBytes(Path.of("src/test/resources/invoices/dutch-invoice.png"));
		MockMultipartFile file = new MockMultipartFile("file", "dutch-invoice.png", "image/png", bytes);

		Invoice invoice = invoiceAnalyzerService.analyzeInvoice(file);

		// note: getting value from image in rare occasions gives the wrong value (at least with my local model)
		assertThat(invoice.getAmount()).isEqualByComparingTo(new BigDecimal("249.00"));
	}

	@Test
	void analyzeInvoice_realModel_ignoresSubtotalAndUsesFinalTotal() throws IOException {
		byte[] bytes = Files.readAllBytes(Path.of("src/test/resources/invoices/invoice-with-subtotal.txt"));
		MockMultipartFile file = new MockMultipartFile("file", "invoice-with-subtotal.txt", "text/plain", bytes);

		Invoice invoice = invoiceAnalyzerService.analyzeInvoice(file);

		assertThat(invoice.getAmount()).isEqualByComparingTo(new BigDecimal("121.00"));
	}
}
