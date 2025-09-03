package com.bank.Bank_ChatBot.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BankingService {

    private final JdbcTemplate jdbc;

    public BankingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Find customer_id by exact full_name (case-insensitive). */
    public Optional<Integer> findCustomerIdByName(String fullName) {
        final String sql = """
            SELECT customer_id
            FROM customers
            WHERE UPPER(TRIM(full_name)) = UPPER(?)
        """;
        try {
            Integer id = jdbc.queryForObject(sql, Integer.class, fullName);
            return Optional.ofNullable(id);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    /** Latest account balance for a customer (by accounts.created_at desc). */
    public Optional<BigDecimal> getLatestBalance(Integer customerId) {
        final String sql = """
            SELECT balance
            FROM accounts
            WHERE customer_id = ?
            ORDER BY created_at DESC
            LIMIT 1
        """;
        try {
            BigDecimal amt = jdbc.queryForObject(sql, BigDecimal.class, customerId);
            return Optional.ofNullable(amt);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    /** Last N transactions for a customer across accounts. */
    public List<Map<String, Object>> getTransactionsByCustomerId(Integer customerId, int n) {
        final String sql = """
            SELECT t.transaction_date, t.amount, t.type, t.description
            FROM transactions t
            JOIN accounts a ON a.account_id = t.account_id
            WHERE a.customer_id = ?
            ORDER BY t.transaction_date DESC
            LIMIT ?
        """;
        return jdbc.queryForList(sql, customerId, n);
    }

    /** Latest loan status for a customer. */
    public Optional<LoanStatus> getLatestLoanStatus(Integer customerId) {
        final String sql = """
            SELECT loan_id, status, amount
            FROM loans
            WHERE customer_id = ?
            ORDER BY end_date DESC, loan_id DESC
            LIMIT 1
        """;
        try {
            return jdbc.query(sql, rs -> {
                if (!rs.next()) return Optional.empty();
                LoanStatus s = new LoanStatus(
                        rs.getInt("loan_id"),
                        rs.getString("status"),
                        rs.getBigDecimal("amount")
                );
                return Optional.of(s);
            }, customerId);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    /** All loans for a customer (latest first). */
    public List<LoanStatus> getLoansByCustomerId(Integer customerId) {
        final String sql = """
            SELECT loan_id, status, amount
            FROM loans
            WHERE customer_id = ?
            ORDER BY end_date DESC, loan_id DESC
        """;
        return jdbc.query(sql, (rs, rowNum) -> new LoanStatus(
                rs.getInt("loan_id"),
                rs.getString("status"),
                rs.getBigDecimal("amount")
        ), customerId);
    }

    // --- Simple DTO for loan status ---
    public static class LoanStatus {
        private final Integer loanId;
        private final String status;
        private final BigDecimal amount;

        public LoanStatus(Integer loanId, String status, BigDecimal amount) {
            this.loanId = loanId;
            this.status = status;
            this.amount = amount;
        }
        public Integer getLoanId() { return loanId; }
        public String getStatus()  { return status; }
        public BigDecimal getAmount() { return amount; }
    }
}
