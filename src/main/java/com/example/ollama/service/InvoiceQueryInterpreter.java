package com.example.ollama.service;

import com.example.ollama.dto.InvoiceQueryInterpretation;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Turns a natural-language search query into structured invoice filter fields via an LLM.
 */
@Log4j2
@Service
public class InvoiceQueryInterpreter {

	private static final BeanOutputConverter<InvoiceQueryInterpretation> OUTPUT =
			new BeanOutputConverter<>(InvoiceQueryInterpretation.class);

	private static final String SYSTEM = """
			Extract invoice search filters from the user query.
			Return only fields you can infer confidently; otherwise null.
			- supplier: company or person name fragment
			- city: city name
			- minAmount / maxAmount: numeric bounds ("over 500" means minAmount 500)
			- currency: ISO code when stated (euro means EUR)
			- fromDate / toDate: ISO-8601 dates if present
			- paid / updated: true only if the user clearly asks for paid or updated invoices
			- semanticQuery: short text for similarity ranking (names, places, or topics),
			  without filler phrases or constraints already captured as filters
			""";

	private final ChatClient chatClient;

	public InvoiceQueryInterpreter(@Qualifier("generalClient") ChatClient chatClient) {
		this.chatClient = chatClient;
	}

	public Optional<InvoiceQueryInterpretation> interpret(String rawQuery) {
		if (rawQuery == null || rawQuery.isBlank()) {
			return Optional.empty();
		}

		try {
			String raw = chatClient.prompt()
					.system(SYSTEM)
					.user(rawQuery + "\n\n" + OUTPUT.getFormat())
					.call()
					.content();
			if (raw == null || raw.isBlank()) {
				return Optional.empty();
			}
			log.info("LLM query interpretation for '{}': {}", rawQuery, raw);
			return Optional.ofNullable(OUTPUT.convert(raw));
		} catch (RuntimeException ex) {
			log.warn("Failed to interpret search query '{}': {}", rawQuery, ex.getMessage());
			return Optional.empty();
		}
	}
}
