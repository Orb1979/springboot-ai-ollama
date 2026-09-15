package com.example.ollama.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class InvoiceNotFoundException extends RuntimeException {

	public InvoiceNotFoundException(Long id) {
		super("Invoice not found: " + id);
	}
}
