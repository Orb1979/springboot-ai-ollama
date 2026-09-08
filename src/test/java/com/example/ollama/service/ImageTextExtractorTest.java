package com.example.ollama.service;

import com.example.ollama.domain.FileType;
import com.example.ollama.exception.InvoiceAnalyzeException;
import com.example.ollama.service.FileTypeDetector;
import com.example.ollama.service.extractors.ImageTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ImageTextExtractorTest {

	@Mock private ChatClient chatClient;
	@Mock private ChatClient.ChatClientRequestSpec requestSpec;
	@Mock private ChatClient.CallResponseSpec callResponseSpec;
	@Mock private FileTypeDetector fileTypeDetector;

	private ImageTextExtractor imageTextExtractor;

	// Real PNG magic bytes - not a full valid image, just enough that this
	// test's intent (mime detection + prompt wiring) reads clearly.
	private static final byte[] FAKE_PNG_BYTES =
			{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		imageTextExtractor = new ImageTextExtractor(chatClient, fileTypeDetector);
	}

	@Test
	void supports_onlyImageFileType() {
		assertThat(imageTextExtractor.supports(FileType.IMAGE)).isTrue();
		assertThat(imageTextExtractor.supports(FileType.PDF)).isFalse();
		assertThat(imageTextExtractor.supports(FileType.TEXT)).isFalse();
	}

	@Test
	void extract_returnsVisionModelTranscription() {
		when(fileTypeDetector.detectImageMimeType(FAKE_PNG_BYTES)).thenReturn("image/png");
		mockChatClientChain("Acme Corp\nInvoice: INV-001\nTotal: EUR 99.90");

		String result = imageTextExtractor.extract(FAKE_PNG_BYTES);

		assertThat(result).contains("INV-001", "EUR 99.90");
	}

	@Test
	void extract_blankTranscription_throwsInvoiceAnalyzeException() {
		when(fileTypeDetector.detectImageMimeType(FAKE_PNG_BYTES)).thenReturn("image/png");
		mockChatClientChain("   ");

		assertThatThrownBy(() -> imageTextExtractor.extract(FAKE_PNG_BYTES))
				.isInstanceOf(InvoiceAnalyzeException.class)
				.hasMessageContaining("no text");
	}

	@Test
	void extract_nullTranscription_throwsInvoiceAnalyzeException() {
		when(fileTypeDetector.detectImageMimeType(FAKE_PNG_BYTES)).thenReturn("image/png");
		mockChatClientChain(null);

		assertThatThrownBy(() -> imageTextExtractor.extract(FAKE_PNG_BYTES))
				.isInstanceOf(InvoiceAnalyzeException.class);
	}

	@SuppressWarnings("unchecked")
	private void mockChatClientChain(String content) {
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.user(any(Consumer.class))).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callResponseSpec);
		when(callResponseSpec.content()).thenReturn(content);
	}
}
