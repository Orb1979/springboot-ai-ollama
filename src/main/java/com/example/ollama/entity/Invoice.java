package com.example.ollama.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "invoice")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Invoice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String supplier;

	@Column(name = "supplier_street", nullable = false)
	private String supplierStreet;

	@Column(name = "supplier_street_number", nullable = false, length = 50)
	private String supplierStreetNumber;

	@Column(name = "supplier_city", nullable = false)
	private String supplierCity;

	@Column(name = "supplier_postal_code", nullable = false, length = 50)
	private String supplierPostalCode;

	@Column(nullable = false)
	private String invoiceNumber;

	@Column(name = "invoice_date", nullable = false)
	private LocalDate invoiceDate;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false)
	private String currency;

	@Column(name = "uploaded_date", nullable = false)
	private Instant uploadedDate;

	@Column(name = "payment_received_date")
	private LocalDate paymentReceivedDate;

	@Column(name = "updated_date")
	private Instant updatedDate;

}
