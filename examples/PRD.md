# Product Requirements Document: Order & Product Services

## Overview

This project is a reference implementation demonstrating a microservices architecture using Spring Boot, Kafka, and PostgreSQL. The system consists of two services that communicate via REST and asynchronous messaging.

**Purpose**: Illustrative example for demonstrating microservice patterns including service-to-service communication, event-driven architecture, and database-per-service.

---

## Technical Requirements

| Technology | Version/Specification |
|------------|----------------------|
| Java | 23 LTS |
| Spring Boot | 4.x |
| Build Tool | Maven |
| Database | PostgreSQL (one instance per service) |
| Message Broker | Apache Kafka |
| Spring Kafka | Latest compatible with Spring Boot 4 |
| Spring Data JPA | Latest compatible with Spring Boot 4 |

---

## Project Structure

```
/examples
├── order-service/
│   ├── pom.xml
│   └── src/
├── product-service/
│   ├── pom.xml
│   └── src/
└── README.md
```

---

## Service Specifications

### Service A: Order Service

**Responsibility**: Manages customer orders

#### Database Schema

Table: `orders`

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | Primary Key |
| product_id | UUID | Not Null |
| quantity | INTEGER | Not Null, > 0 |
| status | VARCHAR(50) | Not Null, Default 'PENDING' |
| created_at | TIMESTAMP | Not Null |

#### REST API Endpoints

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | /orders | Create new order | `{ "productId": "uuid", "quantity": 3 }` | Order object, 201 |
| GET | /orders/{id} | Get order by ID | — | Order object, 200 |
| GET | /orders | List all orders | — | List of orders, 200 |

#### Kafka Producer

- **Topic**: `order-events`
- **Event**: `OrderPlaced`
- **Payload**:
```json
{
  "eventType": "OrderPlaced",
  "orderId": "uuid",
  "productId": "uuid",
  "quantity": 3,
  "timestamp": "ISO-8601"
}
```

#### REST Client (Outbound)

- Calls Product Service `GET /products/{id}` to validate product exists before creating order
- If product does not exist, return 400 Bad Request

---

### Service B: Product Service

**Responsibility**: Manages product catalog and stock levels

#### Database Schema

Table: `products`

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | Primary Key |
| name | VARCHAR(255) | Not Null |
| stock_count | INTEGER | Not Null, >= 0 |
| created_at | TIMESTAMP | Not Null |

#### REST API Endpoints

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | /products | Create new product | `{ "name": "Widget", "stockCount": 100 }` | Product object, 201 |
| GET | /products/{id} | Get product by ID | — | Product object, 200 |
| GET | /products | List all products | — | List of products, 200 |

#### Kafka Consumer

- **Topic**: `order-events`
- **Listens for**: `OrderPlaced`
- **Action**: Decrement `stock_count` by the order quantity
- **Error handling**: Log error if insufficient stock (do not throw)

---

## Infrastructure (Docker Compose)

The `docker-compose.yml` must provide:

1. **Kafka** (with Zookeeper or KRaft mode)
   - Exposed port: 9092

2. **PostgreSQL for Order Service**
   - Database name: `orderdb`
   - Port: 5432
   - Credentials: `order_user` / `order_pass`

3. **PostgreSQL for Product Service**
   - Database name: `productdb`
   - Port: 5433
   - Credentials: `product_user` / `product_pass`

---

## Configuration

### Order Service (`application.yml`)

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/orderdb
    username: order_user
    password: order_pass
  jpa:
    hibernate:
      ddl-auto: update
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

product-service:
  base-url: http://localhost:8082
```

### Product Service (`application.yml`)

```yaml
server:
  port: 8082

spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/productdb
    username: product_user
    password: product_pass
  jpa:
    hibernate:
      ddl-auto: update
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: product-service
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
```

---

## Implementation Requirements

Important: Both services must use the spring boot 'exmaple-service' placed in the folder 'reference-example-service' next to the two services.
The 'exmaple-service' contains the relevant maven deps
* update the package name from the 'exmaple-service' names for the new services to append the name of the new service. For instance. Order service will have package name org.testpods.exmaple.product.
* The same strategy goes for the pom file relevant values.

### Order Service Components

1. **Entity**: `Order` JPA entity
2. **Repository**: `OrderRepository` extends `JpaRepository`
3. **DTO**: `CreateOrderRequest`, `OrderResponse`, `OrderPlacedEvent`
4. **Service**: `OrderService` with business logic
5. **Controller**: `OrderController` REST endpoints
6. **Kafka Producer**: `OrderEventPublisher`
7. **REST Client**: `ProductServiceClient` using `RestClient` or `WebClient`

### Product Service Components

1. **Entity**: `Product` JPA entity
2. **Repository**: `ProductRepository` extends `JpaRepository`
3. **DTO**: `CreateProductRequest`, `ProductResponse`, `OrderPlacedEvent`
4. **Service**: `ProductService` with business logic
5. **Controller**: `ProductController` REST endpoints
6. **Kafka Consumer**: `OrderEventListener`

---

## Acceptance Criteria

### Functional

- [ ] Can create a product via POST /products
- [ ] Can retrieve a product via GET /products/{id}
- [ ] Can create an order via POST /orders (validates product exists)
- [ ] Creating an order with non-existent product returns 400
- [ ] Creating an order publishes `OrderPlaced` event to Kafka
- [ ] Product Service consumes `OrderPlaced` and decrements stock
- [ ] Can verify stock decremented via GET /products/{id}

### Technical

- [ ] Both services start without errors
- [ ] Docker Compose brings up all infrastructure
- [ ] Services connect to their respective PostgreSQL databases
- [ ] Kafka topic `order-events` is created automatically
- [ ] Services use Spring Boot 4 and Java 21

---

## Example Flow (Manual Testing)

```bash
# 1. Start infrastructure
This must be done from the IDE

# 2. Start both services
This must be done from the IDE

# 3. Create a product
curl -X POST http://localhost:8082/products \
  -H "Content-Type: application/json" \
  -d '{"name": "Widget", "stockCount": 100}'
# Response: {"id": "abc-123", "name": "Widget", "stockCount": 100, ...}

# 4. Create an order
curl -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"productId": "abc-123", "quantity": 3}'
# Response: {"id": "def-456", "productId": "abc-123", "quantity": 3, "status": "PENDING", ...}

# 5. Verify stock decremented
curl http://localhost:8082/products/abc-123
# Response: {"id": "abc-123", "name": "Widget", "stockCount": 97, ...}
```

---

## Out of Scope

- Authentication / Authorization
- API Gateway
- Service Discovery
- Distributed Tracing
- Unit and Integration Tests (can be added later)
- CI/CD Pipeline
- Production-ready error handling
- Retry mechanisms / Dead Letter Queue
