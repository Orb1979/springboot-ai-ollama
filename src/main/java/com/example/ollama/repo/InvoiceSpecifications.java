package com.example.ollama.repo;

import com.example.ollama.dto.InvoiceSearchCriteria;
import com.example.ollama.entity.Invoice;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class InvoiceSpecifications {

	private InvoiceSpecifications() {}

	public static Specification<Invoice> matching(InvoiceSearchCriteria criteria) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();

			if (criteria.minAmount() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), criteria.minAmount()));
			}
			if (criteria.maxAmount() != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("amount"), criteria.maxAmount()));
			}
			if (criteria.currency() != null) {
				predicates.add(cb.equal(
						cb.lower(root.get("currency")),
						criteria.currency().toLowerCase(Locale.ROOT)
				));
			}
			if (criteria.fromDate() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("invoiceDate"), criteria.fromDate()));
			}
			if (criteria.toDate() != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("invoiceDate"), criteria.toDate()));
			}
			if (Boolean.TRUE.equals(criteria.paid())) {
				predicates.add(cb.isNotNull(root.get("paymentReceivedDate")));
			}
			if (Boolean.TRUE.equals(criteria.updated())) {
				predicates.add(cb.isNotNull(root.get("updatedDate")));
			}

			if (predicates.isEmpty()) {
				return cb.conjunction();
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	public static Specification<Invoice> matchingByIds(
			Collection<Long> ids,
			InvoiceSearchCriteria criteria) {
		if (ids == null || ids.isEmpty()) {
			return (root, query, cb) -> cb.disjunction();
		}
		return matching(criteria).and((root, query, cb) -> root.get("id").in(ids));
	}
}
