package com.example.ollama.exception;

public class InvoiceAnalyzeException extends RuntimeException {

	public InvoiceAnalyzeException(String message) {
		super(message);
	}

	public InvoiceAnalyzeException(String message, Throwable cause) {
		super(message, cause);
	}
}
