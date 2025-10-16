# Changelog

All notable changes to this project will be documented in this file.

## [v1.1] - TBD

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
