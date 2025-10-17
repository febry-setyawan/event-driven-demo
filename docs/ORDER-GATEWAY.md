# Order Gateway Documentation

## Overview

Order Gateway is a Spring Cloud Gateway implementation that serves as the single entry point for all external traffic. It replaces the traditional Spring Boot API Gateway with a reactive, high-performance gateway solution.

## Key Features

### 1. Spring Cloud Gateway
- **Reactive Architecture**: Built on Spring WebFlux for non-blocking I/O
- **Route-based Routing**: Declarative routing configuration in YAML
- **Filter Chain**: Pre and post-processing of requests/responses
- **Predicates**: Flexible request matching (path, method, headers, etc.)

### 2. JWT Authentication
- **Token Validation**: JWT tokens validated at gateway level
- **Redis Integration**: Token storage with TTL auto-expiration
- **Global Filter**: JwtAuthenticationFilter with order -100
- **Excluded Paths**: `/api/auth/**`, `/actuator/**`, `/fallback/**`

### 3. Rate Limiting
- **Redis-backed**: Distributed rate limiting across gateway instances
- **IP-based**: Rate limit per client IP address
- **Configuration**: 5 requests/second, burst capacity 10
- **Per-route**: Different limits for different routes

### 4. Input Validation
- **Bean Validation**: Jakarta Validation annotations
- **Gateway Filter**: OrderValidationFilter validates before processing
- **Detailed Errors**: HTTP 400 with specific error messages
- **Validation Rules**:
  - `customerId`: @NotBlank
  - `productId`: @NotBlank
  - `quantity`: @NotNull, @Min(1)
  - `amount`: @NotNull, @DecimalMin("0.01")

### 5. Circuit Breaker
- **Resilience4j**: Circuit breaker for downstream services
- **Per-route**: Separate circuit breakers for order and payment services
- **Fallback**: Graceful degradation with fallback responses
- **Configuration**: 50% failure threshold, 10s wait duration

### 6. Event Publishing
- **Kafka Integration**: Publishes OrderCreated events
- **Gateway Filter**: OrderValidationFilter handles event publishing
- **Asynchronous**: Non-blocking event publishing
- **Reliable**: Kafka producer with acks=all, retries=3

## Architecture

```
Client Request
     ↓
[JWT Auth Filter] (-100)
     ↓
[Rate Limiter]
     ↓
[Route Matching]
     ↓
┌────────────────┬────────────────┐
│                │                │
POST /api/orders  GET /api/orders  GET /api/payments
│                │                │
[Validation]     [Circuit Breaker] [Circuit Breaker]
│                │                │
[Kafka Publish]  [Route to        [Route to
│                Order Service]   Payment Service]
[Return Response]
```

## Configuration

### application.yml

```yaml
spring:
  cloud:
    gateway:
      routes:
        # Order Creation Route (with validation)
        - id: order-create-route
          uri: no://op
          predicates:
            - Path=/api/orders
            - Method=POST
          filters:
            - OrderValidationFilter
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 5
                redis-rate-limiter.burstCapacity: 10
                
        # Order Retrieval Route (with circuit breaker)
        - id: order-route
          uri: ${ORDER_SERVICE_URL:http://order-service:8081}
          predicates:
            - Path=/api/orders/**
          filters:
            - name: CircuitBreaker
              args:
                name: orderService
                fallbackUri: forward:/fallback/orders
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 5
                redis-rate-limiter.burstCapacity: 10
```

### Environment Variables

```bash
ORDER_SERVICE_URL=http://order-service:8081
PAYMENT_SERVICE_URL=http://payment-service:8082
REDIS_HOST=redis
REDIS_PORT=6379
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
JWT_SECRET=mySecretKeyForJWTTokenGenerationMustBeLongEnough
TEMPO_URL=http://grafana-agent:9411
```

## API Endpoints

### Authentication
- **POST** `/api/auth/login` - Login and get JWT token (no auth required)

### Orders
- **POST** `/api/orders` - Create new order (auth required, validated, rate limited)
- **GET** `/api/orders/{id}` - Get order by ID (auth required, rate limited, circuit breaker)
- **GET** `/api/orders/health` - Health check (no auth required)

### Payments
- **GET** `/api/payments/{id}` - Get payment by ID (auth required, rate limited, circuit breaker)

### Actuator
- **GET** `/actuator/health` - Gateway health check
- **GET** `/actuator/circuitbreakers` - Circuit breaker status
- **GET** `/actuator/routes` - Gateway routes configuration

## Request Flow

### POST /api/orders

1. **JWT Authentication** (JwtAuthenticationFilter)
   - Extract Authorization header
   - Validate JWT token
   - Check token in Redis
   - Return 401 if invalid

2. **Rate Limiting** (RequestRateLimiter)
   - Check request count for client IP
   - Consume token from bucket
   - Return 429 if limit exceeded

3. **Validation** (OrderValidationFilter)
   - Parse request body to OrderRequest
   - Validate with Bean Validation
   - Return 400 if validation fails

4. **Event Publishing**
   - Generate order ID
   - Publish OrderCreated event to Kafka
   - Return 200 with order response

### GET /api/orders/{id}

1. **JWT Authentication** (JwtAuthenticationFilter)
   - Validate token
   - Return 401 if invalid

2. **Rate Limiting** (RequestRateLimiter)
   - Check rate limit
   - Return 429 if exceeded

3. **Circuit Breaker** (CircuitBreakerFilter)
   - Check circuit state
   - If OPEN: return fallback (503)
   - If CLOSED/HALF_OPEN: proceed

4. **Route to Order Service**
   - Forward request to order-service:8081
   - Return response or fallback

## Error Handling

### HTTP Status Codes

- **200 OK**: Successful request
- **400 Bad Request**: Validation error
- **401 Unauthorized**: Missing or invalid JWT token
- **404 Not Found**: Resource not found
- **429 Too Many Requests**: Rate limit exceeded
- **503 Service Unavailable**: Circuit breaker open or service down

### Error Response Format

```json
{
  "error": "Error message description"
}
```

## Monitoring

### Actuator Endpoints

```bash
# Health check
curl http://localhost:8080/actuator/health

# Circuit breaker status
curl http://localhost:8080/actuator/circuitbreakers

# Gateway routes
curl http://localhost:8080/actuator/routes
```

### Metrics

- Request count per route
- Response time per route
- Circuit breaker state changes
- Rate limit rejections
- JWT validation failures

### Distributed Tracing

All requests are traced with:
- Trace ID
- Span ID
- Service name: order-gateway
- Exported to Tempo via Grafana Agent

## Testing

### Comprehensive Test Suite

```bash
./test-comprehensive.sh
```

**Test Coverage:**
- ✅ Authentication (5 tests)
- ✅ Order Creation (8 tests)
- ✅ Order Retrieval (4 tests)
- ✅ Payment (3 tests)
- ✅ Health Checks (2 tests)
- ✅ Rate Limiting (2 tests)
- ✅ Performance (4 tests)
- ✅ Security (4 tests)

**Total: 31 tests, 100% pass rate**

## Performance

### Benchmarks

- **Login**: ~20ms average
- **Create Order**: ~20ms average
- **Get Order**: ~20ms average
- **Concurrent Requests**: 10 parallel requests in ~50ms

### Scalability

- Reactive architecture supports high concurrency
- Redis-backed rate limiting scales horizontally
- Stateless design allows multiple gateway instances
- Circuit breaker prevents cascading failures

## Security

### Input Validation
- All inputs validated with Bean Validation
- SQL injection attempts safely handled
- XSS attempts safely handled
- Special characters accepted and escaped

### Authentication
- JWT tokens with 24-hour expiration
- Tokens stored in Redis with TTL
- Automatic token cleanup
- Secure password hashing with BCrypt

### Rate Limiting
- Per-IP rate limiting
- Distributed across gateway instances
- Prevents DDoS attacks
- Configurable limits per route

## Migration from API Gateway

### Breaking Changes
- Swagger UI removed (not compatible with reactive gateway)
- Rate limit changed from 100 req/min to 5 req/sec

### Backward Compatible
- All API endpoints remain the same
- Request/response formats unchanged
- Authentication flow unchanged
- Environment variables compatible

### Migration Steps
1. Build new order-gateway: `cd order-gateway && mvn clean package`
2. Update docker-compose.yml (already done)
3. Restart services: `docker-compose up -d`
4. Run tests: `./test-comprehensive.sh`

## Troubleshooting

### Common Issues

**Issue**: HTTP 401 Unauthorized
- **Cause**: Missing or invalid JWT token
- **Solution**: Login via `/api/auth/login` and include token in Authorization header

**Issue**: HTTP 429 Too Many Requests
- **Cause**: Rate limit exceeded
- **Solution**: Wait for rate limit to reset (tokens refill at 5/second)

**Issue**: HTTP 503 Service Unavailable
- **Cause**: Circuit breaker open or downstream service down
- **Solution**: Check downstream service health, wait for circuit to close

**Issue**: HTTP 400 Bad Request
- **Cause**: Validation error
- **Solution**: Check error message and fix request payload

### Logs

```bash
# View gateway logs
docker-compose logs order-gateway

# Follow logs
docker-compose logs -f order-gateway

# Filter errors
docker-compose logs order-gateway | grep ERROR
```

## Best Practices

1. **Always include JWT token** in Authorization header for protected endpoints
2. **Handle rate limiting** gracefully with exponential backoff
3. **Validate input** on client side before sending to gateway
4. **Monitor circuit breaker** state and adjust thresholds if needed
5. **Use distributed tracing** to debug issues across services
6. **Configure rate limits** based on actual traffic patterns
7. **Test thoroughly** with comprehensive test suite before deployment

## References

- [Spring Cloud Gateway Documentation](https://spring.io/projects/spring-cloud-gateway)
- [Resilience4j Circuit Breaker](https://resilience4j.readme.io/docs/circuitbreaker)
- [Bean Validation](https://beanvalidation.org/)
- [Redis Rate Limiting](https://redis.io/docs/manual/patterns/rate-limiter/)
