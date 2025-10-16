# Event-Driven Architecture POC Demo

## Overview

This POC demonstrates a complete Event-Driven Architecture using Spring Boot microservices with Kafka as message broker, PostgreSQL as database, and LGTM stack for monitoring.

## Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   API Gateway   │───▶│  Order Service  │───▶│ Payment Service │
│   (Port 8080)   │    │   (Port 8081)   │    │   (Port 8082)   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 ▼
                    ┌─────────────────────────┐
                    │    Kafka Cluster        │
                    │  (order-events topic)   │
                    └─────────────────────────┘
                                 │
                                 ▼
                    ┌─────────────────────────┐
                    │     PostgreSQL          │
                    │    (Port 5432)          │
                    └─────────────────────────┘
```

## Services

1. **API Gateway** (Port 8080) - Entry point, routes requests and publishes events
2. **Order Service** (Port 8081) - Business logic, consumes events, calls Payment Service
3. **Payment Service** (Port 8082) - Database operations, connects to PostgreSQL

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
   # Create an order
   curl -X POST http://localhost:8080/api/orders \
     -H "Content-Type: application/json" \
     -d '{"customerId": "123", "productId": "456", "quantity": 2, "amount": 100.00}'
   
   # Get order status
   curl http://localhost:8080/api/orders/1
   ```

5. **Access Monitoring:**
   - Grafana: http://localhost:3000 (admin/admin)
   - Kafka UI: http://localhost:8090
   - PostgreSQL: localhost:5432 (user: postgres, password: postgres)

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

## API Documentation

### API Gateway Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/orders` | Create new order |
| GET | `/api/orders/{id}` | Get order by ID |
| GET | `/actuator/health` | Health check |

### Order Service Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/orders/{id}` | Get order details |
| GET | `/actuator/health` | Health check |

### Payment Service Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/payments` | Process payment |
| GET | `/payments/{id}` | Get payment details |
| GET | `/actuator/health` | Health check |

## Kafka Topics

Topics are automatically created on startup by the `kafka-init` container:

- **order-events** (3 partitions) - Order lifecycle events
- **payment-events** (3 partitions) - Payment processing events
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

This POC implements several production-ready patterns:

✅ **Idempotency** - Prevents duplicate payments via database unique constraint and application-level checks
✅ **Retry Mechanism** - HTTP calls and event publishing retry with exponential backoff (3 attempts)
✅ **Dead Letter Queue** - Failed messages captured with full error metadata for debugging
✅ **Error Handling** - Comprehensive exception handling with structured logging
✅ **Sequential Startup** - Services start in correct order with health check dependencies
✅ **Auto-create Topics** - Kafka topics created automatically on startup
✅ **Database Indexes** - Optimized queries with proper indexes
✅ **Distributed Tracing** - End-to-end request tracking across all services
✅ **Structured Logging** - Parameterized logs with trace IDs for correlation
✅ **Health Checks** - Actuator endpoints with Docker healthcheck integration

For more details, see [PRODUCTION-READY.md](PRODUCTION-READY.md)

## Next Steps

1. Add authentication/authorization
2. Implement circuit breakers (Resilience4j)
3. Add saga patterns for distributed transactions
4. Implement API rate limiting
5. Add API versioning
6. Enhance monitoring with alerts and SLOs

## License

MIT License - See LICENSE file for details.
