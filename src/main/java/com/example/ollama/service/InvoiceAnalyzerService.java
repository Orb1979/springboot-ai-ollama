package com.example.ollama.service;

import com.example.ollama.dto.InvoiceExtractionResponse;
import com.example.ollama.dto.InvoiceSearchCriteria;
import com.example.ollama.dto.InvoiceSearchHit;
import com.example.ollama.dto.InvoiceUpdateRequest;
import com.example.ollama.domain.FileType;
import com.example.ollama.entity.Invoice;
import com.example.ollama.exception.InvoiceAnalyzeException;
import com.example.ollama.exception.InvoiceEmbedException;
import com.example.ollama.exception.InvoiceNotFoundException;
import com.example.ollama.repo.InvoiceRepository;
import com.example.ollama.service.extractors.TextExtractor;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Log4j2
@Service
public class InvoiceAnalyzerService {
	private final ChatClient chatClient;
	private final FileTypeDetector fileTypeDetector;
	private final List<TextExtractor> textExtractors;
	private final InvoiceValidator invoiceValidator;
	private final InvoiceRepository invoiceRepository;
	private final InvoiceEmbeddingService invoiceEmbeddingService;

	private static final String invoicePrompt = """
			Extract the following information from this invoice:
			
			- supplier: the company or person issuing the invoice
			- supplierStreet: the supplier street name without the house or building number
			- supplierStreetNumber: the supplier house or building number, including any suffix
			- supplierPostalCode: the supplier postal or ZIP code
			- supplierCity: the supplier city
			- supplierCountry: the supplier country, if there is no supplier country in the invoice, try to infer the country
			  from the supplierCity, supplierPostalCode or state reference
			- invoiceNumber: the unique invoice identifier
			- invoiceDate: the invoice issue date in ISO-8601 format (YYYY-MM-DD)
			- amount: the final total amount that the customer must pay
			- currency: the currency of the final payable amount
			
			Rules:
			- Use only information present in the invoice.
			- Do not invent missing information.
			- Do not calculate values unless explicitly required.
			- Use the final total payable amount.
			- Do not use subtotal, VAT, or amount already paid.
			- If a value cannot be determined, return null.
			""";
	private static final int MAX_ATTEMPTS = 3;
	private static final String correctionSuffix = """
    Your previous response could not be parsed as valid JSON.
    Return only valid JSON matching the schema above - no markdown code fences,
    no explanation, no text before or after the JSON object.
    """;
	private static final BeanOutputConverter<InvoiceExtractionResponse> outputConverter =
			new BeanOutputConverter<>(InvoiceExtractionResponse.class);

	public InvoiceAnalyzerService(
			@Qualifier("generalClient") ChatClient chatClient,
	    FileTypeDetector fileTypeDetector,
	    List<TextExtractor> textExtractors,
	    InvoiceValidator invoiceValidator,
	    InvoiceRepository invoiceRepository,
	    InvoiceEmbeddingService invoiceEmbeddingService) {
		this.chatClient = chatClient;
		this.fileTypeDetector = fileTypeDetector;
		this.textExtractors = textExtractors;
		this.invoiceValidator = invoiceValidator;
		this.invoiceRepository = invoiceRepository;
		this.invoiceEmbeddingService = invoiceEmbeddingService;
	}

	public Invoice analyzeInvoice(MultipartFile file) throws IOException {
		Instant uploadedDate = Instant.now();
		byte[] fileBytes = file.getBytes();
		FileType fileType = fileTypeDetector.detect(fileBytes);

		TextExtractor textExtractor =	findTextExtractor(fileType);
		String invoiceText = textExtractor.extract(fileBytes);

		InvoiceExtractionResponse extraction = analyzeInvoiceText(invoiceText, MAX_ATTEMPTS);
		Invoice invoice = toInvoice(extraction, uploadedDate);
		invoiceValidator.validate(invoice);
		Invoice saved = invoiceRepository.save(invoice);
		try {
			invoiceEmbeddingService.indexInvoice(saved);
		} catch (RuntimeException ex) {
			invoiceRepository.deleteById(saved.getId());
			invoiceEmbeddingService.removeInvoice(saved.getId());
			throw new InvoiceEmbedException("Failed to index invoice", ex);
		}
		return saved;
	}

	public List<Invoice> listInvoices() {
		return invoiceRepository.findAll();
	}

	public List<InvoiceSearchHit> searchInvoices(InvoiceSearchCriteria criteria) {
		return invoiceEmbeddingService.search(criteria);
	}

	public Invoice updateInvoice(Long id, InvoiceUpdateRequest update) {
		Invoice invoice = invoiceRepository.findById(id)
				.orElseThrow(() -> new InvoiceNotFoundException(id));

		invoice.setSupplier(update.supplier());
		invoice.setSupplierStreet(update.supplierStreet());
		invoice.setSupplierStreetNumber(update.supplierStreetNumber());
		invoice.setSupplierCity(update.supplierCity());
		invoice.setSupplierPostalCode(update.supplierPostalCode());
		invoice.setInvoiceNumber(update.invoiceNumber());
		invoice.setInvoiceDate(update.invoiceDate());
		invoice.setAmount(update.amount());
		invoice.setCurrency(update.currency());
		invoice.setPaymentReceivedDate(update.paymentReceivedDate());
		invoiceValidator.validate(invoice);
		invoice.setUpdatedDate(Instant.now());

		Invoice saved = invoiceRepository.save(invoice);
		invoiceEmbeddingService.indexInvoice(saved);
		return saved;
	}

	private TextExtractor findTextExtractor(FileType fileType) {
		return textExtractors.stream().
				       filter(extractor -> extractor.supports(fileType))
				       .findFirst()
				       .orElseThrow(() -> new InvoiceAnalyzeException("No TextExtractor found for file type: " + fileType));
	}

	// simple version without retries
	private InvoiceExtractionResponse analyzeInvoiceText(String invoiceText) {
		InvoiceExtractionResponse invoiceResponse =
				chatClient
          .prompt()
          .user(invoicePrompt.formatted(invoiceText))
          .call()
          .entity(InvoiceExtractionResponse.class);

		if (invoiceResponse == null) {
			throw new InvoiceAnalyzeException("Failed to extract invoice information");
		}
		return invoiceResponse;
	}

	// use Spring AI BeanOutputConverter getFormat() to generate the schema, instead of chatClient.entity()
	// get the content first as a string with .content() because .entity() fails immediately on bad JSON,
	// giving us no chance to inspect or react to it
	private InvoiceExtractionResponse analyzeInvoiceText(String invoiceText, int retries) {
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

	private Invoice toInvoice(InvoiceExtractionResponse extraction, Instant uploadedDate) {
		return new Invoice(
				null,
				extraction.supplier(),
				extraction.supplierStreet(),
				extraction.supplierStreetNumber(),
				extraction.supplierPostalCode(),
				extraction.supplierCity(),
				extraction.supplierCountry(),
				extraction.invoiceNumber(),
				extraction.invoiceDate(),
				extraction.amount(),
				extraction.currency(),
				uploadedDate,
				null,
				null
		);
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
}
