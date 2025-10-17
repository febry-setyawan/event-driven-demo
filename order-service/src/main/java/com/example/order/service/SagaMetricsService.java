package com.example.order.service;

import com.example.order.repository.SagaStateRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SagaMetricsService {

    @Autowired
    private SagaStateRepository sagaStateRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    private final AtomicInteger totalGauge = new AtomicInteger(0);
    private final AtomicInteger completedGauge = new AtomicInteger(0);
    private final AtomicInteger failedGauge = new AtomicInteger(0);
    private final AtomicInteger processingGauge = new AtomicInteger(0);
    private final AtomicInteger successRateGauge = new AtomicInteger(0);

    public SagaMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        meterRegistry.gauge("saga_total", totalGauge);
        meterRegistry.gauge("saga_completed", completedGauge);
        meterRegistry.gauge("saga_failed", failedGauge);
        meterRegistry.gauge("saga_processing", processingGauge);
        meterRegistry.gauge("saga_success_rate", successRateGauge);
    }

    @Scheduled(fixedDelay = 10000)
    public void updateMetrics() {
        long total = sagaStateRepository.count();
        long completed = sagaStateRepository.findAll().stream()
            .filter(s -> "COMPLETED".equals(s.getStatus()))
            .count();
        long failed = sagaStateRepository.findAll().stream()
            .filter(s -> "FAILED".equals(s.getStatus()))
            .count();
        long processing = sagaStateRepository.findAll().stream()
            .filter(s -> "PROCESSING".equals(s.getStatus()) || "STARTED".equals(s.getStatus()))
            .count();

        totalGauge.set((int) total);
        completedGauge.set((int) completed);
        failedGauge.set((int) failed);
        processingGauge.set((int) processing);
        
        if (total > 0) {
            successRateGauge.set((int) ((double) completed / total * 100));
        }
    }
}
