package com.example.ollama.controller;

import com.example.ollama.dto.InvoiceResponse;
import com.example.ollama.exception.InvoiceAnalyzeException;
import com.example.ollama.service.InvoiceAnalyzer;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Log4j2
@RestController
@RequestMapping("/ai/invoices")
@RequiredArgsConstructor
public class InvoiceController {
	private final InvoiceAnalyzer invoiceAnalyzeService;

	// curl -X POST http://localhost:8080/ai/invoices/analyze -F "file=@example-invoice.txt"
	// curl -X POST http://localhost:8080/ai/invoices/analyze -F "file=@example-invoice.pdf"
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

//	// curl -X POST http://localhost:8080/ai/invoices/analyze-pdf -F "file=@example-invoice.pdf"
//	@PostMapping("/analyze-pdf")
//	public InvoiceResponse analyzePdf(@RequestParam("file") MultipartFile file)  {
//		log.info("Analyzing invoice (pdf): {}", file.getOriginalFilename());
//		try {
//			return invoiceAnalyzeService.analyzeInvoice(file);
//		} catch (IOException e) {
//			throw new InvoiceAnalyzeException(
//					"Failed to analyze pdf invoice, for file %s".formatted(file.getOriginalFilename()), e);
//		}
//	}
}


