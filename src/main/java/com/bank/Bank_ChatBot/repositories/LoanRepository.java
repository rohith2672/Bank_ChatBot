package com.bank.Bank_ChatBot.repositories;

import com.bank.Bank_ChatBot.entities.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LoanRepository extends JpaRepository<Loan, Integer> {
    List<Loan> findByCustomerId(Integer customer_id);
}
