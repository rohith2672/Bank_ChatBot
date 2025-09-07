package com.bank.Bank_ChatBot.controllers;

import com.bank.Bank_ChatBot.dto.ParseResponse;
import com.bank.Bank_ChatBot.service.BankingService;
import com.bank.Bank_ChatBot.service.BankingService.LoanStatus;
import com.bank.Bank_ChatBot.service.NlpService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final NlpService nlp;
    private final BankingService banking;

    public ChatController(NlpService nlp, BankingService banking) {
        this.nlp = nlp;
        this.banking = banking;
    }

    // ----------------------------- HTTP entry ------------------------------

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> chat(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        if (message == null || message.trim().isEmpty()) {
            return Mono.just(jsonReply(
                    "Your request is missing the 'message' field.",
                    Map.of("error", "ValidationError")
            ));
        }

        try {
            String msg = message.trim();

            // 1) Primary path: delegate to NLP (synchronous in your service)
            ParseResponse parsed = nlp.parse(msg);

            // 2) Route normally
            Map<String, Object> out = route(parsed);

            // 3) If NLP says UNKNOWN, try a lightweight heuristic on the raw text
            if ("UNKNOWN".equalsIgnoreCase(parsed.intent())) {
                Map<String, Object> heuristic = tryHeuristic(msg);
                if (heuristic != null) return Mono.just(heuristic);
            }

            return Mono.just(out);

        } catch (Exception ex) {
            ex.printStackTrace();
            return Mono.just(jsonReply(
                    "Sorry, I’m having trouble right now.",
                    Map.of("error", ex.getClass().getSimpleName())
            ));
        }
    }

    // ----------------------------- Router ------------------------------

    private Map<String, Object> route(ParseResponse p) {
        String intent = p.intent() == null ? "UNKNOWN" : p.intent();
        Map<String, Object> slots = Map.of(
            "customer_id", p.customerId(),
            "n", p.n(),
            "name", p.name() != null ? p.name() : ""
        );

        return switch (intent) {
            case "GET_BALANCE_BY_ID", "GET_BALANCE_FOR_CUSTOMER" -> handleGetBalance(slots);
            case "GET_ACCOUNTS_BY_ID" -> handleGetAccounts(slots);
            case "LAST_N_TRANSACTIONS" -> handleGetTransactions(slots);
            case "LOAN_STATUS" -> handleGetLoanStatus(slots);
            case "LIST_LOANS" -> handleGetLoans(slots);
            default -> {
                String follow = p.follow_up() != null && !p.follow_up().isBlank()
                        ? p.follow_up()
                        : "I didn't get that. Try: 'balance for customer 101' or 'last 5 transactions for John Doe'.";
                yield jsonReply(follow, Map.of("intent", "UNKNOWN"));
            }
        };
    }

    // ----------------------------- Heuristic fallback ------------------------------
    // Catches common phrasings when NLP misses:
    //  - "balance for customer id 3" / "balance for id 3" / "balance for customer 3"
    //  - "balance for <name>"
    //  - "last 5 transactions for id 3"
    //  - "loan status for id 3"
    //  - "list loans for id 3"
    private static final Pattern RE_BALANCE_BY_ID = Pattern.compile(
            "(?i)\\bbalance\\b.*?\\b(?:customer\\s*id|id|customer)\\s*(\\d+)\\b");
    private static final Pattern RE_BALANCE_BY_NAME = Pattern.compile(
            "(?i)\\bbalance\\b.*?\\bfor\\s+([a-z][a-z '\\-]{1,80})\\s*$");
    private static final Pattern RE_TX_LASTN = Pattern.compile(
            "(?i)\\b(?:last|recent)\\s+(\\d{1,2})\\s+(?:tx|transactions)\\b.*?\\b(?:customer\\s*id|id|customer)\\s*(\\d+)\\b");
    private static final Pattern RE_LOAN_STATUS = Pattern.compile(
            "(?i)\\bloan\\s*status\\b.*?\\b(?:customer\\s*id|id|customer)\\s*(\\d+)\\b");
    private static final Pattern RE_LIST_LOANS = Pattern.compile(
            "(?i)\\b(?:list|show)\\s+loans\\b.*?\\b(?:customer\\s*id|id|customer)\\s*(\\d+)\\b");

    private Map<String, Object> tryHeuristic(String msg) {
        Matcher m;

        m = RE_BALANCE_BY_ID.matcher(msg);
        if (m.find()) {
            Integer id = safeInt(m.group(1));
            if (id != null) return handleGetBalance(Map.of("customer_id", id));
        }

        m = RE_BALANCE_BY_NAME.matcher(msg);
        if (m.find()) {
            String name = clean(m.group(1));
            if (name != null) return handleGetBalance(Map.of("name", name));
        }

        m = RE_TX_LASTN.matcher(msg);
        if (m.find()) {
            Integer n = safeInt(m.group(1));
            Integer id = safeInt(m.group(2));
            if (id != null) {
                Map<String, Object> slots = new HashMap<>();
                slots.put("customer_id", id);
                if (n != null) slots.put("n", clampN(n));
                return handleGetTransactions(slots);
            }
        }

        m = RE_LOAN_STATUS.matcher(msg);
        if (m.find()) {
            Integer id = safeInt(m.group(1));
            if (id != null) return handleGetLoanStatus(Map.of("customer_id", id));
        }

        m = RE_LIST_LOANS.matcher(msg);
        if (m.find()) {
            Integer id = safeInt(m.group(1));
            if (id != null) return handleGetLoans(Map.of("customer_id", id));
        }

        return null; // no heuristic match → stick with original UNKNOWN reply
    }

    // ----------------------------- Handlers (always non-empty reply) ------------------------------

    private Map<String, Object> handleGetBalance(Map<String, Object> slots) {
        Integer id = resolveCustomerId(slots);
        if (id == null) {
            return jsonReply("Whose balance? Provide a customer ID or name.", Map.of());
        }
        Optional<BigDecimal> bal = banking.getLatestBalance(id);
        if (bal.isEmpty()) {
            return jsonReply("No account found for that customer.", Map.of("customerId", id));
        }
        BigDecimal amount = money(bal.get());
        return jsonReply(
                "Balance for customer " + id + " is $" + amount,
                Map.of("customerId", id, "amount", amount)
        );
    }

    private Map<String, Object> handleGetTransactions(Map<String, Object> slots) {
        Integer id = resolveCustomerId(slots);
        if (id == null) {
            return jsonReply("Whose transactions? Provide a customer ID or name.", Map.of());
        }
        int n = clampN(parseN(slots, 5));
        List<Map<String, Object>> rows = banking.getTransactionsByCustomerId(id, n);

        List<Map<String, Object>> norm = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> x = new LinkedHashMap<>(r);
            if (x.containsKey("amount")) x.put("amount", moneySafe(x.get("amount")));
            norm.add(x);
        }

        if (norm.isEmpty()) {
            return jsonReply("No transactions found for that customer.", Map.of("customerId", id, "transactions", List.of()));
        }
        return jsonReply("Here are the last " + n + " transactions.", Map.of("customerId", id, "transactions", norm));
    }

    private Map<String, Object> handleGetLoanStatus(Map<String, Object> slots) {
        Integer id = resolveCustomerId(slots);
        if (id == null) {
            return jsonReply("Whose loan status? Provide a customer ID or name.", Map.of());
        }
        Optional<LoanStatus> s = banking.getLatestLoanStatus(id);
        if (s.isEmpty()) {
            return jsonReply("No loans found for that customer.", Map.of("customerId", id));
        }
        LoanStatus st = s.get();
        String status = normUpper(st.getStatus());
        BigDecimal amt = money(st.getAmount());
        String msg = "Loan status is " + status + (amt != null ? " with outstanding $" + amt : "") + ".";
        return jsonReply(msg, Map.of(
                "customerId", id,
                "status", status,
                "outstanding", amt,
                "loanId", st.getLoanId()
        ));
    }

    private Map<String, Object> handleGetLoans(Map<String, Object> slots) {
        Integer id = resolveCustomerId(slots);
        if (id == null) {
            return jsonReply("Whose loans? Provide a customer ID or name.", Map.of());
        }
        List<LoanStatus> list = banking.getLoansByCustomerId(id);

        List<Map<String, Object>> normalized = new ArrayList<>();
        for (LoanStatus l : list) {
            normalized.add(Map.of(
                    "loanId", l.getLoanId(),
                    "status", normUpper(l.getStatus()),
                    "amount", money(l.getAmount())
            ));
        }

        if (normalized.isEmpty()) {
            return jsonReply("No loans found for that customer.", Map.of("customerId", id, "loans", List.of()));
        }
        return jsonReply("Here are the loans.", Map.of("customerId", id, "loans", normalized));
    }

    private Map<String, Object> handleGetAccounts(Map<String, Object> slots) {
        Integer id = resolveCustomerId(slots);
        if (id == null) {
            return jsonReply("Whose accounts? Provide a customer ID or name.", Map.of());
        }
        // For now, return a simple message - you can implement account listing logic here
        return jsonReply("Account listing for customer " + id + " - feature coming soon!", Map.of("customerId", id));
    }

    // ----------------------------- Helpers ------------------------------

    private Map<String, Object> jsonReply(String reply, Map<String, Object> data) {
        return Map.of(
                "reply", (reply == null || reply.isBlank()) ? "..." : reply,
                "data", data == null ? Map.of() : data
        );
    }

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
        return n == null ? fallback : n;
    }

    private static int clampN(int n) { return Math.max(1, Math.min(n, 50)); }

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

    private static String normUpper(String s) {
        if (s == null || s.isBlank()) return "UNKNOWN";
        return s.toUpperCase(Locale.ROOT);
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal moneySafe(Object v) {
        if (v == null) return null;
        try {
            if (v instanceof BigDecimal bd) return money(bd);
            if (v instanceof Number n) return money(BigDecimal.valueOf(n.doubleValue()));
            return money(new BigDecimal(String.valueOf(v)));
        } catch (Exception e) {
            return null;
        }
    }
}
