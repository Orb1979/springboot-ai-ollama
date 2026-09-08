package com.example.ollama.config;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OllamaModelConfig {

    @Bean("generalModel")
    public OllamaChatModel generalModel(
            OllamaApi api,
            @Value("${app.ai.ollama.general-model}") String modelName) {

        return OllamaChatModel.builder()
                .ollamaApi(api)
                .options(OllamaChatOptions.builder().model(modelName).build())
                .build();
    }

    @Bean("conversationModel")
    public OllamaChatModel conversationModel(
        OllamaApi api,
        @Value("${app.ai.ollama.conversation-model}") String modelName) {

        return OllamaChatModel.builder()
                   .ollamaApi(api)
                   .options(OllamaChatOptions.builder().model(modelName).build())
                   .build();
    }

    @Bean("visionModel")
    public OllamaChatModel visionModel(
            OllamaApi api,
            @Value("${app.ai.ollama.vision-model}") String modelName) {

        return OllamaChatModel.builder()
                .ollamaApi(api)
                .options(OllamaChatOptions.builder().model(modelName).build())
                .build();
    }
}