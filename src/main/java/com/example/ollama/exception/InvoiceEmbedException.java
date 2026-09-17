package com.example.ollama.exception;

public class InvoiceEmbedException extends  RuntimeException {

	public InvoiceEmbedException(String message)	{
		super(message);
	}

	public InvoiceEmbedException(String message, Throwable cause) {
		super(message, cause);
	}
}
