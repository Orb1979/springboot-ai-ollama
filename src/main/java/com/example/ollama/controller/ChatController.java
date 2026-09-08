package com.example.ollama.controller;

import com.example.ollama.dto.ChatRequest;
import com.example.ollama.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.*;

@Log4j2
@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    // curl http://localhost:8080/ai/chat/test
    @GetMapping("/test")
    public String ask() {
        return chatService.test();
    }

    // curl -X POST 'http://localhost:8080/ai/chat' -d '{"message": "Explain what an invoice is"}'
    @PostMapping()
    public String askPost(@RequestBody ChatRequest chatRequest) {
        return chatService.askPromptPost(chatRequest);
    }
}
