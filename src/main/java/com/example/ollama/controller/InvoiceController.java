package com.example.ollama.controller;

import com.example.ollama.dto.InvoiceResponse;
import com.example.ollama.dto.InvoiceSearchCriteria;
import com.example.ollama.dto.InvoiceUpdateRequest;
import com.example.ollama.exception.InvoiceAnalyzeException;
import com.example.ollama.mapper.InvoiceMapper;
import com.example.ollama.service.InvoiceAnalyzerService;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/ai/invoices")
@RequiredArgsConstructor
public class InvoiceController {
	private final InvoiceAnalyzerService invoiceAnalyzeService;

	@PostMapping("/analyze")
	public InvoiceResponse analyzeInvoice(@RequestParam("file") MultipartFile file)  {
		try {
			return InvoiceMapper.toResponse(invoiceAnalyzeService.analyzeInvoice(file));
		} catch (IOException e) {
			throw new InvoiceAnalyzeException(
					"Failed to analyze text invoice, for file %s".formatted(file.getOriginalFilename()), e);
		}
	}

	@GetMapping
	public List<InvoiceResponse> list() {
		return invoiceAnalyzeService.listInvoices().stream()
				.map(InvoiceMapper::toResponse)
				.toList();
	}

	@GetMapping("/search")
	public List<InvoiceResponse> search(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) BigDecimal minAmount,
			@RequestParam(required = false) BigDecimal maxAmount,
			@RequestParam(required = false) String currency,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
			@RequestParam(required = false, defaultValue = "20") int limit) {
		return invoiceAnalyzeService.searchInvoices(
						new InvoiceSearchCriteria(q, minAmount, maxAmount, currency, fromDate, toDate, limit))
				.stream()
				.map(InvoiceMapper::toResponse)
				.toList();
	}

	@PutMapping("/{id}")
	public InvoiceResponse update(@PathVariable Long id, @RequestBody InvoiceUpdateRequest updateRequest) {
		return InvoiceMapper.toResponse(invoiceAnalyzeService.updateInvoice(id, updateRequest));
	}
}
