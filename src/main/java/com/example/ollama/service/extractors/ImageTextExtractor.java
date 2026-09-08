package com.example.ollama.service.extractors;

import com.example.ollama.domain.FileType;
import com.example.ollama.exception.InvoiceAnalyzeException;
import com.example.ollama.service.FileTypeDetector;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

/**
	Image bytes > vision model transcribes visible text > plain String
	Requires a vision-capable model
**/

@Log4j2
@Service
public class ImageTextExtractor implements TextExtractor {
	private final ChatClient chatClient;
	private final FileTypeDetector fileTypeDetector;

	private static final String transcriptionPrompt = """
			Transcribe all visible text from this invoice image exactly as it appears.
			Preserve line breaks and the layout as closely as possible.
			Do not summarize, interpret, translate, or add any information that
			is not visible in the image.
			Output only the transcribed text, nothing else.
			""";

	public ImageTextExtractor(
			@Qualifier("visionClient") ChatClient chatClient,
			FileTypeDetector fileTypeDetector) {
		this.chatClient = chatClient;
		this.fileTypeDetector = fileTypeDetector;
	}

	@Override
	public boolean supports(FileType fileType) {
		return fileType == FileType.IMAGE;
	}

	@Override
	public String extract(byte[] fileBytes) {
		MimeType mimeType = MimeType.valueOf(fileTypeDetector.detectImageMimeType(fileBytes));

		String transcription = chatClient
				.prompt()
				.user(userSpec -> userSpec
						.text(transcriptionPrompt)
						.media(mimeType, new ByteArrayResource(fileBytes)))
				.call()
				.content();

		if (transcription == null || transcription.isBlank()) {
			log.warn("Vision model returned an empty transcription for an image invoice");
			throw new InvoiceAnalyzeException("Vision model returned no text for image invoice");
		}

		log.info("Transcribed {} characters from image invoice", transcription.length());
		return transcription;
	}
}
