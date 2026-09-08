package com.example.ollama.service;

import com.example.ollama.dto.ChatRequest;
import com.example.ollama.dto.InvoiceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {
	@Mock	private ChatClient chatClient;
	@Mock private ChatClient.ChatClientRequestSpec requestSpec;
	@Mock private ChatClient.CallResponseSpec callResponseSpec;
	private ChatService chatService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		chatService = new ChatService(chatClient);
	}

	@Test
	public void test(){
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callResponseSpec);
		when(callResponseSpec.content()).thenReturn("AI response");

		String response = chatService.test();

		assertEquals("AI response", response);
		verify(requestSpec).user(anyString());
	}

	@Test
	public void ask(){
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.system(anyString())).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callResponseSpec);
		when(callResponseSpec.content()).thenReturn("AI response");

		String response = chatService.askPromptPost(new ChatRequest("who are you?"));

		assertEquals("AI response", response);
		verify(requestSpec).user(anyString());
		verify(requestSpec).system(anyString());
	}
}