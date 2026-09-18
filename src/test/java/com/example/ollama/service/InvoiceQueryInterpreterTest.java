package com.example.ollama.service;

import com.example.ollama.dto.InvoiceQueryInterpretation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceQueryInterpreterTest {

	@Mock
	private ChatClient chatClient;
	@Mock
	private ChatClient.ChatClientRequestSpec requestSpec;
	@Mock
	private ChatClient.CallResponseSpec callResponseSpec;

	private InvoiceQueryInterpreter interpreter;

	@BeforeEach
	void setUp() {
		interpreter = new InvoiceQueryInterpreter(chatClient);
	}

	@Test
	void interpret_returnsEmpty_onBlankInput() {
		assertThat(interpreter.interpret("   ")).isEmpty();

		verify(chatClient, never()).prompt();
	}

	@Test
	void interpret_returnsEmpty_onChatFailure() {
		when(chatClient.prompt()).thenThrow(new RuntimeException("boom"));

		assertThat(interpreter.interpret("invoices from Acme")).isEmpty();
	}

	@Test
	void interpret_returnsEmpty_onMalformedResponse() {
		mockChatClientChain("not JSON");

		assertThat(interpreter.interpret("invoices from Acme")).isEmpty();
	}

	@Test
	void interpret_returnsParsedFilters_onValidResponse() {
		mockChatClientChain("""
				{
				  "supplier": "Acme",
				  "city": "Amsterdam",
				  "minAmount": 500,
				  "maxAmount": null,
				  "currency": "EUR",
				  "fromDate": null,
				  "toDate": null,
				  "paid": null,
				  "updated": null,
				  "semanticQuery": "Acme Amsterdam"
				}
				""");

		var result = interpreter.interpret("invoices from Acme in Amsterdam over 500 euro");

		assertThat(result).isPresent();
		InvoiceQueryInterpretation interpretation = result.orElseThrow();
		assertThat(interpretation.supplier()).isEqualTo("Acme");
		assertThat(interpretation.city()).isEqualTo("Amsterdam");
		assertThat(interpretation.minAmount()).isEqualByComparingTo(new BigDecimal("500"));
		verify(requestSpec).system(anyString());
		verify(requestSpec).user(anyString());
	}

	private void mockChatClientChain(String content) {
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.system(anyString())).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callResponseSpec);
		when(callResponseSpec.content()).thenReturn(content);
	}
}
