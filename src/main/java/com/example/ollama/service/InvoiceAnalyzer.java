package com.example.ollama.service;

import com.example.ollama.dto.InvoiceResponse;
import com.example.ollama.entity.Invoice;
import com.example.ollama.exception.InvoiceAnalyzeException;
import com.example.ollama.repo.InvoiceRepository;
import com.example.ollama.service.extractors.TextExtractor;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.example.ollama.domain.FileType;

import java.io.IOException;
import java.util.List;

@Log4j2
@Service
public class InvoiceAnalyzer {
	private final ChatClient chatClient;
	private final FileTypeDetector fileTypeDetector;
	private final List<TextExtractor> textExtractors;
	private final InvoiceResponseValidator invoiceResponseValidator;
	private final InvoiceRepository invoiceRepository;

	private static final String invoicePrompt = """
        Extract the following information from this invoice:

        - supplier
        - invoice number
        - total amount payable
        - currency

        Rules:
        - Use only information present in the invoice.
        - Do not invent missing information.
        - Use the final total amount, not the subtotal or VAT amount.
        - Return the result in the requested structured format.

        Invoice:
        %s
        """;

	public InvoiceAnalyzer(
			ChatClient.Builder chatClientBuilder,
	    FileTypeDetector fileTypeDetector,
	    List<TextExtractor> textExtractors,
	    InvoiceResponseValidator invoiceResponseValidator,
	    InvoiceRepository invoiceRepository) {
		this.chatClient = chatClientBuilder.build();
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

		InvoiceResponse response = analyzeInvoiceText(invoiceText);
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
