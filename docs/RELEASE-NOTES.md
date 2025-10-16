# Release Notes

## v1.1 - Authentication & Single Entry Point Architecture (Current)

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
