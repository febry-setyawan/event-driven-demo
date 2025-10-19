# Event-Driven Architecture POC Demo

## Overview

This POC demonstrates a complete Event-Driven Architecture using Spring Boot microservices with Kafka as message broker, PostgreSQL as database, and LGTM stack for monitoring.

## Architecture

```
                    ┌─────────────────┐
                    │     Client      │
                    └────────┬────────┘
                             │ (HTTP Port 80)
                             ▼
                    ┌─────────────────┐
                    │  Nginx Proxy    │◄─── Reverse Proxy
                    │   (Port 80)     │     (CORS + Swagger UI)
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │ Order Gateway   │◄─── API Gateway
                    │   (Port 8080)   │     (Spring Cloud Gateway)
                    │ + Redis + Kafka │     (JWT + Rate Limiting)
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
     ┌────────────┐  ┌────────────┐  ┌────────────┐
     │   Order    │  │  Payment   │  │   Kafka    │
     │  Service   │─▶│  Service   │  │  Cluster   │
     │ (Internal) │  │ (Internal) │  │ (4 topics) │
     │ + Saga     │  │            │  └────────────┘
     └──────┬─────┘  └──────┬─────┘
            │               │
            └────────┬──────┘
                     ▼
            ┌─────────────────┐
            │   PostgreSQL    │
            │  - orders       │
            │  - payments     │
            │  - saga_state   │
            └─────────────────┘
```

**Key Points:**
- All external traffic goes through Nginx reverse proxy (port 80)
- Nginx serves Swagger UI and proxies API requests to Order Gateway
- Order Gateway (port 8080) handles routing, JWT auth, and rate limiting
- Order and Payment services are internal only (no external ports)
- JWT authentication validated at Order Gateway
- Redis stores JWT tokens with TTL auto-expiration
- Order Gateway publishes events to Kafka and routes requests
- Order Service implements Saga orchestrator for distributed transactions
- Both Order and Payment services persist data to PostgreSQL
- Saga state tracked in database for compensation and recovery
- Input validation with Bean Validation at gateway level
- Swagger UI aggregates API docs from all services

## Services

1. **Nginx Reverse Proxy** (Port 80) - Entry point for all external traffic, serves Swagger UI, proxies API requests
2. **Order Gateway** (Port 8080) - Spring Cloud Gateway with JWT auth, rate limiting, circuit breaker, and Kafka event publishing
3. **Order Service** (Internal) - Saga orchestrator, business logic, consumes Kafka events, persists orders and saga state to PostgreSQL
4. **Payment Service** (Internal) - Payment processing, persists payments to PostgreSQL
5. **Redis** (Port 6379) - JWT token storage and rate limiting backend

## Monitoring Stack (LGTM)

- **Loki** (Port 3100) - Log aggregation
- **Grafana** (Port 3000) - Visualization dashboard
- **Tempo** (Port 3200, 4317) - Distributed tracing
- **Mimir** (Port 9009) - Metrics storage
- **Grafana Agent** (Port 9411) - Unified agent for metrics, logs, and traces collection

## Quick Start

### Prerequisites
- Docker and Docker Compose
- Java 17+ (for local development)
- Maven 3.8+ (for local development)

### Run the Demo

1. **Build services:**
   ```bash
   ./build-services.sh
   ```

2. **Start all services:**
   ```bash
   docker-compose up -d
   ```

3. **Check services status:**
   ```bash
   docker-compose ps
   ```

4. **Test the API:**
   ```bash
   # Login to get JWT token
   curl -X POST http://localhost/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username": "admin", "password": "admin"}'
   
   # Create an order (use token from login response)
   curl -X POST http://localhost/api/orders \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer <your-token>" \
     -d '{"customerId": "123", "productId": "456", "quantity": 2, "amount": 100.00}'
   
   # Get order status
   curl http://localhost/api/orders/1 \
     -H "Authorization: Bearer <your-token>"
   ```

5. **Access Services:**
   - **Swagger UI**: http://localhost (Aggregated API docs via Nginx)
   - **Grafana**: http://localhost:3000 (admin/admin)
   - **Kafka UI**: http://localhost:8090
   - **Redis**: localhost:6379
   - **PostgreSQL**: localhost:5432 (user: postgres, password: postgres)

   **Note**: All API requests go through Nginx (port 80). Order Gateway, Order Service, and Payment Service are internal only.

### Stop the Demo

```bash
docker-compose down -v
```

## Event Flow

### Happy Path (Successful Order)
1. **Order Gateway** receives HTTP POST request to create order
2. **Order Gateway** validates request with Bean Validation
3. **Order Gateway** generates correlationId and publishes `OrderCreated` event to **order-events** Kafka topic
4. **Order Service** consumes the event and persists order to PostgreSQL (generates orderId)
5. **Order Service** publishes order response with orderId to **order-response** topic
6. **Order Gateway** receives order response and returns orderId to client
7. **Order Service** creates saga state (STARTED → PROCESSING)
8. **Order Service** calls **Payment Service** via HTTP with circuit breaker
9. **Payment Service** checks for existing payment (idempotency)
10. **Payment Service** persists payment to PostgreSQL
11. **Payment Service** publishes `PaymentProcessed` event to **payment-events** topic
12. **Order Service** updates saga state to COMPLETED
13. **Order Service** updates order status to COMPLETED

### Compensation Flow (Failed Payment)
1. **Payment Service** is unavailable or returns error
2. **Circuit Breaker** opens after failure threshold
3. **Order Service** saga orchestrator detects failure
4. **Order Service** updates saga state to COMPENSATING
5. **Order Service** cancels payment (if created) via Payment Service
6. **Payment Service** publishes `PaymentCancelled` event to **compensation-events** topic
7. **Order Service** publishes `OrderCancelled` event to **compensation-events** topic
8. **Order Service** updates saga state to FAILED with step COMPENSATED
9. **Order Service** updates order status to FAILED

### Saga Timeout Handling
1. **Scheduled task** checks for expired sagas every 5 seconds
2. Sagas exceeding 30-second timeout are detected
3. **Compensation flow** triggered automatically for timed-out sagas

### Saga Recovery on Restart
1. **Order Service** starts up and runs @PostConstruct recovery method
2. Queries all sagas with status PROCESSING or STARTED
3. **Compensation flow** triggered for in-progress sagas
4. Ensures no lost transactions after service restart

### Observability
- All services emit logs, metrics, and traces to **Grafana Agent**
- Grafana Agent forwards data to **Loki** (logs), **Mimir** (metrics), and **Tempo** (traces)
- Saga metrics exposed via Prometheus: total, completed, failed, success_rate
- Failed messages sent to **dead-letter-queue** for manual review

## Monitoring

### Grafana Dashboards

Access Grafana at http://localhost:3000 with credentials `admin/admin`.

Pre-configured dashboards:
- **Event-Driven Architecture Metrics** - HTTP request rate, response time, JVM memory, Kafka messages
- **Saga Pattern Monitoring** - Total sagas, completed/failed counts, success rate, time series, distribution

### Logs

View aggregated logs in Grafana Explore using Loki data source:
1. Go to Explore (compass icon)
2. Select "Loki" datasource
3. Query examples:
```
{container_name=~".*api-gateway.*"} |= "Creating order"
{container_name=~".*order-service.*"} |= "Processing order"
{container_name=~".*payment-service.*"} |= "Payment"
```

### Traces

Distributed tracing available in Grafana Explore using Tempo data source:
1. Go to Explore (compass icon)
2. Select "Tempo" datasource
3. Search traces by service name or trace ID
4. Traces show the complete request flow: Order Gateway → Order Service → Payment Service

## Development

### Build Services Locally

```bash
# Build all services
./build-services.sh

# Or build individually
cd order-gateway && mvn clean package
cd order-service && mvn clean package  
cd payment-service && mvn clean package
```

### Environment Variables

Key configuration via environment variables:

```bash
# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# Database
POSTGRES_URL=jdbc:postgresql://postgres:5432/eventdb
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# Monitoring
LOKI_URL=http://loki:3100
TEMPO_URL=http://grafana-agent:9411
```

## Authentication

All API endpoints (except `/api/auth/login`) require JWT authentication.

**Default Credentials:**
- Username: `admin`
- Password: `admin`

**Authentication Flow:**
1. Login via `POST /api/auth/login` to get JWT token
2. Include token in subsequent requests: `Authorization: Bearer <token>`
3. Token expires after 24 hours

## API Documentation

All API requests must go through Nginx reverse proxy (port 80), which forwards to Order Gateway. Direct access to Order Gateway, Order Service, and Payment Service is not allowed.

**Swagger UI**: Access comprehensive API documentation at http://localhost with aggregated specs from all services.

### Order Gateway Endpoints

| Method | Endpoint | Auth Required | Description |
|--------|----------|---------------|-------------|
| POST | `/api/auth/login` | No | Login and get JWT token |
| POST | `/api/orders` | Yes | Create new order |
| GET | `/api/orders/{id}` | Yes | Get order by ID |
| GET | `/actuator/health` | No | Health check |

### Payment Endpoints (via API Gateway)

| Method | Endpoint | Auth Required | Description |
|--------|----------|---------------|-------------|
| POST | `/api/payments` | Yes | Process payment |
| GET | `/api/payments/{id}` | Yes | Get payment details |
| POST | `/api/payments/{id}/cancel` | Yes | Cancel payment (Saga compensation) |

**Note**: All endpoints are accessed through Nginx → Order Gateway. Order and Payment services are internal only.

## Kafka Topics

Topics are automatically created on startup:

- **order-events** (3 partitions) - Order lifecycle events from Order Gateway
- **order-response** (1 partition) - Order creation responses with orderId from Order Service to Order Gateway
- **payment-events** (3 partitions) - Payment processing events
- **compensation-events** (3 partitions) - Saga compensation events
- **dead-letter-queue** (1 partition) - Failed messages for manual review

## Database Schema

```sql
-- Orders table
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Payments table  
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT REFERENCES orders(id),
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Saga state table
CREATE TABLE saga_state (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL,
    current_step VARCHAR(50),
    payment_id BIGINT,
    timeout_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Troubleshooting

### Common Issues

1. **Services not starting:**
   ```bash
   docker-compose logs <service-name>
   ```

2. **Kafka connection issues:**
   ```bash
   docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092
   ```

3. **Database connection issues:**
   ```bash
   docker-compose exec postgres psql -U postgres -d eventdb -c "\dt"
   ```

4. **Port conflicts:**
   - Check if ports 80, 8080-8082, 3000, 3100, 3200, 4317, 5432, 9092, 9411 are available
   - Modify `docker-compose.yml` if needed

5. **Swagger UI not loading:**
   ```bash
   # Check nginx is running
   docker-compose logs nginx
   
   # Verify nginx can reach order-gateway
   docker-compose exec nginx curl http://order-gateway:8080/actuator/health
   ```

6. **Metrics not showing in Grafana:**
   ```bash
   # Check Grafana Agent is scraping
   docker-compose logs grafana-agent | grep "scrape"
   
   # Verify Mimir is receiving data
   curl -H "X-Scope-OrgID: demo" http://localhost:9009/prometheus/api/v1/label/__name__/values
   ```

7. **Schema validation errors:**
   ```bash
   # If you see "wrong column type" errors, rebuild database
   docker-compose down -v
   docker-compose up -d
   ```

### Logs Location

- Application logs: Available in Loki via Grafana
- Docker logs: `docker-compose logs <service>`
- Kafka logs: `docker-compose logs kafka`

## Performance Testing

```bash
# Load test with Apache Bench (via Nginx)
ab -n 1000 -c 10 -H "Content-Type: application/json" \
   -p order-payload.json \
   http://localhost/api/orders

# Monitor in Grafana during load test
```

## Production-Ready Features

| Feature | Status | Description |
|---------|--------|-------------|
| **JWT Authentication** | ✅ | Secure API endpoints with token-based authentication |
| **Input Validation** | ✅ | Bean Validation with detailed error messages |
| **Circuit Breaker** | ✅ | Resilience4j for preventing cascading failures |
| **Idempotency** | ✅ | Prevents duplicate payments via database constraints |
| **Retry Mechanism** | ✅ | HTTP calls and event publishing with exponential backoff |
| **Dead Letter Queue** | ✅ | Failed messages captured with full error metadata |
| **Error Handling** | ✅ | Comprehensive exception handling with structured logging |
| **Sequential Startup** | ✅ | Services start in correct order with health checks |
| **Auto-create Topics** | ✅ | Kafka topics created automatically on startup |
| **Database Indexes** | ✅ | Optimized queries with proper indexes |
| **Distributed Tracing** | ✅ | End-to-end request tracking across all services |
| **Structured Logging** | ✅ | Parameterized logs with trace IDs for correlation |
| **Health Checks** | ✅ | Actuator endpoints with Docker healthcheck integration |
| **Saga Pattern** | ✅ | Orchestration-based saga for distributed transactions |
| **Saga Timeout Handling** | ✅ | Automatic compensation for timed-out sagas (30s default) |
| **Saga Recovery** | ✅ | Automatic recovery of in-progress sagas on service restart |
| **Saga Monitoring** | ✅ | Real-time Grafana dashboard with metrics and visualization |
| **API Rate Limiting** | ✅ | Spring Cloud Gateway rate limiting with Redis (5 req/sec, burst 10) |
| **Swagger Documentation** | ✅ | Aggregated OpenAPI docs from all services via Nginx reverse proxy |
| **CORS Handling** | ✅ | Nginx reverse proxy eliminates CORS issues with single origin |

For more details, see [docs/PRODUCTION-READY.md](docs/PRODUCTION-READY.md)

## Documentation

- [Production-Ready Features](docs/PRODUCTION-READY.md)
- [Saga Pattern Implementation](docs/SAGA-PATTERN.md)
- [Release Notes](docs/RELEASE-NOTES.md)

## Monitoring Circuit Breakers

Circuit breaker state can be monitored via actuator endpoints:

```bash
# Check circuit breaker status (via Nginx)
curl http://localhost/actuator/circuitbreakers

# Check circuit breaker events
curl http://localhost/actuator/circuitbreakerevents

# Check health including circuit breakers
curl http://localhost/actuator/health
```

**Circuit Breaker States:**
- **CLOSED**: Normal operation, requests pass through
- **OPEN**: Circuit is open, requests fail fast with fallback
- **HALF_OPEN**: Testing if service recovered, limited requests allowed

### Testing Circuit Breaker

Test circuit breaker state transitions:

```bash
# Get JWT token (via Nginx)
TOKEN=$(curl -s -X POST http://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin"}' | jq -r '.token')

# 1. Stop order-service to simulate failure
docker-compose stop order-service

# 2. Make 10 requests to trigger OPEN state
for i in {1..10}; do
  curl -H "Authorization: Bearer $TOKEN" http://localhost/api/orders/1
  sleep 0.3
done

# 3. Check state (should be OPEN)
curl http://localhost/actuator/circuitbreakers | jq '.circuitBreakers.orderService.state'

# 4. Wait 10 seconds for HALF_OPEN transition
sleep 10

# 5. Check state (should be HALF_OPEN)
curl http://localhost/actuator/circuitbreakers | jq '.circuitBreakers.orderService.state'

# 6. Restart order-service
docker-compose start order-service
sleep 8

# 7. Create order and make successful requests to close circuit
curl -X POST http://localhost/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"customerId": "test", "productId": "prod", "quantity": 1, "amount": 100.00}'

for i in {1..5}; do
  curl -H "Authorization: Bearer $TOKEN" http://localhost/api/orders/1
  sleep 0.5
done

# 8. Check state (should be CLOSED)
curl http://localhost/actuator/circuitbreakers | jq '.circuitBreakers.orderService.state'
```

## Rate Limiting

Order Gateway implements distributed rate limiting using Spring Cloud Gateway RequestRateLimiter with Redis backend.

**Configuration:**
- Default: 5 requests per second with burst capacity of 10
- Distributed across multiple gateway instances via Redis
- Configurable via application.yml

**Configuration (application.yml):**
```yaml
redis-rate-limiter:
  replenishRate: 5        # Tokens per second
  burstCapacity: 10       # Maximum burst
  requestedTokens: 1      # Tokens per request
```

**Testing Rate Limit:**
```bash
# Get JWT token (via Nginx)
TOKEN=$(curl -s -X POST http://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin"}' | jq -r '.token')

# Make 101 requests to trigger rate limit
for i in {1..101}; do
  curl -H "Authorization: Bearer $TOKEN" http://localhost/api/orders/1
done

# Expected: First 100 succeed, 101st returns 429 Too Many Requests
```

**Response when rate limited:**
```json
{
  "error": "Too many requests. Please try again later."
}
```

## Testing

Comprehensive test suite with 42 test scenarios:

```bash
# Run all tests (100% coverage)
./test-comprehensive.sh
```

**Test Coverage:**
- Authentication (5 tests) - Valid/invalid credentials, empty fields, malformed JSON
- Order Creation (7 tests) - Valid data, unauthorized, validation, edge cases
- Order Retrieval (4 tests) - Existing, non-existent, unauthorized, invalid format
- Payment (3 tests) - Existing, unauthorized, non-existent
- Health Checks (2 tests) - Actuator, orders health
- Rate Limiting (2 tests) - Enforcement, recovery
- Performance (4 tests) - Response time, concurrent requests
- Circuit Breaker (4 tests) - State check, service availability, fallback, metrics
- Saga Pattern (7 tests) - Order flow, status, payment, idempotency, compensation, timeout, metrics
- Edge Cases (4 tests) - Long input, special characters, SQL injection, XSS

## Next Steps

1. Add authentication/authorization ✅ Completed in v1.1
2. Implement circuit breakers (Resilience4j) ✅ Completed in v1.2
3. Add saga patterns for distributed transactions ✅ Completed in v1.3
4. Implement Spring Cloud Gateway for rate limiting, input validation, etc ✅ Completed in v1.4
5. Add API versioning
6. Enhance monitoring with alerts and SLOs

## License

MIT License - See LICENSE file for details.
