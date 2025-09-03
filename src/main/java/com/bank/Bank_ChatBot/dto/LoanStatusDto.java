package com.bank.Bank_ChatBot.dto;

import java.math.BigDecimal;

public record LoanStatusDto(
    Integer loanId,
    String status,
    BigDecimal amount
) {}
