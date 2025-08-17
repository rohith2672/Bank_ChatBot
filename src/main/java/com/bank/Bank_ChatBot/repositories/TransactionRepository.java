package com.bank.Bank_ChatBot.repositories;

import com.bank.Bank_ChatBot.entities.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {

    // All transactions for an account, newest first
    List<Transaction> findByAccountIdOrderByTimestampDesc(Integer accountId);

    // Optional: top N helper if you want to limit in code
    List<Transaction> findTop5ByAccount_IdOrderByTimestampDesc(Integer accountId);
}
