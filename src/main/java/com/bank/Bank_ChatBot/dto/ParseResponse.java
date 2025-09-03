package com.bank.Bank_ChatBot.dto;

import java.util.Map;

public record ParseResponse(
    String intent,
    double confidence,
    Map<String, Object> slots,
    String follow_up,
    Map<String, Object> safety
) {}
