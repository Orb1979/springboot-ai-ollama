package com.example.ollama.service;

import com.example.ollama.dto.ChatRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;

public class ChatService {

	private final ChatClient chatClient;
	private final static String prompt = """
                You are an assistant for an accountancy office.
                Answer questions professionally.
                If you don't know the answer, say so.
                Do not invent financial or legal information.
                """;

	public ChatService(@Qualifier("conversationClient") ChatClient chatClient) {
		this.chatClient = chatClient;
	}

	public String test(){
		return chatClient
				       .prompt()
				       .user("who are you?")
				       .call()
				       .content();
	}

	public String askPromptPost(ChatRequest chatRequest){
		return chatClient
				       .prompt()
				       .system(prompt)
				       .user(chatRequest.message())
				       .call()
				       .content();
	}
}
