package com.example.ollama.dto;

import com.example.ollama.entity.Invoice;

public record InvoiceSearchHit(Invoice invoice, Double similarityScore) {
}
