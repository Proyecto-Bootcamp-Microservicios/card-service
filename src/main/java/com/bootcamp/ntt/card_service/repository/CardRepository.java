package com.bootcamp.ntt.card_service.repository;

import com.bootcamp.ntt.cardservice.model.CreditCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CardRepository extends JpaRepository<CreditCard,String> {
}
