package com.example.ollama.controller;

import com.example.ollama.dto.InvoiceResponse;
import com.example.ollama.exception.InvoiceAnalyzeException;
import com.example.ollama.service.InvoiceAnalyzerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Log4j2
@RestController
@RequestMapping("/ai/invoices")
@RequiredArgsConstructor
public class InvoiceController {
	private final InvoiceAnalyzerService invoiceAnalyzeService;

	// curl -X POST http://localhost:8080/ai/invoices/analyze -F "file=@example-invoice.txt"
	// curl -X POST http://localhost:8080/ai/invoices/analyze -F "file=@example-invoice.pdf"
	// curl -X POST http://localhost:8080/ai/invoices/analyze -F "file=@example-invoice.png"
	@PostMapping("/analyze")
	public InvoiceResponse analyzeText(@RequestParam("file") MultipartFile file)  {
		log.info("Analyzing invoice: {}", file.getOriginalFilename());
		try {
			return invoiceAnalyzeService.analyzeInvoice(file);
		} catch (IOException e) {
			throw new InvoiceAnalyzeException(
					"Failed to analyze text invoice, for file %s".formatted(file.getOriginalFilename()), e);
		}
	}

	@GetMapping
	public List<InvoiceResponse> list() {
		return invoiceAnalyzeService.listInvoices();
	}

	@PutMapping("/{id}")
	public InvoiceResponse update(
			@PathVariable Long id,
			@RequestBody InvoiceResponse invoiceResponse) {
		log.info("Updating invoice {}", id);
		return invoiceAnalyzeService.updateInvoice(id, invoiceResponse);
	}
}
