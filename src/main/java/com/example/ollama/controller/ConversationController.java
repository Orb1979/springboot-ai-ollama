package com.example.ollama.controller;

import com.example.ollama.dto.ConversationRequest;
import com.example.ollama.dto.ConversationResponse;

import com.example.ollama.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.*;

@Log4j2
@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class ConversationController {
    private final ConversationService conversationService;

    @GetMapping("/test")
    public String ask() {
        return conversationService.test();
    }

    @PostMapping()
    public ConversationResponse askPost(@RequestBody ConversationRequest conversationRequest) {
        return conversationService.askPromptPost(conversationRequest);
    }
}
