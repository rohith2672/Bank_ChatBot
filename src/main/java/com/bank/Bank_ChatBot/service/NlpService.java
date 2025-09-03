package com.bank.Bank_ChatBot.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.bank.Bank_ChatBot.dto.ParseResponse;

@Service
public class NlpService {

    private final RestTemplate rest;
    private final String baseUrl;

    public NlpService(RestTemplate rest,
                      @Value("${nlp.base-url:http://localhost:8000}") String baseUrl) {
        this.rest = rest;
        this.baseUrl = baseUrl;
        System.out.println("✅ NlpService ready using RestTemplate, baseUrl=" + this.baseUrl);
    }

    public ParseResponse parse(String message) {
        Map<String, String> payload = Map.of("message", message);
        ResponseEntity<ParseResponse> resp =
                rest.postForEntity(baseUrl + "/parse", payload, ParseResponse.class);
        return resp.getBody();
    }
}
