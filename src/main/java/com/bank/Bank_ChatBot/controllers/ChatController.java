package com.bank.Bank_ChatBot.controllers;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // quick health check: http://localhost:8080/api/chat (GET)
    @GetMapping("/chat")
    public Map<String, String> ping() {
        return Map.of("reply", "chat endpoint is alive");
    }

    // UI posts here
    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> body) {
        String message = body == null ? "" : body.getOrDefault("message", "");
        String reply = chatService.getResponse(message);
        return Map.of("reply", reply);
    }
}
