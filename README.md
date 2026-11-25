# E-Commerce Product Catalog Service

A read-optimized RESTful backend service for an e-commerce product catalog built with **Java 21**, **Spring Boot 3.3**, **PostgreSQL 16**, and **Redis 7**.

---

## Architecture & Design Decisions

### 1. Datastore Selection: PostgreSQL + Redis
- **Why PostgreSQL?** A catalog of a few hundred thousand products easily fits on a single PostgreSQL node with single-digit millisecond query latency when properly indexed. E-commerce catalogs demand strong ACID guarantees for pricing and inventory mutations, strict schema validation, and efficient multi-attribute filtering (`category` + `price` range + deterministic ordering).
- **Why Redis (Cache-Aside)?** Reads vastly outnumber writes:
  - **Point lookups (`GET /api/v1/products/{id}`):** Cached in Redis with a 15-minute TTL and explicitly updated/evicted (`@CachePut` / `@CacheEvict`) on `PUT` and `DELETE` so clients never see stale prices.
  - **Catalog list/filter pages (`GET /api/v1/products`):** Cached with a short 60-second TTL to absorb traffic spikes on popular category landing pages without complex multi-key invalidation.
  - **Graceful Degradation:** A custom `CacheErrorHandler` logs Redis errors and transparently falls back to PostgreSQL so a cache outage never causes HTTP 500s.

### 2. Indexing Strategy
Because writes are infrequent relative to reads, we optimize aggressively for read paths using **partial composite B-tree indexes** (`WHERE active = TRUE`):
```sql
CREATE INDEX idx_products_active_cat_price_id
    ON products (category, price, id)
    WHERE active = TRUE;
```
- Satisfies equality filtering on `category`, range filtering on `price`, and deterministic ordering by `(price ASC, id ASC)` in a single index scan without an in-memory sort.
- Partial index excludes soft-deleted items, keeping the index compact in memory.

### 3. Keyset (Cursor) Pagination vs. `OFFSET`
- Traditional `OFFSET` pagination degrades linearly (`O(N)`) as users paginate deeper because the database must scan and discard skipped rows.
- This API encodes `(lastPrice, lastId)` into a URL-safe Base64 cursor and executes a keyset seek:
  ```sql
  WHERE (price > :lastPrice) OR (price = :lastPrice AND id > :lastId)
  ORDER BY price ASC, id ASC
  LIMIT :limit + 1
  ```
- Fetching `limit + 1` rows determines `hasMore` without running an expensive `SELECT COUNT(*)` query across hundreds of thousands of rows.

### 4. Concurrency, Data Integrity & Soft Deletes
- **Optimistic Locking (`@Version`):** Prevents lost updates when concurrent requests modify the same product, returning `409 Conflict` on version mismatch.
- **Soft Deletes (`active = false`):** Preserves referential integrity for historical orders and analytics while hiding deleted items from all catalog endpoints.

---

## Quick Start

### Prerequisites
- Docker & Docker Compose (or Java 21 + Maven 3.9+)

### Run the Entire Stack (App + Postgres + Redis)
```bash
docker compose up --build
```
On startup, Flyway applies database migrations and `CatalogDataSeeder` populates **10,000 sample products** across 5 categories (`running-shoes`, `apparel`, `accessories`, `basketball`, `training`) so you can immediately test filtering and cursor pagination at scale.

---

## API Reference & Example Requests

### 1. Create a Product
```bash
curl -i -X POST http://localhost:8080/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "NIKE-VOMERO-17",
    "name": "Zoom Vomero 17",
    "description": "Max-cushion road running shoe",
    "category": "running-shoes",
    "price": 159.99,
    "stockQuantity": 250
  }'
```

### 2. Retrieve a Product by ID
```bash
curl -i http://localhost:8080/api/v1/products/<PRODUCT_UUID>
```

### 3. List & Filter Products with Cursor Pagination
```bash
# First page: running-shoes between $50 and $180
curl -s "http://localhost:8080/api/v1/products?category=running-shoes&minPrice=50&maxPrice=180&limit=10"

# Next page: pass nextCursor from previous response
curl -s "http://localhost:8080/api/v1/products?category=running-shoes&minPrice=50&maxPrice=180&limit=10&cursor=<NEXT_CURSOR>"
```

### 4. Update a Product
```bash
curl -i -X PUT http://localhost:8080/api/v1/products/<PRODUCT_UUID> \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "NIKE-VOMERO-17",
    "name": "Zoom Vomero 17",
    "description": "Max-cushion road running shoe - Sale",
    "category": "running-shoes",
    "price": 134.99,
    "stockQuantity": 210
  }'
```

### 5. Delete a Product (Soft Delete)
```bash
curl -i -X DELETE http://localhost:8080/api/v1/products/<PRODUCT_UUID>
```

---

## Production Evolution (Next Steps)
If traffic and catalog complexity grow beyond a few hundred thousand SKUs:
1. **Horizontal Read Scaling:** Route `@Transactional(readOnly = true)` queries to PostgreSQL read replicas.
2. **Full-Text & Faceted Search:** Introduce OpenSearch/Elasticsearch fed via Transactional Outbox + CDC (Debezium) if fuzzy keyword search, typo tolerance, or dynamic facet counts are needed.
3. **Rate Limiting & Observability:** Add Bucket4j/Redis rate limiting at the API gateway and export Micrometer/OpenTelemetry traces and p95/p99 latency metrics.
