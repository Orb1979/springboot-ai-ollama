package com.example.ollama.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

@Configuration
public class ChatClientConfig {

	/*
		create OpenAIClient bean if a spring.ai.openai API key is configured
  	This is not needed for OllamaApi because its already created as a bean by Spring AI's
  	auto-configuration because its part of org.springframework.ai)
	*/
	@Bean
	@ConditionalOnProperty(prefix = "spring.ai.openai", name = "api-key")
	public OpenAIClient openAIClient(@Value("${spring.ai.openai.api-key}") String apiKey) {
		if (apiKey == null || apiKey.isBlank()) {
			return null;
		}
		return OpenAIOkHttpClient.builder()
				       .apiKey(apiKey)
				       .build();
	}

	/*
		different ChatClients beans, will need @Qualifier("<name>")
		Note that we wrap OpenAIClient in a provider because it can be missing
	*/

	@Bean("generalClient")
	public ChatClient generalClient(
			@Value("${app.ai.general.provider}") String provider,
			@Value("${app.ai.general.model}") String modelName,
			OllamaApi ollamaApi,
			ObjectProvider<OpenAIClient> openAiClientProvider) {
		return buildTaskClient(provider, modelName, ollamaApi, openAiClientProvider);
	}

	@Bean("conversationClient")
	public ChatClient conversationClient(
			@Value("${app.ai.conversation.provider}") String provider,
			@Value("${app.ai.conversation.model}") String modelName,
			OllamaApi ollamaApi,
			ObjectProvider<OpenAIClient> openAiClientProvider) {
		return buildTaskClient(provider, modelName, ollamaApi, openAiClientProvider);
	}

	@Bean("visionClient")
	public ChatClient visionClient(
			@Value("${app.ai.vision.provider}") String provider,
			@Value("${app.ai.vision.model}") String modelName,
			OllamaApi ollamaApi,
			ObjectProvider<OpenAIClient> openAiClientProvider) {
		return buildTaskClient(provider, modelName, ollamaApi, openAiClientProvider);
	}

	/*
		Create the ChatClient with the specified provider and model name
	*/
	private ChatClient buildTaskClient(
			String provider,
			String modelName,
			OllamaApi ollamaApi,
			ObjectProvider<OpenAIClient> openAiClientProvider) {

		String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);

		switch (normalizedProvider) {
			case "ollama" -> {
				OllamaChatModel model =
						OllamaChatModel.builder()
								.ollamaApi(ollamaApi)
								.options(OllamaChatOptions.builder().model(modelName).build())
								.build();
				return ChatClient.builder(model).build();
			}
			case "openai" -> {
				OpenAIClient openAiClient = openAiClientProvider.getIfAvailable();
				if (openAiClient == null) {
					throw new IllegalStateException(
							"OpenAI provider selected but no OpenAI client bean is configured. " +
									"Set spring.ai.openai.api-key and ensure the OpenAI starter is active.");
				}
				OpenAiChatModel model =
						OpenAiChatModel.builder()
								.openAiClient(openAiClient)
								.options(OpenAiChatOptions.builder().model(modelName).build())
								.build();
				return ChatClient.builder(model).build();
			}
			default -> throw new IllegalArgumentException(
					"Unsupported AI provider for task: '" + provider + "'. Use 'ollama' or 'openai'.");
		}
	}
}