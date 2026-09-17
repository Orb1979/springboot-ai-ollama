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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceAnalyzerServiceTest {

	private static final String DEFAULT_EXTRACTED_TEXT = "Invoice text content";
	private static final String VALID_JSON_FAILURE_MESSAGE =
			"Model did not return valid JSON after 3 attempts";

	@Mock private ChatClient chatClient;
	@Mock private ChatClient.ChatClientRequestSpec requestSpec;
	@Mock private ChatClient.CallResponseSpec callResponseSpec;
	@Mock private FileTypeDetector fileTypeDetector;
	@Mock private TextExtractor textExtractor;
	@Mock private InvoiceValidator invoiceValidator;
	@Mock private InvoiceRepository invoiceRepository;
	@Mock private InvoiceEmbeddingService invoiceEmbeddingService;

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
		MockMultipartFile file = givenReadyToAnalyze(42L);

		Invoice actual = invoiceAnalyzerService.analyzeInvoice(file);

		assertExpectedInvoice(actual);
		assertThat(actual.getId()).isEqualTo(42L);
		verifyAnalyzePersisted();
		verify(invoiceEmbeddingService).indexInvoice(any(Invoice.class));
	}

	@Test
	void analyzeInvoice_mapsExtractedAndServerFieldsToSavedInvoice() throws Exception {
		MockMultipartFile file = givenReadyToAnalyze(7L);

		Invoice invoice = invoiceAnalyzerService.analyzeInvoice(file);

		assertExpectedInvoice(invoice);
		assertThat(invoice.getId()).isEqualTo(7L);

		ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
		verify(invoiceRepository).save(invoiceCaptor.capture());
		Invoice savedInvoice = invoiceCaptor.getValue();
		assertExpectedInvoice(savedInvoice);
		assertThat(savedInvoice.getUploadedDate()).isEqualTo(invoice.getUploadedDate());
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
		MockMultipartFile file = givenReadyToAnalyze(55L);
		doThrow(new InvoiceEmbedException("vector store down"))
				.when(invoiceEmbeddingService).indexInvoice(any(Invoice.class));

		assertThatThrownBy(() -> invoiceAnalyzerService.analyzeInvoice(file))
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
						"A", "B", "C", "D", "E", "F", "G",
						LocalDate.of(2024, 1, 1), new BigDecimal("1.00"), "EUR", null)))
				.isInstanceOf(InvoiceNotFoundException.class)
				.hasMessageContaining("404");
		verify(invoiceRepository, never()).save(any());
	}

	@Test
	void analyzeInvoice_capturesUploadedDateBeforeExtractionStarts() throws Exception {
		AtomicReference<Instant> extractionStarted = new AtomicReference<>();
		givenValidFileWithText(invocation -> {
			extractionStarted.set(Instant.now());
			return DEFAULT_EXTRACTED_TEXT;
		});
		mockChatClientChain(validInvoiceJson());
		givenPersistedInvoice(1L);

		Invoice invoice = invoiceAnalyzerService.analyzeInvoice(createTextFile());

		assertThat(invoice.getUploadedDate()).isBeforeOrEqualTo(extractionStarted.get());
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
		verifyAnalyzeDidNotPersist();
	}

	@ParameterizedTest
	@MethodSource("exhaustedRetryResponses")
	void analyzeInvoice_exhaustedRetries_throwsAndDoesNotPersist(String[] modelResponses) throws Exception {
		MockMultipartFile file = givenFileWithModelResponses(modelResponses);

		assertThatThrownBy(() -> invoiceAnalyzerService.analyzeInvoice(file))
				.isInstanceOf(InvoiceAnalyzeException.class)
				.hasMessageContaining(VALID_JSON_FAILURE_MESSAGE);

		verify(callResponseSpec, times(3)).content();
		verifyAnalyzeDidNotPersist();
	}

	@Test
	void analyzeInvoice_promptIncludesExtractedText() throws Exception {
		givenValidFileWithText("UNIQUE_MARKER_TEXT_12345");
		mockChatClientChain(validInvoiceJson());
		givenPersistedInvoice(1L);

		invoiceAnalyzerService.analyzeInvoice(createTextFile());

		assertThat(capturedUserPrompts()).singleElement()
				.asString()
				.contains("UNIQUE_MARKER_TEXT_12345");
	}

	@Test
	void analyzeInvoice_systemPromptIsProvided() throws Exception {
		MockMultipartFile file = givenReadyToAnalyze(1L);

		invoiceAnalyzerService.analyzeInvoice(file);

		ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
		verify(requestSpec).system(systemPrompt.capture());
		assertThat(systemPrompt.getValue())
				.contains("Extract the following information from this invoice")
				.doesNotContain("%s");
	}

	@Test
	void analyzeInvoice_invalidJsonThenValidJson_retriesAndSucceeds() throws Exception {
		MockMultipartFile file = givenReadyToAnalyze(1L, "THIS IS NOT VALID JSON", validInvoiceJson());

		Invoice actual = invoiceAnalyzerService.analyzeInvoice(file);

		assertExpectedInvoice(actual);
		verify(callResponseSpec, times(2)).content();
		verifyAnalyzePersisted();
	}

	@Test
	void analyzeInvoice_invalidJson_retryAddsCorrectionInstruction() throws Exception {
		MockMultipartFile file = givenReadyToAnalyze(1L, "INVALID JSON", validInvoiceJson());

		invoiceAnalyzerService.analyzeInvoice(file);

		List<String> prompts = capturedUserPrompts();
		assertThat(prompts).hasSize(2);
		assertThat(prompts.get(0))
				.contains(DEFAULT_EXTRACTED_TEXT)
				.doesNotContain("Your previous response could not be parsed");
		assertThat(prompts.get(1))
				.contains("Your previous response could not be parsed as valid JSON")
				.contains(DEFAULT_EXTRACTED_TEXT);
	}

	@ParameterizedTest
	@MethodSource("messyButParsableModelResponses")
	void analyzeInvoice_messyModelResponse_parsesSuccessfully(String modelResponse) throws Exception {
		MockMultipartFile file = givenReadyToAnalyze(1L, modelResponse);

		Invoice actual = invoiceAnalyzerService.analyzeInvoice(file);

		assertExpectedInvoice(actual);
		verify(callResponseSpec, times(1)).content();
		verifyAnalyzePersisted();
	}

	@Test
	void analyzeInvoice_validJson_doesNotRetry() throws Exception {
		MockMultipartFile file = givenReadyToAnalyze(1L);

		invoiceAnalyzerService.analyzeInvoice(file);

		verify(callResponseSpec, times(1)).content();
		verify(requestSpec, times(1)).user(anyString());
		verify(invoiceRepository, times(1)).save(any());
	}

	private static Stream<Arguments> exhaustedRetryResponses() {
		return Stream.of(
				Arguments.of((Object) new String[]{null, null, null}),
				Arguments.of((Object) new String[]{"INVALID JSON 1", "INVALID JSON 2", "INVALID JSON 3"})
		);
	}

	private static Stream<Arguments> messyButParsableModelResponses() {
		String json = validInvoiceJson();
		return Stream.of(
				Arguments.of("""
						```json
						%s
						```
						""".formatted(json.trim())),
				Arguments.of("""
						Here is the invoice information:
						%s
						Hope this helps!
						""".formatted(json.trim()))
		);
	}

	private MockMultipartFile givenReadyToAnalyze(Long id, String... modelResponses) throws Exception {
		String[] responses = modelResponses.length == 0
				? new String[]{validInvoiceJson()}
				: modelResponses;
		givenValidFile();
		mockChatClientChain(responses);
		givenPersistedInvoice(id);
		return createTextFile();
	}

	private MockMultipartFile givenFileWithModelResponses(String... modelResponses) throws Exception {
		givenValidFile();
		mockChatClientChain(modelResponses);
		return createTextFile();
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

	private void mockChatClientChain(String... responses) {
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.system(anyString())).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callResponseSpec);
		when(callResponseSpec.content()).thenReturn(responses[0], Arrays.copyOfRange(responses, 1, responses.length));
	}

	private void givenValidFile() throws Exception {
		givenValidFileWithText(DEFAULT_EXTRACTED_TEXT);
	}

	private void givenValidFileWithText(String invoiceText) throws Exception {
		givenValidFileWithText(invocation -> invoiceText);
	}

	private void givenValidFileWithText(org.mockito.stubbing.Answer<String> extractAnswer) throws Exception {
		when(fileTypeDetector.detect(any())).thenReturn(FileType.TEXT);
		when(textExtractor.supports(FileType.TEXT)).thenReturn(true);
		when(textExtractor.extract(any())).thenAnswer(extractAnswer);
	}

	private MockMultipartFile createTextFile() {
		return new MockMultipartFile(
				"file",
				"invoice.txt",
				"text/plain",
				"raw bytes".getBytes()
		);
	}

	private static String validInvoiceJson() {
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

	private List<String> capturedUserPrompts() {
		ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
		verify(requestSpec, atLeastOnce()).user(promptCaptor.capture());
		return promptCaptor.getAllValues();
	}

	private void verifyAnalyzePersisted() {
		verify(invoiceValidator).validate(any(Invoice.class));
		verify(invoiceRepository).save(any());
	}

	private void verifyAnalyzeDidNotPersist() {
		verify(invoiceValidator, never()).validate(any());
		verify(invoiceRepository, never()).save(any());
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
