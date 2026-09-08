package com.example.ollama.service;

import com.example.ollama.dto.ConversationRequest;
import com.example.ollama.dto.ConversationResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class ConversationService {
	private final ChatClient chatClient;
	private final static String prompt = """
                You are an assistant for an accountancy office.
                Answer questions professionally.
                If you don't know the answer, say so.
                Do not invent financial or legal information.
                """;

	public ConversationService(@Qualifier("conversationClient") ChatClient chatClient) {
		this.chatClient = chatClient;
	}

	public String test(){
		return chatClient
				       .prompt()
				       .user("who are you?")
				       .call()
				       .content();
	}

	public ConversationResponse askPromptPost(ConversationRequest conversationRequest){
		return chatClient
				       .prompt()
				       .system(prompt)
				       .user(conversationRequest.message())
				       .call()
				       .entity(ConversationResponse.class);
	}
}
