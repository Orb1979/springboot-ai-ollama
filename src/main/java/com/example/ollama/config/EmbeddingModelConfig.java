package com.example.ollama.config;

import com.openai.client.OpenAIClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.document.MetadataMode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

@Configuration
public class EmbeddingModelConfig {

	@Bean
	public EmbeddingModel embeddingModel(
			@Value("${app.ai.embedding.provider}") String provider,
			@Value("${app.ai.embedding.model}") String modelName,
			OllamaApi ollamaApi,
			ObjectProvider<OpenAIClient> openAiClientProvider) {

		String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);

		return switch (normalizedProvider) {
			case "ollama" -> OllamaEmbeddingModel.builder()
					.ollamaApi(ollamaApi)
					.options(OllamaEmbeddingOptions.builder().model(modelName).build())
					.build();
			case "openai" -> {
				OpenAIClient openAiClient = openAiClientProvider.getIfAvailable();
				if (openAiClient == null) {
					throw new IllegalStateException(
							"OpenAI embedding provider selected but no OpenAI client bean is configured. " +
									"Set spring.ai.openai.api-key and ensure the OpenAI starter is active.");
				}
				yield OpenAiEmbeddingModel.builder()
						.openAiClient(openAiClient)
						.metadataMode(MetadataMode.EMBED)
						.options(OpenAiEmbeddingOptions.builder().model(modelName).build())
						.build();
			}
			default -> throw new IllegalArgumentException(
					"Unsupported AI provider for embeddings: '" + provider + "'. Use 'ollama' or 'openai'.");
		};
	}
}
