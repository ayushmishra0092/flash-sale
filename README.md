# Flash Sale Inventory Engine

A high-performance inventory system designed to handle massive concurrent traffic during e-commerce flash sales without overselling.

## Architecture Overview

### The Problem

When 10000+ users compete for limited inventory simultaneously, traditional database-centric approaches fail due to:

- Race conditions causing overselling
- Database write bottlenecks
- Poor response times under load

### The Solution

This system uses a **three-layer architecture**:

1. **Redis with Lua Scripts** (Edge Layer)
   - Atomic inventory checks and decrements
   - Instantly filters out requests exceeding available stock
   - Sub-millisecond response times
   - Zero race conditions

2. **Apache Kafka** (Streaming Layer)
   - Decouples user traffic from database writes
   - Asynchronous order processing
   - Guaranteed event delivery
   - Horizontal scalability

3. **PostgreSQL** (Persistence Layer)
   - Permanent order records
   - Inventory reconciliation
   - Business intelligence data

### Traffic Flow

```
User Request → gRPC/REST → Redis Lua Script → Instant Response
                                ↓
                          (if successful)
                                ↓
                          Kafka Event → Background Processing → PostgreSQL
```

**Key Advantage**: Users get instant responses while database writes happen asynchronously in the background!

## Tech Stack

- **Java 17** with Spring Boot 3.2
- **Redis** for atomic inventory operations
- **Apache Kafka** for event streaming
- **PostgreSQL** for persistent storage
- **gRPC** for high-performance RPC
- **Docker** for containerization

## Getting Started

### Prerequisites

- Docker and Docker Compose
- Java 17+ (for local development)
- Maven 3.9+ (for local development)

### Quick Start with Docker

1. **Start all services**:

```bash
docker-compose up -d
```

2. **Check service health**:

```bash
docker-compose ps
```

3. **View logs**:

```bash
docker-compose logs -f app
```

### Local Development

1. **Start infrastructure services**:

```bash
docker-compose up -d postgres redis kafka zookeeper
```

2. **Run application locally**:

```bash
mvn spring-boot:run
```

## API Usage

### 1. Create a Product (Direct DB Insert for Demo)

First, insert a product directly into PostgreSQL:

```sql
INSERT INTO products (name, description, price, total_stock, available_stock, sale_start_time, sale_end_time, active, created_at, updated_at)
VALUES (
    'iPhone 15 Pro',
    'Limited flash sale - 100 units only!',
    999.99,
    100,
    100,
    NOW(),
    NOW() + INTERVAL '1 day',
    true,
    NOW(),
    NOW()
);
```

### 2. Initialize Redis Inventory

Before starting the flash sale, load inventory into Redis:

```bash
curl -X POST http://localhost:8080/api/flash-sale/inventory/init \
  -H "Content-Type: application/json" \
  -d '{"productId": 1}'
```

### 3. Book Inventory (Flash Sale Request)

```bash
curl -X POST http://localhost:8080/api/flash-sale/book \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "productId": 1,
    "quantity": 1
  }'
```

**Response**:

```json
{
  "success": true,
  "message": "Booking successful",
  "orderNumber": "FS-1705123456789-A1B2C3D4"
}
```

### 4. Check Inventory

```bash
curl http://localhost:8080/api/flash-sale/inventory/1
```

**Response**:

```json
{
  "productId": 1,
  "availableStock": 99
}
```

### 5. Check Order Status

```bash
curl http://localhost:8080/api/flash-sale/order/FS-1705123456789-A1B2C3D4
```

**Response**:

```json
{
  "orderNumber": "FS-1705123456789-A1B2C3D4",
  "userId": "user123",
  "productId": 1,
  "quantity": 1,
  "totalPrice": 999.99,
  "status": "CONFIRMED",
  "createdAt": "2024-01-13T10:30:45"
}
```

### Expected Results

- **No overselling**: Total confirmed orders ≤ available stock
- **Fast response times**: < 50ms for Redis layer
- **Zero race conditions**: Atomic Lua script guarantees

## Project Structure

```
src/main/java/com/flashsale/
├── config/           # Redis, Kafka, gRPC configuration
├── controller/       # REST API endpoints
├── dto/             # Data transfer objects
├── entity/          # JPA entities (Product, Order)
├── grpc/            # gRPC service implementation
├── repository/      # Database repositories
└── service/         # Business logic
    ├── InventoryService.java         # Core booking logic
    └── OrderProcessingService.java   # Kafka consumer

src/main/resources/
├── lua/             # Redis Lua scripts
│   ├── inventory_decrement.lua
│   └── inventory_check.lua
└── proto/           # gRPC protocol definitions
```
