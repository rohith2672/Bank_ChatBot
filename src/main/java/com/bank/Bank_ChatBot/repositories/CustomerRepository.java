package com.bank.Bank_ChatBot.repositories;

import com.bank.Bank_ChatBot.entities.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Integer> {
}
