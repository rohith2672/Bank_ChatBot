package com.bank.Bank_ChatBot;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/health")   // was "/"
    public String health() {
        return "OK";
    }
}
