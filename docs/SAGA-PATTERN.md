# Saga Pattern Implementation

## Overview

This project implements the **Orchestration-based Saga Pattern** for managing distributed transactions across microservices. The Order Service acts as the saga orchestrator, coordinating the transaction flow and handling compensations when failures occur.

## Architecture

### Saga Flow

**Normal Flow (Happy Path):**
```
1. OrderCreated event → Order Service
2. Order Service starts saga (STARTED)
3. Order Service calls Payment Service (PAYMENT_PENDING)
4. Payment Service processes payment
5. Payment Service saves to database
6. Payment Service publishes PaymentProcessed event
7. Saga completes (COMPLETED)
```

**Compensation Flow (Failure Path):**
```
1. Payment Service fails or Circuit Breaker opens
2. Saga status changes to COMPENSATING
3. Order Service cancels payment (if created)
4. Payment Service updates status to CANCELLED
5. Payment Service publishes PaymentCancelled event
6. Order Service publishes OrderCancelled event
7. Saga status changes to FAILED
```

## Components

### 1. Saga State Entity

Tracks the state of each distributed transaction:

- **orderId**: Unique identifier for the order
- **status**: Current saga status (STARTED, PROCESSING, COMPLETED, COMPENSATING, FAILED)
- **currentStep**: Current step in the saga (ORDER_CREATED, PAYMENT_PENDING, PAYMENT_COMPLETED, COMPENSATED)
- **paymentId**: Reference to payment if created
- **timestamps**: Created and updated timestamps

### 2. Saga Orchestrator

Manages the saga lifecycle:

- **startSaga()**: Initiates a new saga transaction
- **processPaymentStep()**: Executes payment processing step
- **compensate()**: Handles rollback when failures occur
- **cancelPayment()**: Cancels payment as part of compensation
- **cancelOrder()**: Cancels order as part of compensation

### 3. Compensation Events

New Kafka topic for compensation events:

- **compensation-events**: Publishes OrderCancelled and PaymentCancelled events
- Used for auditing and triggering additional compensation logic

## Database Schema

### saga_state Table

```sql
CREATE TABLE saga_state (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL,
    current_step VARCHAR(50),
    payment_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Saga States

| State | Description |
|-------|-------------|
| STARTED | Saga initiated, order created |
| PROCESSING | Payment processing in progress |
| COMPLETED | All steps completed successfully |
| COMPENSATING | Rollback in progress |
| FAILED | Saga failed after compensation |

## Saga Steps

| Step | Description |
|------|-------------|
| ORDER_CREATED | Initial step after order creation |
| PAYMENT_PENDING | Payment processing initiated |
| PAYMENT_COMPLETED | Payment successfully processed |
| COMPENSATED | Compensation completed |

## Testing Saga Pattern

### Test Happy Path

```bash
# Get JWT token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin"}' | jq -r '.token')

# Create order
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"customerId": "test", "productId": "prod", "quantity": 1, "amount": 100.00}'

# Check saga state in database
docker-compose exec postgres psql -U postgres -d eventdb \
  -c "SELECT * FROM saga_state ORDER BY id DESC LIMIT 1;"
```

Expected result: status = 'COMPLETED', current_step = 'PAYMENT_COMPLETED'

### Test Compensation Flow

```bash
# Stop payment service to simulate failure
docker-compose stop payment-service

# Create order (will fail and trigger compensation)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"customerId": "test", "productId": "prod", "quantity": 1, "amount": 100.00}'

# Check saga state
docker-compose exec postgres psql -U postgres -d eventdb \
  -c "SELECT * FROM saga_state ORDER BY id DESC LIMIT 1;"

# Check compensation events
docker-compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic compensation-events \
  --from-beginning \
  --max-messages 5

# Restart payment service
docker-compose start payment-service
```

Expected result: status = 'FAILED', current_step = 'COMPENSATED'

### Test Circuit Breaker Integration

```bash
# Stop payment service
docker-compose stop payment-service

# Make 10 requests to open circuit breaker
for i in {1..10}; do
  curl -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d '{"customerId": "test", "productId": "prod", "quantity": 1, "amount": 100.00}'
  sleep 0.3
done

# Check circuit breaker state
curl http://localhost:8080/actuator/circuitbreakers | jq '.circuitBreakers.paymentService.state'

# All failed orders should have saga state = FAILED
docker-compose exec postgres psql -U postgres -d eventdb \
  -c "SELECT order_id, status, current_step FROM saga_state WHERE status = 'FAILED';"
```

## Monitoring Saga Execution

### View Saga States

```sql
-- All saga states
SELECT * FROM saga_state ORDER BY created_at DESC;

-- Failed sagas
SELECT * FROM saga_state WHERE status = 'FAILED';

-- In-progress sagas
SELECT * FROM saga_state WHERE status IN ('STARTED', 'PROCESSING', 'COMPENSATING');

-- Saga success rate
SELECT 
    status,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 2) as percentage
FROM saga_state
GROUP BY status;
```

### View Compensation Events

```bash
# View all compensation events
docker-compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic compensation-events \
  --from-beginning

# View with Kafka UI
# Open http://localhost:8090
# Navigate to Topics → compensation-events
```

### Logs

```bash
# Order Service logs (saga orchestrator)
docker-compose logs -f order-service | grep -i saga

# Payment Service logs (compensation)
docker-compose logs -f payment-service | grep -i cancel
```

## Benefits

1. **Consistency**: Ensures data consistency across distributed services
2. **Reliability**: Automatic compensation on failures
3. **Observability**: Complete audit trail of saga execution
4. **Resilience**: Integrates with circuit breaker for fault tolerance
5. **Idempotency**: Prevents duplicate operations during retries

## Limitations

1. **Eventual Consistency**: Not ACID transactions, eventual consistency model
2. **Complexity**: More complex than simple request-response
3. **Compensation Logic**: Requires careful design of compensation steps
4. **State Management**: Requires persistent saga state storage

## Production-Ready Improvements

### 1️⃣ Saga Timeout Handling ✅

**Implementation:**
- Added `timeout_at` field to `saga_state` table
- Default timeout: 30 seconds from saga creation
- Scheduled task checks for expired sagas every 5 seconds
- Automatic compensation triggered for timed-out sagas

**Database Schema:**
```sql
ALTER TABLE saga_state ADD COLUMN timeout_at TIMESTAMP;
```

**Code:**
```java
// SagaState.java
@Column(name = "timeout_at")
private LocalDateTime timeoutAt;

public SagaState(Long orderId, String status, String currentStep) {
    this();
    this.orderId = orderId;
    this.status = status;
    this.currentStep = currentStep;
    this.timeoutAt = LocalDateTime.now().plusSeconds(30);
}

// SagaOrchestrator.java
@Scheduled(fixedDelay = 5000)
public void checkTimeouts() {
    sagaStateRepository.findAll().stream()
        .filter(saga -> "PROCESSING".equals(saga.getStatus()) || "STARTED".equals(saga.getStatus()))
        .filter(saga -> saga.getTimeoutAt() != null && saga.getTimeoutAt().isBefore(LocalDateTime.now()))
        .forEach(saga -> {
            logger.warn("Saga timeout detected for order: {}", saga.getOrderId());
            compensate(saga);
        });
}
```

**Benefits:**
- ✅ Prevents hanging sagas
- ✅ Automatic cleanup of stale transactions
- ✅ Better resource management
- ✅ Configurable timeout duration

### 2️⃣ Saga Recovery on Service Restart ✅

**Implementation:**
- `@PostConstruct` method runs on service startup
- Queries all sagas with status IN_PROGRESS or STARTED
- Automatically triggers compensation for incomplete sagas
- Ensures no lost transactions after service restart

**Code:**
```java
// SagaOrchestrator.java
@PostConstruct
public void recoverSagas() {
    logger.info("Checking for in-progress sagas to recover...");
    sagaStateRepository.findAll().stream()
        .filter(saga -> "PROCESSING".equals(saga.getStatus()) || "STARTED".equals(saga.getStatus()))
        .forEach(saga -> {
            logger.info("Recovering saga for order: {} with status: {}", saga.getOrderId(), saga.getStatus());
            compensate(saga);
        });
}

// OrderServiceApplication.java
@SpringBootApplication
@EnableKafka
@EnableScheduling  // Required for scheduled tasks
public class OrderServiceApplication { ... }
```

**Benefits:**
- ✅ No lost transactions on service restart
- ✅ Automatic recovery without manual intervention
- ✅ Consistent state after failures
- ✅ Better reliability in production

### 3️⃣ Saga Visualization Dashboard (Grafana) ✅

**Implementation:**
- Created `SagaMetricsService` to expose Prometheus metrics
- Metrics updated every 10 seconds
- Grafana dashboard with 6 panels
- Real-time monitoring of saga states

**Metrics Exposed:**
```
saga_total          - Total number of sagas
saga_completed      - Number of completed sagas
saga_failed         - Number of failed sagas
saga_processing     - Number of in-progress sagas
saga_success_rate   - Success rate percentage
```

**Code:**
```java
@Service
public class SagaMetricsService {
    @Autowired
    private SagaStateRepository sagaStateRepository;
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @Scheduled(fixedDelay = 10000)
    public void updateMetrics() {
        long total = sagaStateRepository.count();
        long completed = sagaStateRepository.findAll().stream()
            .filter(s -> "COMPLETED".equals(s.getStatus()))
            .count();
        long failed = sagaStateRepository.findAll().stream()
            .filter(s -> "FAILED".equals(s.getStatus()))
            .count();
        
        meterRegistry.gauge("saga_total", total);
        meterRegistry.gauge("saga_completed", completed);
        meterRegistry.gauge("saga_failed", failed);
        meterRegistry.gauge("saga_success_rate", total > 0 ? (double) completed / total * 100 : 0);
    }
}
```

**Dashboard Access:**
```
URL: http://localhost:3000
Username: admin
Password: admin
Dashboard: "Saga Pattern Monitoring"
```

**Dashboard Panels:**
1. Total Sagas - Stat panel
2. Completed Sagas - Green stat panel
3. Failed Sagas - Red stat panel
4. Success Rate - Percentage with thresholds
5. Saga Status Over Time - Time series graph
6. Saga Distribution - Pie chart

**Benefits:**
- ✅ Real-time saga monitoring
- ✅ Visual representation of saga states
- ✅ Easy troubleshooting
- ✅ Performance insights
- ✅ Success rate tracking

## Future Enhancements

1. Support parallel saga steps
2. Implement saga versioning for backward compatibility
3. Add configurable per-saga-type timeouts
4. Implement retry before compensation
5. Add saga history tracking for audit
