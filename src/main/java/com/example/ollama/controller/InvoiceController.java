package com.example.ollama.controller;

import com.example.ollama.dto.InvoiceResponse;
import com.example.ollama.exception.InvoiceAnalyzeException;
import com.example.ollama.service.InvoiceAnalyzerService;

import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/ai/invoices")
@RequiredArgsConstructor
public class InvoiceController {
	private final InvoiceAnalyzerService invoiceAnalyzeService;

	@PostMapping("/analyze")
	public InvoiceResponse analyzeText(@RequestParam("file") MultipartFile file)  {
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
		return invoiceAnalyzeService.updateInvoice(id, invoiceResponse);
	}
}
