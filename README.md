# Event-Driven Architecture POC Demo

## Overview

This POC demonstrates a complete Event-Driven Architecture using Spring Boot microservices with Kafka as message broker, PostgreSQL as database, and LGTM stack for monitoring.

## Architecture

```
                    ┌─────────────────┐
                    │     Client      │
                    └────────┬────────┘
                             │ (All traffic)
                             ▼
                    ┌─────────────────┐
                    │   API Gateway   │◄─── Single Entry Point
                    │   (Port 8080)   │     (JWT Authentication)
                    │   + Redis       │
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
- All external traffic goes through API Gateway (port 8080)
- Order and Payment services are internal only (no external ports)
- JWT authentication validated at API Gateway
- Redis stores JWT tokens with TTL auto-expiration
- Order Service implements Saga orchestrator for distributed transactions
- Both Order and Payment services persist data to PostgreSQL
- Saga state tracked in database for compensation and recovery

## Services

1. **API Gateway** (Port 8080) - Single entry point with JWT authentication, proxies all requests to internal services
2. **Order Service** (Internal) - Saga orchestrator, business logic, consumes Kafka events, persists orders and saga state to PostgreSQL
3. **Payment Service** (Internal) - Payment processing, persists payments to PostgreSQL
4. **Redis** (Port 6379) - JWT token storage with TTL auto-expiration

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
   curl -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username": "admin", "password": "admin"}'
   
   # Create an order (use token from login response)
   curl -X POST http://localhost:8080/api/orders \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer <your-token>" \
     -d '{"customerId": "123", "productId": "456", "quantity": 2, "amount": 100.00}'
   
   # Get order status
   curl http://localhost:8080/api/orders/1 \
     -H "Authorization: Bearer <your-token>"
   ```

5. **Access Services:**
   - **Swagger UI**: http://localhost:8080/swagger-ui/index.html (API Gateway only)
   - **Grafana**: http://localhost:3000 (admin/admin)
   - **Kafka UI**: http://localhost:8090
   - **Redis**: localhost:6379
   - **PostgreSQL**: localhost:5432 (user: postgres, password: postgres)

   **Note**: Order and Payment services are internal only and not directly accessible from outside.

### Stop the Demo

```bash
docker-compose down -v
```

## Event Flow

1. **API Gateway** receives HTTP request
2. **API Gateway** publishes `OrderCreated` event to Kafka
3. **Order Service** consumes the event
4. **Order Service** calls **Payment Service** via HTTP (with retry)
5. **Payment Service** checks for existing payment (idempotency)
6. **Payment Service** stores data in PostgreSQL
7. **Payment Service** publishes `PaymentProcessed` event (with retry)
8. Failed messages are sent to **dead-letter-queue** for manual review
9. All services emit logs, metrics, and traces to Grafana Agent
10. Grafana Agent forwards data to Loki (logs), Mimir (metrics), and Tempo (traces)

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
4. Traces show the complete request flow: API Gateway → Order Service → Payment Service

## Development

### Build Services Locally

```bash
# Build all services
./build-services.sh

# Or build individually
cd api-gateway && mvn clean package
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

Interactive API documentation available via Swagger UI at API Gateway:
- **Swagger UI**: http://localhost:8080/swagger-ui/index.html

All API requests must go through API Gateway. Direct access to Order and Payment services is not allowed.

### API Gateway Endpoints

| Method | Endpoint | Auth Required | Description |
|--------|----------|---------------|-------------|
| POST | `/api/auth/login` | No | Login and get JWT token |
| POST | `/api/orders` | Yes | Create new order |
| GET | `/api/orders/{id}` | Yes | Get order by ID |
| GET | `/actuator/health` | No | Health check |

### Payment Endpoints (via API Gateway)

| Method | Endpoint | Auth Required | Description |
|--------|----------|---------------|-------------|
| GET | `/api/payments/{id}` | Yes | Get payment details |

**Note**: All endpoints are accessed through API Gateway. Order and Payment services are internal only.

## Kafka Topics

Topics are automatically created on startup by the `kafka-init` container:

- **order-events** (3 partitions) - Order lifecycle events
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
   - Check if ports 8080-8082, 3000, 3100, 3200, 4317, 5432, 9092, 9411 are available
   - Modify `docker-compose.yml` if needed

6. **Metrics not showing in Grafana:**
   ```bash
   # Check Grafana Agent is scraping
   docker-compose logs grafana-agent | grep "scrape"
   
   # Verify Mimir is receiving data
   curl -H "X-Scope-OrgID: demo" http://localhost:9009/prometheus/api/v1/label/__name__/values
   ```

5. **Schema validation errors:**
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
# Load test with Apache Bench
ab -n 1000 -c 10 -H "Content-Type: application/json" \
   -p order-payload.json \
   http://localhost:8080/api/orders

# Monitor in Grafana during load test
```

## Production-Ready Features

| Feature | Status | Description |
|---------|--------|-------------|
| **JWT Authentication** | ✅ | Secure API endpoints with token-based authentication |
| **API Documentation** | ✅ | Interactive Swagger UI for all services |
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

For more details, see [docs/PRODUCTION-READY.md](docs/PRODUCTION-READY.md)

## Documentation

- [Production-Ready Features](docs/PRODUCTION-READY.md)
- [Saga Pattern Implementation](docs/SAGA-PATTERN.md)
- [Release Notes](docs/RELEASE-NOTES.md)

## Monitoring Circuit Breakers

Circuit breaker state can be monitored via actuator endpoints:

```bash
# Check circuit breaker status
curl http://localhost:8080/actuator/circuitbreakers

# Check circuit breaker events
curl http://localhost:8080/actuator/circuitbreakerevents

# Check health including circuit breakers
curl http://localhost:8080/actuator/health
```

**Circuit Breaker States:**
- **CLOSED**: Normal operation, requests pass through
- **OPEN**: Circuit is open, requests fail fast with fallback
- **HALF_OPEN**: Testing if service recovered, limited requests allowed

### Testing Circuit Breaker

Test circuit breaker state transitions:

```bash
# Get JWT token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin"}' | jq -r '.token')

# 1. Stop order-service to simulate failure
docker-compose stop order-service

# 2. Make 10 requests to trigger OPEN state
for i in {1..10}; do
  curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/orders/1
  sleep 0.3
done

# 3. Check state (should be OPEN)
curl http://localhost:8080/actuator/circuitbreakers | jq '.circuitBreakers.orderService.state'

# 4. Wait 10 seconds for HALF_OPEN transition
sleep 10

# 5. Check state (should be HALF_OPEN)
curl http://localhost:8080/actuator/circuitbreakers | jq '.circuitBreakers.orderService.state'

# 6. Restart order-service
docker-compose start order-service
sleep 8

# 7. Create order and make successful requests to close circuit
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"customerId": "test", "productId": "prod", "quantity": 1, "amount": 100.00}'

for i in {1..5}; do
  curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/orders/1
  sleep 0.5
done

# 8. Check state (should be CLOSED)
curl http://localhost:8080/actuator/circuitbreakers | jq '.circuitBreakers.orderService.state'
```

## Next Steps

1. Add authentication/authorization ✅ Completed in v1.1
2. Implement circuit breakers (Resilience4j) ✅ Completed in v1.2
3. Add saga patterns for distributed transactions ✅ Completed in v1.3
4. Implement API rate limiting
5. Add API versioning
6. Enhance monitoring with alerts and SLOs

## License

MIT License - See LICENSE file for details.
