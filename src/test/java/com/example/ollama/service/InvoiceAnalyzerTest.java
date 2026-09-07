package com.example.ollama.service;

import com.example.ollama.domain.FileType;
import com.example.ollama.dto.InvoiceResponse;
import com.example.ollama.exception.InvoiceAnalyzeException;
import com.example.ollama.repo.InvoiceRepository;
import com.example.ollama.service.extractors.TextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * These tests do NOT call a real model. They mock ChatClient's fluent chain
 * so we can verify InvoiceAnalyzer service logic: extractor selection, error handling, validate and save sequence.
 */
class InvoiceAnalyzerTest {

	@Mock private ChatClient.Builder chatClientBuilder;
	@Mock private ChatClient chatClient;
	@Mock private ChatClient.ChatClientRequestSpec requestSpec;
	@Mock private ChatClient.CallResponseSpec callResponseSpec;
	@Mock private FileTypeDetector fileTypeDetector;
	@Mock private TextExtractor textExtractor;
	@Mock private InvoiceResponseValidator invoiceResponseValidator;
	@Mock private InvoiceRepository invoiceRepository;

	private InvoiceAnalyzer invoiceAnalyzer;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		when(chatClientBuilder.build()).thenReturn(chatClient);

		invoiceAnalyzer = new InvoiceAnalyzer(
				chatClientBuilder,
				fileTypeDetector,
				List.of(textExtractor),
				invoiceResponseValidator,
				invoiceRepository);
	}

	@Test
	void analyzeInvoice_happyPath_extractsValidatesAndSaves() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file", "invoice.txt", "text/plain", "raw bytes".getBytes());
		InvoiceResponse expected = new InvoiceResponse(
				"Acme Corp", "INV-001", new BigDecimal("99.90"), "EUR");

		when(fileTypeDetector.detect(any())).thenReturn(FileType.TEXT);
		when(textExtractor.supports(FileType.TEXT)).thenReturn(true);
		when(textExtractor.extract(any())).thenReturn("Invoice text content");
		mockChatClientChain(expected);

		InvoiceResponse actual = invoiceAnalyzer.analyzeInvoice(file);

		assertThat(actual).isEqualTo(expected);
		verify(invoiceResponseValidator).validate(expected);
		verify(invoiceRepository).save(any());
	}

	@Test
	void analyzeInvoice_noMatchingExtractor_throwsInvoiceAnalyzeException() {
		MockMultipartFile file = new MockMultipartFile(
				"file", "photo.png", "image/png", new byte[]{1, 2, 3});

		when(fileTypeDetector.detect(any())).thenReturn(FileType.IMAGE);
		when(textExtractor.supports(FileType.IMAGE)).thenReturn(false);

		assertThatThrownBy(() -> invoiceAnalyzer.analyzeInvoice(file))
				.isInstanceOf(InvoiceAnalyzeException.class)
				.hasMessageContaining("No TextExtractor found for file type: IMAGE");
	}

	@Test
	void analyzeInvoice_modelReturnsNull_throwsInvoiceAnalyzeException() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file", "invoice.txt", "text/plain", "raw bytes".getBytes());

		when(fileTypeDetector.detect(any())).thenReturn(FileType.TEXT);
		when(textExtractor.supports(FileType.TEXT)).thenReturn(true);
		when(textExtractor.extract(any())).thenReturn("Invoice text content");
		mockChatClientChain(null);

		assertThatThrownBy(() -> invoiceAnalyzer.analyzeInvoice(file))
				.isInstanceOf(InvoiceAnalyzeException.class)
				.hasMessageContaining("Failed to extract invoice information");
	}

	@Test
	void analyzeInvoice_promptIncludesExtractedText() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file", "invoice.txt", "text/plain", "raw bytes".getBytes());
		InvoiceResponse response = new InvoiceResponse(
				"Acme Corp", "INV-001", new BigDecimal("10.00"), "EUR");

		when(fileTypeDetector.detect(any())).thenReturn(FileType.TEXT);
		when(textExtractor.supports(FileType.TEXT)).thenReturn(true);
		when(textExtractor.extract(any())).thenReturn("UNIQUE_MARKER_TEXT_12345");
		mockChatClientChain(response);

		invoiceAnalyzer.analyzeInvoice(file);

		verify(requestSpec).user(org.mockito.ArgumentMatchers.contains("UNIQUE_MARKER_TEXT_12345"));
	}

	@SuppressWarnings("unchecked")
	private void mockChatClientChain(InvoiceResponse toReturn) {
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callResponseSpec);
		when(callResponseSpec.entity(InvoiceResponse.class)).thenReturn(toReturn);
	}
}
