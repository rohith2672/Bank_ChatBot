package com.bank.Bank_ChatBot.controllers;

import com.bank.Bank_ChatBot.dto.ParseResponse;
import com.bank.Bank_ChatBot.service.BankingService;
import com.bank.Bank_ChatBot.service.BankingService.LoanStatus;
import com.bank.Bank_ChatBot.service.NlpService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final NlpService nlp;
    private final BankingService banking;

    public ChatController(NlpService nlp, BankingService banking) {
        this.nlp = nlp;
        this.banking = banking;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> chat(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        if (message.isBlank()) {
            return Mono.just(Map.of("error", "message is required"));
        }

        try {
            // nlp.parse(...) returns ParseResponse (synchronous)
            ParseResponse parsed = nlp.parse(message);
            Map<String, Object> out = route(parsed);
            return Mono.just(out);
        } catch (Exception ex) {
            ex.printStackTrace();
            return Mono.just(Map.of(
                    "reply", "Sorry, I’m having trouble right now.",
                    "error", ex.getClass().getSimpleName(),
                    "message", String.valueOf(ex.getMessage())
            ));
        }
    }

    // ---------- routing ----------

    private Map<String, Object> route(ParseResponse p) {
        String intent = p.intent() == null ? "UNKNOWN" : p.intent();
        @SuppressWarnings("unchecked")
        Map<String, Object> slots = p.slots() == null ? Map.of() : p.slots();

        switch (intent) {
            case "GET_BALANCE":
                return handleGetBalance(slots);
            case "GET_TRANSACTIONS":
                return handleGetTransactions(slots);
            case "GET_LOAN_STATUS":
                return handleGetLoanStatus(slots);
            case "GET_LOANS":
                return handleGetLoans(slots);
            default:
                String follow = p.follow_up() != null
                        ? p.follow_up()
                        : "I didn’t get that. Try: 'balance for customer 12345678' or 'last 5 transactions for John Doe'.";
                return Map.of("reply", follow);
        }
    }

    // ---------- handlers ----------

    private Map<String, Object> handleGetBalance(Map<String, Object> slots) {
        Integer id = resolveCustomerId(slots);
        if (id == null) return Map.of("reply", "Whose account? Provide customer ID or name.");

        Optional<BigDecimal> bal = banking.getLatestBalance(id);
        if (bal.isEmpty()) return Map.of("reply", "No account found for that customer.");

        return Map.of("intent", "GET_BALANCE", "balance", bal.get());
    }

    private Map<String, Object> handleGetTransactions(Map<String, Object> slots) {
        Integer id = resolveCustomerId(slots);
        if (id == null) return Map.of("reply", "Whose account? Provide customer ID or name.");

        int n = parseN(slots, 5);
        List<Map<String, Object>> rows = banking.getTransactionsByCustomerId(id, n);
        return Map.of("intent", "GET_TRANSACTIONS", "count", rows.size(), "transactions", rows);
    }

    private Map<String, Object> handleGetLoanStatus(Map<String, Object> slots) {
        Integer id = resolveCustomerId(slots);
        if (id == null) return Map.of("reply", "Whose loan? Provide customer ID or name.");

        Optional<LoanStatus> s = banking.getLatestLoanStatus(id);
        if (s.isEmpty()) return Map.of("reply", "No loans found for that customer.");

        LoanStatus st = s.get();
        return Map.of(
                "intent", "GET_LOAN_STATUS",
                "loan", Map.of("loanId", st.getLoanId(), "status", norm(st.getStatus()), "amount", st.getAmount())
        );
    }

    private Map<String, Object> handleGetLoans(Map<String, Object> slots) {
    Integer id = resolveCustomerId(slots);
    if (id == null) return Map.of("reply", "Whose loans? Provide customer ID or name.");

    List<LoanStatus> list = banking.getLoansByCustomerId(id);

    List<Map<String, Object>> normalized = list.stream()
            .map(l -> Map.<String, Object>of(
                    "loanId", l.getLoanId(),
                    "status", norm(l.getStatus()),
                    "amount", l.getAmount()
            ))
            .collect(java.util.stream.Collectors.toList()); // ✅ Fix

    return Map.of("intent", "GET_LOANS", "count", normalized.size(), "loans", normalized);
}


    // ---------- helpers ----------

    private Integer resolveCustomerId(Map<String, Object> slots) {
        Integer id = safeInt(slots.get("customer_id"));
        if (id != null) return id;

        String name = clean(String.valueOf(slots.get("name")));
        if (name != null) {
            return banking.findCustomerIdByName(name).orElse(null);
        }
        return null;
    }

    private static int parseN(Map<String, Object> slots, int fallback) {
        Integer n = safeInt(slots.get("n"));
        if (n == null) return fallback;
        return Math.max(1, Math.min(n, 50));
    }

    private static Integer safeInt(Object o) {
        if (o == null) return null;
        try {
            String s = String.valueOf(o).trim();
            if (s.isBlank()) return null;
            return Integer.valueOf(s);
        } catch (Exception e) { return null; }
    }

    private static String clean(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isBlank() ? null : s;
    }

    private static String norm(String s) {
        return (s == null || s.isBlank()) ? "UNKNOWN" : s;
    }
}
