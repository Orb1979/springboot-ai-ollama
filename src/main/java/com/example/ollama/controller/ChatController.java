package com.example.ollama.controller;

import com.example.ollama.dto.ChatRequest;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@Log4j2
@RestController
public class ChatController {
    private final ChatClient chatClient;
    private final static String prompt = """
                You are an assistant for an accountancy office.
                Answer questions professionally.
                If you don't know the answer, say so.
                Do not invent financial or legal information.
                """;

    public ChatController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    // curl http://localhost:8080/test
    @GetMapping("/ai/test")
    public String ask() {
        return chatClient
                .prompt()
                .user("who are you?")
                .call()
                .content();
    }

    // curl 'http://localhost:8080/ai/chat?message=what%20is%20a%20invoice'
    @GetMapping("/ai/chat")
    public String askPrompt(@RequestParam String message) {
        return chatClient
                   .prompt()
                   .system(prompt)
                   .user(message)
                   .call()
                   .content();
    }

    // curl -X POST 'http://localhost:8080/ai/chat' -d '{"message": "Explain what an invoice is"}'
    @PostMapping("/ai/chat")
    public String askPost(@RequestBody ChatRequest chatRequest) {
        return chatClient
                .prompt()
                .system(prompt)
                .user(chatRequest.message())
                .call()
                .content();
    }
}
