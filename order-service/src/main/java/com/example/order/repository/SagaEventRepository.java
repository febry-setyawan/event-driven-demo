package com.example.order.repository;

import com.example.order.entity.SagaEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SagaEventRepository extends JpaRepository<SagaEvent, Long> {
    List<SagaEvent> findBySagaIdOrderByCreatedAtAsc(String sagaId);
}
