# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

---

## [v1.4] - 2025-10-17

### Added
- **Spring Cloud Gateway Migration**
  - Replaced traditional API Gateway with Spring Cloud Gateway (order-gateway)
  - Reactive routing with WebFlux for non-blocking I/O
  - Built-in rate limiting with Redis backend
  - Gateway filters for request/response manipulation
  - Route-level circuit breakers with Resilience4j
  
- **Input Validation**
  - Bean Validation (Jakarta Validation) for request DTOs
  - OrderRequest validation: @NotBlank, @NotNull, @Min, @DecimalMin
  - Custom validation messages
  - OrderValidationFilter for gateway-level validation
  - HTTP 400 responses with detailed error messages
  
- **Enhanced Rate Limiting**
  - Spring Cloud Gateway RequestRateLimiter
  - Redis-backed distributed rate limiting
  - IP-based rate limiting with KeyResolver
  - Configurable: 5 requests/second, burst capacity 10
  - Per-route rate limit configuration
  
- **Comprehensive Test Suite**
  - test-comprehensive.sh with 42 test scenarios
  - 100% test coverage achieved
  - Test categories: Authentication (5), Order Creation (7), Order Retrieval (4), Payment (3), Health (2), Rate Limiting (2), Performance (4), Circuit Breaker (4), Saga Pattern (7), Edge Cases (4)
  - Automated validation of all positive and negative scenarios
  - Performance benchmarks (all < 100ms)
  - Circuit breaker state transitions and fallback testing
  - Saga orchestration flow, compensation, timeout, and idempotency testing
  - Security tests: SQL injection, XSS, special characters
  
- **New Components**
  - OrderValidationFilter: Gateway filter for order validation
  - ValidationConfig: Validator bean configuration
  - FallbackController: Health endpoint and circuit breaker fallbacks
  - RateLimiterConfig: IP-based key resolver

### Changed
- **Architecture**
  - Migrated from traditional Spring Boot API Gateway to Spring Cloud Gateway
  - Event publishing moved to gateway filter
  - Reactive programming model with Mono/Flux
  - Gateway routes configuration in application.yml
  
- **Order Creation Flow**
  - POST /api/orders now handled by OrderValidationFilter
  - Validation happens at gateway level before event publishing
  - Synchronous validation with immediate error responses
  - Event published to Kafka after successful validation
  
- **Rate Limiting**
  - Changed from Bucket4j to Spring Cloud Gateway RequestRateLimiter
  - From 100 req/min to 5 req/sec (burst 10)
  - Distributed across gateway instances via Redis
  - Per-route configuration support

### Removed
- api-gateway service (replaced by order-gateway)
- test-all-endpoints.sh (merged into test-comprehensive.sh)
- test-order-gateway-ratelimit.sh (merged into test-comprehensive.sh)
- OrderController and PaymentController (replaced by gateway routing)
- Bucket4j dependency
- Swagger UI (not compatible with reactive gateway)

### Technical Details
- **New Dependencies**: spring-cloud-starter-gateway, spring-boot-starter-data-redis-reactive, spring-boot-starter-validation, spring-cloud-starter-circuitbreaker-reactor-resilience4j
- **Spring Cloud Version**: 2022.0.4
- **Gateway Routes**: order-create-route (POST), order-route (GET), payment-route (GET)
- **Validation Annotations**: @NotBlank, @NotNull, @Min(1), @DecimalMin("0.01")
- **Filter Order**: JWT Auth (-100), Rate Limiter (default), Validation (custom)

### Testing Results
- All 42 tests passed (100% success rate)
- Authentication: 5/5 passed
- Order Creation: 7/7 passed (including validation tests)
- Order Retrieval: 4/4 passed
- Payment: 3/3 passed
- Health Checks: 2/2 passed
- Rate Limiting: 2/2 passed (15/20 requests blocked)
- Performance: 4/4 passed (avg 20ms response time)
- Circuit Breaker: 4/4 passed (state check, fallback, metrics)
- Saga Pattern: 7/7 passed (flow, compensation, timeout, idempotency)
- Edge Cases: 4/4 passed (safe handling of malicious input)

### Migration Notes
- Update any references from "api-gateway" to "order-gateway"
- Rate limit is now 5 req/sec (was 100 req/min)
- Validation errors return HTTP 400 with error message
- All endpoints remain the same (backward compatible)
- Swagger UI removed (use curl or Postman for testing)

---

## [v1.3] - 2025-10-17

### Added
- **Saga Timeout Handling**
  - Default 30-second timeout for saga transactions
  - Scheduled check every 5 seconds for expired sagas
  - Automatic compensation triggered for timed-out sagas
  - `timeout_at` field added to saga_state table
  - Configurable timeout duration

- **Saga Recovery on Service Restart**
  - @PostConstruct method for automatic recovery
  - Queries in-progress sagas (PROCESSING/STARTED status)
  - Automatic compensation for incomplete sagas
  - No lost transactions after service restart
  - @EnableScheduling annotation for scheduled tasks

- **Saga Monitoring Dashboard**
  - Real-time Grafana dashboard for saga visualization
  - Prometheus metrics: saga_total, saga_completed, saga_failed, saga_processing, saga_success_rate
  - 6 dashboard panels: Total, Completed, Failed, Success Rate, Time Series, Pie Chart
  - Metrics updated every 10 seconds
  - Dashboard auto-refresh every 10 seconds

- **Database Persistence for Orders**
  - Order entity with JPA annotations
  - OrderRepository for data access
  - Database-generated IDs for orders
  - Foreign key constraint: payments.order_id → orders.id
  - Orders table with fields: id, customer_id, product_id, quantity, amount, status, created_at, updated_at

- **New Services**
  - SagaOrchestrator: Manages saga lifecycle with timeout and recovery
  - SagaMetricsService: Exposes Prometheus metrics for monitoring

- **New Kafka Topic**
  - compensation-events (3 partitions) for saga rollback events

- **Documentation**
  - docs/SAGA-PATTERN.md with comprehensive saga documentation
  - Production-ready improvements section in README
  - Updated database schema with timeout_at field

### Changed
- **OrderService**: Now persists orders to PostgreSQL instead of in-memory storage
- **PaymentService**: Added cancelPayment method for compensation
- **PaymentController**: Added POST /payments/{id}/cancel endpoint
- **SagaState**: Added timeout_at field with 30-second default
- **SagaMetricsService**: Uses AtomicInteger for gauge values to prevent NaN

### Fixed
- Success rate metric displaying NaN (fixed with AtomicInteger)
- Foreign key constraint between payments and orders

### Removed
- order-payload.json (temporary test file)

### Technical Details
- **New Entities**: Order, SagaState (with timeout_at)
- **New Repositories**: OrderRepository, SagaStateRepository
- **New Services**: SagaOrchestrator, SagaMetricsService
- **Scheduled Tasks**: checkTimeouts() every 5s, updateMetrics() every 10s
- **Grafana Dashboard**: monitoring/grafana/dashboards/saga-dashboard.json
- **Micrometer Integration**: Prometheus metrics exposure

### Testing Results
- Positive case: 3 orders completed (60%)
- Negative case: 2 orders failed with compensation (40%)
- Recovery case: In-progress saga compensated on restart
- Timeout case: Expired sagas automatically compensated
- Dashboard: All metrics displaying correctly

---

## [v1.2] - 2025-10-16

### Added
- **Circuit Breaker Pattern** with Resilience4j
  - Circuit breaker for API Gateway → Order Service calls
  - Circuit breaker for Order Service → Payment Service calls
  - Automatic failure detection and circuit opening
  - Graceful fallback responses when circuit is open
  - Actuator endpoints for monitoring circuit breaker state
  - Configurable thresholds and timeouts

### Changed
- Enhanced resilience with automatic circuit breaking
- Improved error handling with fallback methods
- Added circuit breaker health indicators

### Technical Details
- **New Dependency**: spring-cloud-starter-circuitbreaker-resilience4j (3.0.3)
- **Configuration**: Sliding window size: 10, Failure threshold: 50%, Wait duration: 10s
- **Monitoring**: `/actuator/circuitbreakers` and `/actuator/circuitbreakerevents` endpoints
- **Fallback**: Returns HTTP 503 when circuit is open

---

## [v1.1] - 2025-10-16

### Added
- **JWT Authentication System**
  - Login endpoint with username/password authentication
  - JWT token generation and validation
  - Redis integration for token storage with TTL (24 hours)
  - Secure password encoding with BCrypt
  
- **Single Entry Point Architecture**
  - API Gateway as the only external access point (port 8080)
  - All client requests must go through API Gateway
  - Centralized JWT validation at API Gateway
  - Internal-only Order and Payment services (no external ports)
  
- **API Gateway Enhancements**
  - Payment proxy endpoint: `GET /api/payments/{id}`
  - Authentication controller with login endpoint
  - JWT authentication filter for request validation
  - Token service for Redis operations
  
- **Redis Integration**
  - Redis container for JWT token storage
  - Automatic token expiration based on TTL
  - RedisTemplate configuration for string serialization
  
- **Swagger/OpenAPI Documentation**
  - Interactive API documentation at API Gateway
  - JWT Bearer authentication scheme in Swagger UI
  - Request/response examples for all endpoints
  - Accessible at: http://localhost:8080/swagger-ui/index.html

### Changed
- **Architecture**
  - Removed port exposure for Order Service (was 8081)
  - Removed port exposure for Payment Service (was 8082)
  - All service-to-service communication remains internal
  
- **Security**
  - All `/api/orders` endpoints now require JWT authentication
  - All `/api/payments` endpoints now require JWT authentication
  - Public endpoints: `/api/auth/login`, `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`
  
- **Documentation**
  - Updated architecture diagram to show single entry point design
  - Removed Swagger UI from Order and Payment services
  - Updated all API endpoint documentation
  - Added Redis to infrastructure components

### Removed
- Direct external access to Order Service
- Direct external access to Payment Service
- Swagger dependencies from Order and Payment services
- Swagger configuration files from internal services

### Technical Details
- **New Dependencies**: spring-boot-starter-security, jjwt (0.11.5), springdoc-openapi (2.2.0), spring-boot-starter-data-redis
- **New Classes**: JwtUtil, JwtAuthenticationFilter, TokenService, SecurityConfig, RedisConfig, AuthController, PaymentController
- **New DTOs**: AuthRequest, AuthResponse
- **Configuration**: JWT secret, Redis connection, Swagger OpenAPI settings

### Migration Notes
- All API clients must now authenticate via `/api/auth/login` before accessing protected endpoints
- Include JWT token in Authorization header: `Bearer <token>`
- Update any direct calls to Order/Payment services to go through API Gateway
- Default credentials: username=admin, password=admin

---

## [v1.0] - 2025-10-16

### Added
- Initial release with event-driven microservices architecture
- Three microservices: API Gateway, Order Service, Payment Service
- Apache Kafka message broker with KRaft mode
- PostgreSQL database with optimized schema
- LGTM observability stack (Loki, Grafana, Tempo, Mimir)
- Grafana Agent for unified metrics/logs/traces collection
- Production-ready patterns: idempotency, retry mechanism, DLQ, error handling
- Sequential container startup with health check dependencies
- Auto-created Kafka topics on startup
- Comprehensive monitoring dashboards
- Docker Compose orchestration for entire stack
