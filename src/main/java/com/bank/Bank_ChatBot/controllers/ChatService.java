package com.bank.Bank_ChatBot.controllers;

import com.bank.Bank_ChatBot.entities.Account;
import com.bank.Bank_ChatBot.entities.Customer;
import com.bank.Bank_ChatBot.entities.Loan;
import com.bank.Bank_ChatBot.entities.Transaction;
import com.bank.Bank_ChatBot.repositories.AccountRepository;
import com.bank.Bank_ChatBot.repositories.CustomerRepository;
import com.bank.Bank_ChatBot.repositories.LoanRepository;
import com.bank.Bank_ChatBot.repositories.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LoanRepository loanRepository;

    public ChatService(CustomerRepository customerRepository,
                       AccountRepository accountRepository,
                       TransactionRepository transactionRepository,
                       LoanRepository loanRepository) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.loanRepository = loanRepository;
    }

    // main entry
    public String getResponse(String message) {
        if (message == null || message.isBlank()) {
            return help();
        }
        String msg = message.trim().toLowerCase();

        try {
            // --- list customers ---
            if (msg.startsWith("list customers")) {
                List<Customer> customers = customerRepository.findAll();
                if (customers.isEmpty()) return "No customers found.";
                String body = customers.stream()
                        .sorted(Comparator.comparing(Customer::getId))
                        .map(c -> String.format("customer %d: %s (%s)", c.getId(), c.getFullName(), c.getEmail()))
                        .collect(Collectors.joining("\n"));
                return "Customers:\n" + body;
            }

            // --- customer {id} ---
            if (msg.startsWith("customer")) {
                Integer id = safeInt(extractTrailingNumber(message, "customer"));
                if (id == null) return "Please provide a valid numeric customer id.";
                Optional<Customer> oc = customerRepository.findById(id);
                if (oc.isEmpty()) return "Customer " + id + " not found.";
                Customer c = oc.get();
                String header = String.format("customer %d: %s (%s)", c.getId(), c.getFullName(), c.getEmail());

                List<Account> accounts = accountRepository.findByCustomerId(id);
                String accountsStr = accounts.isEmpty() ? "(none)" :
                        accounts.stream()
                                .sorted(Comparator.comparing(Account::getId))
                                .map(a -> String.format("  account %d: %s, balance=%.2f",
                                        a.getId(), a.getType(), a.getBalance()))
                                .collect(Collectors.joining("\n"));

                List<Loan> loans = loanRepository.findByCustomerId(id);
                String loansStr = loans.isEmpty() ? "(none)" :
                        loans.stream()
                                .sorted(Comparator.comparing(Loan::getId))
                                .map(l -> String.format("  loan %d: amount=%.2f, status=%s",
                                        l.getId(), l.getAmount(), l.getStatus()))
                                .collect(Collectors.joining("\n"));

                return header + "\naccounts:\n" + accountsStr + "\nloans:\n" + loansStr;
            }

            // --- accounts for customer {id} ---
            if (msg.startsWith("accounts for customer")) {
                Integer id = safeInt(extractTrailingNumber(message, "accounts for customer"));
                if (id == null) return "Please provide a valid numeric customer id.";
                List<Account> accounts = accountRepository.findByCustomerId(id);
                if (accounts.isEmpty()) return "No accounts found for customer " + id + ".";
                return accounts.stream()
                        .sorted(Comparator.comparing(Account::getId))
                        .map(a -> String.format("account %d: %s, balance=%.2f",
                                a.getId(), a.getType(), a.getBalance()))
                        .collect(Collectors.joining("\n"));
            }

            // --- balance for account {id} ---
            if (msg.startsWith("balance for account")) {
                Integer id = safeInt(extractTrailingNumber(message, "balance for account"));
                if (id == null) return "Please provide a valid numeric account id.";
                Optional<Account> oa = accountRepository.findById(id);
                if (oa.isEmpty()) return "Account " + id + " not found.";
                Account a = oa.get();
                return String.format("Account %d (%s) balance: %.2f", a.getId(), a.getType(), a.getBalance());
            }

            // --- transactions for account {id} ---
            if (msg.startsWith("transactions for account")) {
                Integer id = safeInt(extractTrailingNumber(message, "transactions for account"));
                if (id == null) return "Please provide a valid numeric account id.";
                // requires repository method: List<Transaction> findByAccountIdOrderByTimestampDesc(Integer accountId);
                List<Transaction> txs = transactionRepository.findByAccountIdOrderByTimestampDesc(id);
                if (txs.isEmpty()) return "No transactions found for account " + id + ".";
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                return txs.stream()
                        .map(t -> String.format("[%s] %s %.2f - %s",
                                t.getTimestamp() == null ? "no-date" : fmt.format(t.getTimestamp()),
                                t.getType(), t.getAmount(),
                                t.getDescription() == null ? "" : t.getDescription()))
                        .collect(Collectors.joining("\n"));
            }

            // --- loans for customer {id} ---
            if (msg.startsWith("loans for customer")) {
                Integer id = safeInt(extractTrailingNumber(message, "loans for customer"));
                if (id == null) return "Please provide a valid numeric customer id.";
                List<Loan> loans = loanRepository.findByCustomerId(id);
                if (loans.isEmpty()) return "No loans found for customer " + id + ".";
                return loans.stream()
                        .sorted(Comparator.comparing(Loan::getId))
                        .map(l -> String.format("loan %d: amount=%.2f, status=%s",
                                l.getId(), l.getAmount(), l.getStatus()))
                        .collect(Collectors.joining("\n"));
            }

            // default help
            return help();

        } catch (Exception ex) {
            // keep it friendly; logs will show details from controllers
            return "Sorry — I hit an error.";
        }
    }

    // ---------- helpers ----------

    private String help() {
        return "Hi! I can answer:\n"
                + "• list customers\n"
                + "• customer {id}\n"
                + "• accounts for customer {id}\n"
                + "• balance for account {id}\n"
                + "• transactions for account {id}\n"
                + "• loans for customer {id}\n";
    }

    private Integer safeInt(String s) {
        if (s == null) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String extractTrailingNumber(String text, String commandPrefix) {
        // returns the part after the prefix, e.g. "customer 3" -> "3"
        String raw = text == null ? "" : text.trim();
        String prefix = commandPrefix.toLowerCase().trim();
        if (!raw.toLowerCase().startsWith(prefix)) return null;
        String rest = raw.substring(prefix.length()).trim();
        // allow formats like "customer: 3", "customer - 3"
        rest = rest.replaceFirst("^[^0-9-]*", "");
        return rest;
    }
}
