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
		ChatClient.ChatClientRequestSpec prompt = chatClient.prompt();
		if (conversationRequest.system() != null && !conversationRequest.system().isBlank()) {
			prompt.system(conversationRequest.system());
		}

		return prompt
				       .user(conversationRequest.question())
				       .call()
				       .entity(ConversationResponse.class);
	}
}
