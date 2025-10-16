# Release Notes

## v1.3 - Saga Pattern Improvements (Current)

**Release Date:** 2025-10-17

### New Features
- **Saga Timeout Handling**: Automatic compensation for timed-out sagas
  - Default timeout: 30 seconds from saga creation
  - Scheduled check every 5 seconds for expired sagas
  - Automatic compensation triggered for timed-out transactions
  - Configurable timeout duration via SagaState constructor

- **Saga Recovery on Service Restart**: Automatic recovery of in-progress sagas
  - @PostConstruct method runs on service startup
  - Queries all sagas with status PROCESSING or STARTED
  - Automatically triggers compensation for incomplete sagas
  - Ensures no lost transactions after service restart

- **Saga Monitoring Dashboard**: Real-time Grafana visualization
  - Prometheus metrics: saga_total, saga_completed, saga_failed, saga_processing, saga_success_rate
  - Metrics updated every 10 seconds via scheduled task
  - 6 dashboard panels: Total, Completed, Failed, Success Rate, Time Series, Pie Chart
  - Dashboard auto-refresh every 10 seconds

- **Database Persistence for Orders**: Orders now persisted to PostgreSQL
  - Order entity with JPA annotations
  - OrderRepository for data access
  - Database-generated IDs instead of API Gateway IDs
  - Foreign key relationship maintained between payments and orders

### Implementation
- **SagaOrchestrator**: Enhanced with timeout checking and recovery methods
  - `checkTimeouts()`: @Scheduled method runs every 5 seconds
  - `recoverSagas()`: @PostConstruct method for startup recovery
  - Compensation logic for failed/timed-out sagas

- **SagaMetricsService**: Exposes Prometheus metrics for monitoring
  - Uses AtomicInteger for gauge values to prevent NaN
  - Scheduled update every 10 seconds
  - Metrics: total, completed, failed, processing, success_rate

- **Database Schema Updates**:
  - Added `timeout_at` field to saga_state table
  - Added Order entity with fields: id, customer_id, product_id, quantity, amount, status, created_at, updated_at
  - Restored foreign key constraint: payments.order_id → orders.id

- **Kafka Topics**: Added compensation-events topic (3 partitions)

### Configuration
- **@EnableScheduling**: Added to OrderServiceApplication for scheduled tasks
- **Micrometer**: Integrated for Prometheus metrics exposure
- **Grafana Dashboard**: saga-dashboard.json with 6 monitoring panels

### Testing Results
- Positive case: 3 orders completed successfully (60%)
- Negative case: 2 orders failed with compensation (40%)
- Recovery case: In-progress saga compensated on service restart
- Timeout case: Expired sagas automatically compensated
- Dashboard: All metrics displaying correctly with 60% success rate

### Technical Changes
- Added entities: Order, SagaState (with timeout_at field)
- Added repositories: OrderRepository, SagaStateRepository
- Added services: SagaOrchestrator, SagaMetricsService
- Updated OrderService to persist orders to database
- Updated PaymentService with cancelPayment method
- Updated PaymentController with cancel endpoint
- Added Grafana dashboard: monitoring/grafana/dashboards/saga-dashboard.json
- Updated README with saga improvements documentation
- Removed temporary files: order-payload.json

### Documentation
- New file: docs/SAGA-PATTERN.md with comprehensive saga documentation
- Updated README with saga improvements section
- Updated database schema documentation with timeout_at field
- Updated Grafana dashboards section

---

## v1.2 - Circuit Breaker Pattern

**Release Date:** 2025-10-16

### New Features
- **Circuit Breaker with Resilience4j Reactor**: Prevent cascading failures in reactive WebClient calls
  - Automatic failure detection and circuit opening
  - Graceful degradation with fallback responses
  - Self-healing with automatic half-open state transition
  - Configurable failure thresholds and timeouts

### Implementation
- **API Gateway**: Circuit breaker for calls to Order Service and Payment Service using Resilience4j Reactor
  - `GET /api/orders/{id}` - Circuit breaker for Order Service
  - `GET /api/payments/{id}` - Circuit breaker for Payment Service
- **Configuration**: 50% failure threshold, 10s wait duration, 10 calls sliding window, 5 minimum calls
- **Fallback**: Returns HTTP 503 Service Unavailable when circuit is open
- **Reactive Support**: Uses `CircuitBreakerOperator.of()` with `.transformDeferred()` for Mono/Flux

### Circuit Breaker States
- **CLOSED**: Normal operation, all requests pass through
- **OPEN**: Circuit trips after failure threshold, requests fail fast with fallback
- **HALF_OPEN**: After wait duration, allows limited requests to test service recovery

### Monitoring
- Circuit breaker state via actuator endpoints: `/actuator/circuitbreakers`
- Circuit breaker events: `/actuator/circuitbreakerevents`
- Health indicators for circuit breaker status
- Event tracking for circuit state changes

### Testing
- Complete test coverage for all three states (CLOSED → OPEN → HALF_OPEN → CLOSED)
- Test using actual endpoints with authentication
- Documented test procedure in README.md

### Technical Changes
- Added dependencies:
  - spring-cloud-starter-circuitbreaker-resilience4j (3.0.3)
  - resilience4j-reactor (2.0.2)
- Reactive circuit breaker using `CircuitBreakerOperator` and `transformDeferred()`
- Enhanced actuator endpoints: circuitbreakers, circuitbreakerevents
- Updated SecurityConfig to allow test endpoint without authentication

---

## v1.1 - Authentication & Single Entry Point Architecture

**Release Date:** 2025-10-16

**Release Date:** TBD

### New Features
- **JWT Authentication**: Secure API endpoints with JWT token-based authentication
  - Login endpoint: `POST /api/auth/login`
  - In-memory user store (username: admin, password: admin)
  - Token expiration: 24 hours
  - Redis integration for token storage with TTL auto-expiration
- **Single Entry Point Architecture**: API Gateway as the only external access point
  - All client traffic must go through API Gateway (port 8080)
  - Order and Payment services are internal only (no external ports)
  - JWT validation centralized at API Gateway
- **Swagger/OpenAPI Documentation**: Interactive API documentation at API Gateway
  - Swagger UI: http://localhost:8080/swagger-ui/index.html
  - All endpoints documented with request/response examples
- **Redis Integration**: JWT token storage with automatic expiration

### Technical Changes
- Added dependencies: spring-boot-starter-security, jjwt (0.11.5), springdoc-openapi (2.2.0), spring-boot-starter-data-redis
- New packages: security/, config/, service/
- New classes: JwtUtil, JwtAuthenticationFilter, TokenService, SecurityConfig, RedisConfig
- New DTOs: AuthRequest, AuthResponse
- Protected endpoints require Bearer token in Authorization header
- Removed port exposure for Order and Payment services

### Architecture Changes
- **API Gateway**: Single entry point with JWT authentication and request proxying
- **Order Service**: Internal only, no authentication required
- **Payment Service**: Internal only, no authentication required
- **Redis**: Added for JWT token storage (port 6379)

### API Changes
- All `/api/orders` and `/api/payments` endpoints now require authentication
- New public endpoints: `/api/auth/login`, `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`
- Payment proxy endpoint: `GET /api/payments/{id}` via API Gateway

### Documentation
- Updated architecture diagram showing single entry point design
- Added Redis to infrastructure components
- Removed direct access documentation for Order/Payment services
- Updated all endpoint tables to reflect API Gateway routing

---

## v1.0 - Initial Release

**Release Date:** 2025-10-16

### Features
- **Microservices Architecture**: 3 independent services (API Gateway, Order Service, Payment Service)
- **Event-Driven Communication**: Asynchronous messaging via Apache Kafka
- **LGTM Observability Stack**: Loki, Grafana, Tempo, Mimir for complete observability
- **Production-Ready Patterns**:
  - Idempotency: Prevents duplicate payments
  - Retry Mechanism: HTTP calls and event publishing with exponential backoff
  - Dead Letter Queue: Failed message handling
  - Error Handling: Comprehensive exception handling
  - Sequential Startup: Health check-based container dependencies
  - Auto-create Topics: Kafka topics created automatically on startup

### Infrastructure
- **Kafka**: Message broker with 3 topics (order-events, payment-events, dead-letter-queue)
- **PostgreSQL**: Database with optimized schema and indexes
- **Docker Compose**: Single-command deployment
- **Grafana Agent**: Unified metrics, logs, and traces collection

### Monitoring
- Distributed tracing across all services
- Centralized log aggregation
- Pre-configured Grafana dashboards
- Health check endpoints

### Technical Stack
- Java 17
- Spring Boot 3.1.5
- Apache Kafka 7.4.0
- PostgreSQL 15
- Grafana Stack (Loki 2.9.0, Tempo 2.2.0, Mimir 2.10.0)
