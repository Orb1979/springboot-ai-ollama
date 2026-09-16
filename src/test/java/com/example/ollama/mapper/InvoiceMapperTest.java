package com.example.ollama.mapper;

import com.example.ollama.dto.InvoiceResponse;
import com.example.ollama.entity.Invoice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceMapperTest {

	@Test
	void toResponse_mapsAllEntityFields() {
		Invoice invoice = new Invoice(
				42L,
				"Acme Corp",
				"Main Street",
				"42A",
				"Amsterdam",
				"1012 AB",
				"INV-001",
				LocalDate.of(2024, 3, 12),
				new BigDecimal("99.90"),
				"EUR",
				Instant.parse("2026-09-15T10:00:00Z"),
				Instant.parse("2026-09-16T11:00:00Z"),
				Instant.parse("2026-09-17T12:00:00Z")
		);

		InvoiceResponse response = InvoiceMapper.toResponse(invoice);

		assertThat(response.id()).isEqualTo(42L);
		assertThat(response.supplier()).isEqualTo("Acme Corp");
		assertThat(response.supplierStreet()).isEqualTo("Main Street");
		assertThat(response.uploadedDate()).isEqualTo(Instant.parse("2026-09-15T10:00:00Z"));
		assertThat(response.paymentReceivedDate()).isEqualTo(Instant.parse("2026-09-16T11:00:00Z"));
		assertThat(response.updatedDate()).isEqualTo(Instant.parse("2026-09-17T12:00:00Z"));
	}
}
