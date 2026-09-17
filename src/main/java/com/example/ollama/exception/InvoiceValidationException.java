package com.example.ollama.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class InvoiceValidationException extends RuntimeException {

	private final List<String> errors;

	public InvoiceValidationException(List<String> errors) {
		super(errors.toString());
		this.errors = errors;
	}
}
