package com.example.ollama.service;

import com.example.ollama.domain.FileType;
import com.example.ollama.dto.InvoiceUpdateRequest;
import com.example.ollama.entity.Invoice;
import com.example.ollama.exception.InvoiceAnalyzeException;
import com.example.ollama.exception.InvoiceEmbedException;
import com.example.ollama.exception.InvoiceNotFoundException;
import com.example.ollama.repo.InvoiceRepository;
import com.example.ollama.service.extractors.TextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceAnalyzerServiceTest {

	@Mock	private ChatClient chatClient;
	@Mock	private ChatClient.ChatClientRequestSpec requestSpec;
	@Mock	private ChatClient.CallResponseSpec callResponseSpec;
	@Mock	private FileTypeDetector fileTypeDetector;
	@Mock	private TextExtractor textExtractor;
	@Mock	private InvoiceValidator invoiceValidator;
	@Mock	private InvoiceRepository invoiceRepository;
	@Mock	private InvoiceEmbeddingService invoiceEmbeddingService;
	private InvoiceAnalyzerService invoiceAnalyzerService;

	@BeforeEach
	void setUp() {
		invoiceAnalyzerService = new InvoiceAnalyzerService(
				chatClient,
				fileTypeDetector,
				List.of(textExtractor),
				invoiceValidator,
				invoiceRepository,
				invoiceEmbeddingService
		);
	}

	@Test
	void analyzeInvoice_happyPath_extractsValidatesAndSaves() throws Exception {
		MockMultipartFile file = createTextFile();
		String validJson = validInvoiceJson();
		givenValidFile();
		mockChatClientChain(validJson);
		givenPersistedInvoice(42L);

		Invoice actual = invoiceAnalyzerService.analyzeInvoice(file);

		assertExpectedInvoice(actual);
		assertThat(actual.getId()).isEqualTo(42L);
		assertThat(actual.getUpdatedDate()).isNull();
		verify(invoiceValidator).validate(any(Invoice.class));
		verify(invoiceRepository).save(any());
		verify(invoiceEmbeddingService).indexInvoice(any(Invoice.class));
	}

	@Test
	void analyzeInvoice_mapsExtractedAndServerFieldsToSavedInvoice() throws Exception {
		givenValidFile();
		mockChatClientChain(validInvoiceJson());
		givenPersistedInvoice(7L);

		Invoice invoice = invoiceAnalyzerService.analyzeInvoice(createTextFile());

		assertThat(invoice.getId()).isEqualTo(7L);
		assertThat(invoice.getSupplierStreet()).isEqualTo("Main Street");
		assertThat(invoice.getSupplierStreetNumber()).isEqualTo("42A");
		assertThat(invoice.getSupplierCity()).isEqualTo("Amsterdam");
		assertThat(invoice.getSupplierPostalCode()).isEqualTo("1012 AB");
		assertThat(invoice.getInvoiceDate()).isEqualTo(LocalDate.of(2024, 3, 12));
		assertThat(invoice.getUploadedDate()).isNotNull();
		assertThat(invoice.getPaymentReceivedDate()).isNull();
		assertThat(invoice.getUpdatedDate()).isNull();

		ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
		verify(invoiceRepository).save(invoiceCaptor.capture());
		Invoice savedInvoice = invoiceCaptor.getValue();
		assertThat(savedInvoice.getSupplierStreet()).isEqualTo("Main Street");
		assertThat(savedInvoice.getSupplierStreetNumber()).isEqualTo("42A");
		assertThat(savedInvoice.getSupplierCity()).isEqualTo("Amsterdam");
		assertThat(savedInvoice.getSupplierPostalCode()).isEqualTo("1012 AB");
		assertThat(savedInvoice.getInvoiceDate()).isEqualTo(LocalDate.of(2024, 3, 12));
		assertThat(savedInvoice.getUploadedDate()).isEqualTo(invoice.getUploadedDate());
		assertThat(savedInvoice.getPaymentReceivedDate()).isNull();
		assertThat(savedInvoice.getUpdatedDate()).isNull();
	}

	@Test
	void listInvoices_returnsEntities() {
		Invoice invoice = sampleInvoice(5L, Instant.parse("2026-09-15T10:00:00Z"), null);
		when(invoiceRepository.findAll()).thenReturn(List.of(invoice));

		List<Invoice> invoices = invoiceAnalyzerService.listInvoices();

		assertThat(invoices).hasSize(1);
		assertThat(invoices.getFirst().getId()).isEqualTo(5L);
		assertThat(invoices.getFirst().getSupplier()).isEqualTo("Acme Corp");
		assertThat(invoices.getFirst().getUpdatedDate()).isNull();
	}

	@Test
	void updateInvoice_updatesFieldsAndSetsUpdatedDate() {
		Invoice existing = sampleInvoice(9L, Instant.parse("2026-09-15T09:00:00Z"), null);
		when(invoiceRepository.findById(9L)).thenReturn(Optional.of(existing));
		when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

		InvoiceUpdateRequest update = new InvoiceUpdateRequest(
				"Updated Corp",
				"New Street",
				"99",
				"3500 AA",
				"Utrecht",
				"Netherlands",
				"INV-009",
				LocalDate.of(2024, 4, 1),
				new BigDecimal("150.00"),
				"USD",
				Instant.parse("2024-05-01T11:22:00Z")
		);

		Invoice result = invoiceAnalyzerService.updateInvoice(9L, update);

		assertThat(result.getSupplier()).isEqualTo("Updated Corp");
		assertThat(result.getSupplierStreet()).isEqualTo("New Street");
		assertThat(result.getUploadedDate()).isEqualTo(Instant.parse("2026-09-15T09:00:00Z"));
		assertThat(result.getPaymentReceivedDate()).isEqualTo(Instant.parse("2024-05-01T11:22:00Z"));
		assertThat(result.getUpdatedDate()).isNotNull();
		verify(invoiceValidator).validate(any(Invoice.class));
		verify(invoiceEmbeddingService).indexInvoice(any(Invoice.class));
	}

	@Test
	void analyzeInvoice_indexingFailure_deletesSavedInvoice() throws Exception {
		givenValidFile();
		mockChatClientChain(validInvoiceJson());
		givenPersistedInvoice(55L);

		doThrow(new InvoiceEmbedException("vector store down"))
				.when(invoiceEmbeddingService).indexInvoice(any(Invoice.class));

		assertThatThrownBy(() -> invoiceAnalyzerService.analyzeInvoice(createTextFile()))
				.isInstanceOf(InvoiceEmbedException.class);

		verify(invoiceRepository).deleteById(55L);
		verify(invoiceEmbeddingService).removeInvoice(55L);
	}

	@Test
	void updateInvoice_missingInvoice_throwsNotFound() {
		when(invoiceRepository.findById(404L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> invoiceAnalyzerService.updateInvoice(
				404L,
				new InvoiceUpdateRequest(
						"A", "B", "C", "D", "E", "F", "G", LocalDate.of(2024, 1, 1), new BigDecimal("1.00"), "EUR", null)))
				.isInstanceOf(InvoiceNotFoundException.class)
				.hasMessageContaining("404");
		verify(invoiceRepository, never()).save(any());
	}

	@Test
	void analyzeInvoice_capturesUploadedDateBeforeExtractionStarts() throws Exception {
		AtomicReference<Instant> extractionStarted = new AtomicReference<>();
		when(fileTypeDetector.detect(any())).thenReturn(FileType.TEXT);
		when(textExtractor.supports(FileType.TEXT)).thenReturn(true);
		when(textExtractor.extract(any())).thenAnswer(invocation -> {
			extractionStarted.set(Instant.now());
			return "Invoice text content";
		});
		mockChatClientChain(validInvoiceJson());
		givenPersistedInvoice(1L);

		Invoice invoice = invoiceAnalyzerService.analyzeInvoice(createTextFile());

		assertThat(invoice.getUploadedDate())
				.isBeforeOrEqualTo(extractionStarted.get());
	}

	@Test
	void analyzeInvoice_noMatchingExtractor_throwsInvoiceAnalyzeException() {
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"photo.png",
				"image/png",
				new byte[]{1, 2, 3}
		);
		when(fileTypeDetector.detect(any())).thenReturn(FileType.IMAGE);
		when(textExtractor.supports(FileType.IMAGE)).thenReturn(false);

		assertThatThrownBy(() -> invoiceAnalyzerService.analyzeInvoice(file))
				.isInstanceOf(InvoiceAnalyzeException.class)
				.hasMessageContaining("No TextExtractor found for file type: IMAGE");
		verify(invoiceRepository, never()).save(any());
		verify(invoiceValidator, never()).validate(any());
	}

	@Test
	void analyzeInvoice_modelReturnsEmptyResponse_retriesAndEventuallyFails() throws Exception {
		MockMultipartFile file = createTextFile();
		givenValidFile();
		mockChatClientChain(null, null, null);

		assertThatThrownBy(() -> invoiceAnalyzerService.analyzeInvoice(file))
				.isInstanceOf(InvoiceAnalyzeException.class)
				.hasMessageContaining("Model did not return valid JSON after 3 attempts");

		verify(callResponseSpec, times(3)).content();
		verify(invoiceValidator, never()).validate(any());
		verify(invoiceRepository, never()).save(any());
	}

	@Test
	void analyzeInvoice_promptIncludesExtractedText() throws Exception {
		MockMultipartFile file = createTextFile();
		givenValidFileWithText("UNIQUE_MARKER_TEXT_12345");
		mockChatClientChain(validInvoiceJson());
		givenPersistedInvoice(1L);
		invoiceAnalyzerService.analyzeInvoice(file);

		ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);

		verify(requestSpec) .user(promptCaptor.capture());
		assertThat(promptCaptor.getValue()).contains("UNIQUE_MARKER_TEXT_12345");
	}

	@Test
	void analyzeInvoice_systemPromptIsProvided() throws Exception {
		MockMultipartFile file = createTextFile();
		givenValidFile();
		mockChatClientChain(validInvoiceJson());
		givenPersistedInvoice(1L);

		invoiceAnalyzerService.analyzeInvoice(file);

		ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
		verify(requestSpec).system(systemPrompt.capture());
		assertThat(systemPrompt.getValue())
				.contains("Extract the following information from this invoice")
				.doesNotContain("%s");
	}

	@Test
	void analyzeInvoice_invalidJsonThenValidJson_retriesAndSucceeds() throws Exception {
		MockMultipartFile file = createTextFile();
		givenValidFile();

		mockChatClientChain("THIS IS NOT VALID JSON", validInvoiceJson());
		givenPersistedInvoice(1L);

		Invoice actual = invoiceAnalyzerService.analyzeInvoice(file);

		assertExpectedInvoice(actual);

		verify(callResponseSpec, times(2)).content();
		verify(invoiceValidator).validate(any(Invoice.class));
		verify(invoiceRepository).save(any());
	}

	@Test
	void analyzeInvoice_invalidJsonAfterAllRetries_throwsException() throws Exception {

		MockMultipartFile file = createTextFile();
		givenValidFile();
		mockChatClientChain("INVALID JSON 1", "INVALID JSON 2", "INVALID JSON 3");

		assertThatThrownBy(() -> invoiceAnalyzerService.analyzeInvoice(file))
				.isInstanceOf(InvoiceAnalyzeException.class)
				.hasMessageContaining(
						"Model did not return valid JSON after 3 attempts"
				);

		verify(callResponseSpec, times(3)).content();
		verify(invoiceValidator, never()).validate(any());
		verify(invoiceRepository, never()).save(any());
	}

	@Test
	void analyzeInvoice_invalidJson_retryAddsCorrectionInstruction() throws Exception {
		MockMultipartFile file = createTextFile();
		givenValidFile();
		mockChatClientChain("INVALID JSON", validInvoiceJson());
		givenPersistedInvoice(1L);

		invoiceAnalyzerService.analyzeInvoice(file);

		ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);

		verify(requestSpec, times(2)).user(promptCaptor.capture());

		List<String> prompts = promptCaptor.getAllValues();

		assertThat(prompts.get(0)).contains("Invoice text content");
		assertThat(prompts.get(0)).doesNotContain("Your previous response could not be parsed");
		assertThat(prompts.get(1)).contains("Your previous response could not be parsed as valid JSON");
		assertThat(prompts.get(1)).contains("Invoice text content");
	}

	@Test
	void analyzeInvoice_modelReturnsJsonWrappedInMarkdown_parsesSuccessfully()  throws Exception {
		MockMultipartFile file = createTextFile();
		givenValidFile();
		String wrappedJson = """
        ```json
        {
          "supplier": "Acme Corp",
          "supplierStreet": "Main Street",
          "supplierStreetNumber": "42A",
          "supplierCity": "Amsterdam",
          "supplierPostalCode": "1012 AB",
          "supplierCountry": "Netherlands",
          "invoiceNumber": "INV-001",
          "invoiceDate": "2024-03-12",
          "amount": 99.90,
          "currency": "EUR"
        }
        ```
        """;
		mockChatClientChain(wrappedJson);
		givenPersistedInvoice(1L);
		Invoice actual = invoiceAnalyzerService.analyzeInvoice(file);

		assertExpectedInvoice(actual);

		verify(callResponseSpec, times(1)).content();
		verify(invoiceValidator).validate(any(Invoice.class));
		verify(invoiceRepository).save(any());
	}

	@Test
	void analyzeInvoice_modelReturnsTextAroundJson_parsesSuccessfully() throws Exception {
		MockMultipartFile file = createTextFile();
		givenValidFile();
		String responseWithExtraText = """
        Here is the invoice information:
        {
          "supplier": "Acme Corp",
          "supplierStreet": "Main Street",
          "supplierStreetNumber": "42A",
          "supplierCity": "Amsterdam",
          "supplierPostalCode": "1012 AB",
          "supplierCountry": "Netherlands",
          "invoiceNumber": "INV-001",
          "invoiceDate": "2024-03-12",
          "amount": 99.90,
          "currency": "EUR"
        }
        Hope this helps!
        """;
		mockChatClientChain(responseWithExtraText);
		givenPersistedInvoice(1L);

		Invoice actual = invoiceAnalyzerService.analyzeInvoice(file);

		assertExpectedInvoice(actual);
		verify(invoiceValidator).validate(any(Invoice.class));
		verify(invoiceRepository).save(any());
	}

	@Test
	void analyzeInvoice_validJson_doesNotRetry()
			throws Exception {

		MockMultipartFile file = createTextFile();
		givenValidFile();
		mockChatClientChain(validInvoiceJson());
		givenPersistedInvoice(1L);

		invoiceAnalyzerService.analyzeInvoice(file);

		verify(callResponseSpec, times(1)).content();
		verify(requestSpec, times(1)).user(anyString());
		verify(invoiceRepository, times(1)).save(any());
	}

	private void givenPersistedInvoice(Long id) {
		when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> {
			Invoice invoice = invocation.getArgument(0);
			invoice.setId(id);
			return invoice;
		});
	}

	private Invoice sampleInvoice(Long id, Instant uploadedDate, Instant updatedDate) {
		return new Invoice(
				id,
				"Acme Corp",
				"Main Street",
				"42A",
				"1012 AB",
				"Amsterdam",
				"Netherlands",
				"INV-001",
				LocalDate.of(2024, 3, 12),
				new BigDecimal("99.90"),
				"EUR",
				uploadedDate,
				null,
				updatedDate
		);
	}

	private void mockChatClientChain(String firstResponse, String... subsequentResponses) {
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.system(anyString())).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callResponseSpec);
		when(callResponseSpec.content()).thenReturn(firstResponse, subsequentResponses);
	}

	private void givenValidFile() throws Exception {
		givenValidFileWithText("Invoice text content");
	}

	private void givenValidFileWithText(String invoiceText) throws Exception {
		when(fileTypeDetector.detect(any())).thenReturn(FileType.TEXT);
		when(textExtractor.supports(FileType.TEXT)).thenReturn(true);
		when(textExtractor.extract(any())).thenReturn(invoiceText);
	}

	private MockMultipartFile createTextFile() {
		return new MockMultipartFile(
				"file",
				"invoice.txt",
				"text/plain",
				"raw bytes".getBytes()
		);
	}

	private String validInvoiceJson() {
		return """
        {
          "supplier": "Acme Corp",
          "supplierStreet": "Main Street",
          "supplierStreetNumber": "42A",
          "supplierPostalCode": "1012 AB",
          "supplierCity": "Amsterdam",
          "supplierCountry": "Netherlands",
          "invoiceNumber": "INV-001",
          "invoiceDate": "2024-03-12",
          "amount": 99.90,
          "currency": "EUR"
        }
        """;
	}

	private void assertExpectedInvoice(Invoice invoice) {
		assertThat(invoice.getId()).isNotNull();
		assertThat(invoice.getSupplier()).isEqualTo("Acme Corp");
		assertThat(invoice.getSupplierStreet()).isEqualTo("Main Street");
		assertThat(invoice.getSupplierStreetNumber()).isEqualTo("42A");
		assertThat(invoice.getSupplierPostalCode()).isEqualTo("1012 AB");
		assertThat(invoice.getSupplierCity()).isEqualTo("Amsterdam");
		assertThat(invoice.getSupplierCountry()).isEqualTo("Netherlands");
		assertThat(invoice.getInvoiceNumber()).isEqualTo("INV-001");
		assertThat(invoice.getInvoiceDate()).isEqualTo(LocalDate.of(2024, 3, 12));
		assertThat(invoice.getAmount()).isEqualByComparingTo("99.90");
		assertThat(invoice.getCurrency()).isEqualTo("EUR");
		assertThat(invoice.getUploadedDate()).isNotNull();
		assertThat(invoice.getPaymentReceivedDate()).isNull();
		assertThat(invoice.getUpdatedDate()).isNull();
	}
}
