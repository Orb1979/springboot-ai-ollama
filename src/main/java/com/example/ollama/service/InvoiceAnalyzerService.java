package com.example.ollama.service;

import com.example.ollama.dto.InvoiceResponse;
import com.example.ollama.entity.Invoice;
import com.example.ollama.exception.InvoiceAnalyzeException;
import com.example.ollama.repo.InvoiceRepository;
import com.example.ollama.service.extractors.TextExtractor;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.example.ollama.domain.FileType;

import java.io.IOException;
import java.util.List;

@Log4j2
@Service
public class InvoiceAnalyzerService {
	private final ChatClient chatClient;
	private final FileTypeDetector fileTypeDetector;
	private final List<TextExtractor> textExtractors;
	private final InvoiceResponseValidator invoiceResponseValidator;
	private final InvoiceRepository invoiceRepository;

	private static final String invoicePrompt = """
			Extract the following information from this invoice:
			
			- supplier: the company or person issuing the invoice
			- invoiceNumber: the unique invoice identifier
			- amount: the final total amount that the customer must pay
			- currency: the currency of the final payable amount
			
			Rules:
			- Use only information present in the invoice.
			- Do not invent missing information.
			- Do not calculate values unless explicitly required.
			- Use the final total payable amount.
			- Do not use subtotal, VAT, or amount already paid.
			- If a value cannot be determined, return null.
			
			Invoice:
			%s
			""";
	private static final int MAX_ATTEMPTS = 3;
	private static final String correctionSuffix = """
    Your previous response could not be parsed as valid JSON.
    Return only valid JSON matching the schema above - no markdown code fences,
    no explanation, no text before or after the JSON object.
    """;
	private static final BeanOutputConverter<InvoiceResponse> outputConverter =
			new BeanOutputConverter<>(InvoiceResponse.class);

	public InvoiceAnalyzerService(
			@Qualifier("generalClient") ChatClient chatClient,
	    FileTypeDetector fileTypeDetector,
	    List<TextExtractor> textExtractors,
	    InvoiceResponseValidator invoiceResponseValidator,
	    InvoiceRepository invoiceRepository) {
		this.chatClient = chatClient;
		this.fileTypeDetector = fileTypeDetector;
		this.textExtractors = textExtractors;
		this.invoiceResponseValidator = invoiceResponseValidator;
		this.invoiceRepository = invoiceRepository;
	}

	public InvoiceResponse analyzeInvoice(MultipartFile file) throws IOException {
		byte[] fileBytes = file.getBytes();
		FileType fileType = fileTypeDetector.detect(fileBytes);

		TextExtractor textExtractor =	findTextExtractor(fileType);
		String invoiceText = textExtractor.extract(fileBytes);

		InvoiceResponse response = analyzeInvoiceText(invoiceText, MAX_ATTEMPTS);
		invoiceResponseValidator.validate(response);
		saveInvoice(response);
		return response;
	}

	private TextExtractor findTextExtractor(FileType fileType) {
		return textExtractors.stream().
				       filter(extractor -> extractor.supports(fileType))
				       .findFirst()
				       .orElseThrow(() -> new InvoiceAnalyzeException("No TextExtractor found for file type: " + fileType));
	}

	// simple version without retries
	private InvoiceResponse analyzeInvoiceText(String invoiceText) {
		InvoiceResponse invoiceResponse = chatClient
      .prompt()
      .user(invoicePrompt.formatted(invoiceText))
      .call()
      .entity(InvoiceResponse.class);

		if (invoiceResponse == null) {
			throw new InvoiceAnalyzeException("Failed to extract invoice information");
		}
		return invoiceResponse;
	}

	// use Spring AI BeanOutputConverter getFormat() to generate the schema, instead of chatClient.entity()
	// get the content first as a string with .content() because .entity() fails immediately on bad JSON,
	// giving us no chance to inspect or react to it
	private InvoiceResponse analyzeInvoiceText(String invoiceText, int retries) {
		String userBasePrompt = invoiceText + "\n\n" + outputConverter.getFormat();
		String userPrompt = userBasePrompt;
		Exception lastFailure = null;

		for (int attempt = 1; attempt <= retries; attempt++) {
			String rawResponse = chatClient
					                     .prompt()
					                     .system(invoicePrompt)
					                     .user(userPrompt)
					                     .call()
					                     .content();

			if (rawResponse == null || rawResponse.isBlank()) {
				lastFailure = new InvoiceAnalyzeException("Model returned an empty response");
				log.warn("Attempt {}/{}: empty response from model", attempt, retries);
				userPrompt = userBasePrompt + correctionSuffix;
				continue;
			}

			String cleaned = stripToJsonObject(rawResponse);
			try {
				return outputConverter.convert(cleaned);
			} catch (Exception e) {
				lastFailure = e;
				log.warn("Attempt {}/{}: failed to parse model response as JSON: {}", attempt, retries, e.getMessage());
			}

			userPrompt = userBasePrompt + correctionSuffix;
		}

		throw new InvoiceAnalyzeException("Model did not return valid JSON after " + retries + " attempts", lastFailure);
	}

	// Small local models frequently wrap their JSON in ```json ... ``` fences
	// or add a sentence before/after it despite instructions not to.
	// Rather than fail on that alone, take the substring between the first '{' and the last '}'
	private String stripToJsonObject(String rawResponse) {
		String trimmed = rawResponse.trim();
		int firstBrace = trimmed.indexOf('{');
		int lastBrace = trimmed.lastIndexOf('}');
		if (firstBrace >= 0 && lastBrace > firstBrace) {
			return trimmed.substring(firstBrace, lastBrace + 1);
		}
		return trimmed;
	}

	private void saveInvoice(InvoiceResponse response) {
		Invoice invoice = new Invoice(
				null,
				response.supplier(),
				response.invoiceNumber(),
				response.amount(),
				response.currency()
		);
		invoiceRepository.save(invoice);
	}
}
