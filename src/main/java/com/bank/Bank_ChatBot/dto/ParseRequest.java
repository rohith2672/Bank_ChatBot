package com.bank.Bank_ChatBot.dto;

import java.util.Map;

public record ParseRequest(String message, String session_id, Map<String, Object> context) {}
