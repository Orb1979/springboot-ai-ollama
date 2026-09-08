package com.example.ollama.service;

import com.example.ollama.dto.ConversationRequest;
import com.example.ollama.dto.ConversationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {
	@Mock	private ChatClient chatClient;
	@Mock private ChatClient.ChatClientRequestSpec requestSpec;
	@Mock private ChatClient.CallResponseSpec callResponseSpec;
	private ConversationService conversationService;

	@BeforeEach
	void setUp() {
		conversationService = new ConversationService(chatClient);
	}

	@Test
	public void test(){
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callResponseSpec);
		when(callResponseSpec.content()).thenReturn("AI response");

		String response = conversationService.test();

		assertEquals("AI response", response);
		verify(requestSpec).user(anyString());
	}

	@Test
	public void ask(){
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.system(anyString())).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callResponseSpec);
		when(callResponseSpec.entity(ConversationResponse.class)).thenReturn(new ConversationResponse("AI response"));

		ConversationResponse response = conversationService.askPromptPost(new ConversationRequest("who are you?"));

		assertEquals("AI response", response.message());
		verify(requestSpec).user(anyString());
		verify(requestSpec).system(anyString());
	}
}