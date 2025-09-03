package com.bank.Bank_ChatBot.dto;
public record TransactionDto(
    java.time.LocalDateTime date,
    java.math.BigDecimal amount,
    String type,
    String description
) {}
