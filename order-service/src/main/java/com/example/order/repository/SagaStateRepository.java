package com.example.order.repository;

import com.example.order.entity.SagaState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SagaStateRepository extends JpaRepository<SagaState, Long> {
    Optional<SagaState> findByOrderId(Long orderId);
}
