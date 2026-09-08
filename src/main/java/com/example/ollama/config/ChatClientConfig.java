package com.example.ollama.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

	@Bean("generalClient")
	public ChatClient generalClient(@Qualifier("generalModel") OllamaChatModel model) {
		return ChatClient.builder(model).build();
	}

	@Bean("conversationClient")
	public ChatClient conversationClient(@Qualifier("conversationModel") OllamaChatModel model) {
		return ChatClient.builder(model).build();
	}

	@Bean("visionClient")
	public ChatClient visionClient(@Qualifier("visionModel") OllamaChatModel model) {
		return ChatClient.builder(model).build();
	}

}
