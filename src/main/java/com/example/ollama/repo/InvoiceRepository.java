package com.example.ollama.repo;

import com.example.ollama.dto.InvoiceSearchCriteria;
import com.example.ollama.entity.Invoice;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

	default List<Invoice> findMatching(InvoiceSearchCriteria criteria) {
		return findAll(
				InvoiceSpecifications.matching(criteria),
				PageRequest.of(0, criteria.limit(), Sort.by(Sort.Direction.DESC, "uploadedDate"))
		).getContent();
	}

	/** Hard-filter matches used as the candidate pool before vector ranking (up to {@link InvoiceSearchCriteria#MAX_LIMIT}). */
	default List<Invoice> findMatchingCandidates(InvoiceSearchCriteria criteria) {
		return findAll(
				InvoiceSpecifications.matching(criteria),
				PageRequest.of(0, InvoiceSearchCriteria.MAX_LIMIT, Sort.by(Sort.Direction.DESC, "uploadedDate"))
		).getContent();
	}

	default List<Invoice> findMatchingByIds(Collection<Long> ids, InvoiceSearchCriteria criteria) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return findAll(InvoiceSpecifications.matchingByIds(ids, criteria));
	}
}
