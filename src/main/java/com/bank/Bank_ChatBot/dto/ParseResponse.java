package com.bank.Bank_ChatBot.dto;

import java.util.Map;

public record ParseResponse(
    String intent,
    int customerId,
    int n,
    String name,
    String follow_up
) {}
