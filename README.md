# Modular Monolith Integration Lab — Order + Inventory

Java Spring Boot (modular monolith) + Supabase (Postgres) + React (Vite) frontend.

- `edu.cit.alvarado.shop` — Order module
- `edu.cit.alvarado.inventory` — Inventory module
- Order calls Inventory **in-process** (plain Java method call through the
  `InventoryService` interface — no HTTP, no network hop between the two
  modules).
- The React frontend calls the backend over HTTP (`POST /api/orders`) — the
  one genuine network boundary in this lab.

## Project layout

```
backend/    Spring Boot app (Order + Inventory modules)
frontend/   React (Vite) client
sql/        schema.sql - run this in Supabase before starting the backend
```

## Setup

### 1. Supabase

1. Create a free project at supabase.com.
2. Open the SQL editor and run `sql/schema.sql`. This creates `inventory`
   and `orders` and seeds `inventory` with P100 (Wireless Mouse, stock 25),
   P200 (Mechanical Keyboard, stock 10), and P300 (USB-C Hub, stock 0).
3. From Project Settings → Database, grab the connection string, database
   user, and password.

### 2. Backend

```bash
cd backend
cp .env.example .env   # then fill in your real Supabase values
export $(cat .env | xargs)   # or set these in your IDE's run config
./mvnw spring-boot:run
```

Required environment variables (never committed — see `.gitignore`):
`SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`.

The backend starts on `http://localhost:8080`.

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Opens on `http://localhost:5173` (matches the backend's default CORS
allow-list). Pick a product, enter a quantity, submit, and watch the result
area show CONFIRMED or REJECTED.

### 4. Testing the two paths + capturing Network tab evidence

- **Confirmed path**: order P100 (stock 25) with quantity ≤ 25.
- **Rejected path**: order P300 (stock 0) with any quantity ≥ 1, or order
  more than the available stock of any product.
- In the browser DevTools → Network tab, click the `orders` request for each
  case and screenshot the Request payload and Response body — that's your
  end-to-end evidence.

## Reflection (~400 words)

**1. In-process vs. separate microservices over a network — what do you get
for free, and what would you need to add back if split?**

Keeping Order and Inventory in one process gets us a lot for free: calls are
just Java method invocations, so they're fast (nanoseconds, not
milliseconds), type-safe at compile time, and share one transaction — the
stock decrement and the order row can commit or roll back together with a
single `@Transactional` boundary. There's also no serialization, no network
client to configure, and no partial-failure handling, because if the JVM is
up, the "call" cannot fail for network reasons. Deployment is simpler too:
one artifact, one process to monitor.

If we split Inventory into its own microservice, we'd have to add back
everything the in-process boundary was hiding: a network client (REST or
gRPC) with timeouts and retries, serialization/deserialization, service
discovery or at least a configured base URL, and authentication between
services. Because a single ACID transaction across two databases isn't
realistic, we'd need a strategy for consistency — e.g., a saga: reserve
stock via Inventory's API, then create the order, and compensate (release
the reservation) if the order write fails. We'd also need observability
(distributed tracing, correlation IDs) to debug a request that now spans two
processes, and a plan for what Order does when Inventory is slow or down
(circuit breakers, fallback behavior) instead of assuming it always
responds.

**2. Why does package-private visibility on `InventoryServiceImpl` matter
for the module boundary — what breaks if it's public?**

Making `InventoryServiceImpl` package-private turns the module boundary from
a convention into something the Java compiler enforces: code outside
`edu.cit.alvarado.inventory` literally cannot name or inject the
implementation class, so `OrderService` is forced to depend on the
`InventoryService` interface. That keeps the contract stable and small on
purpose. If `InventoryServiceImpl` were public, nothing would stop the Order
module (or any future module) from injecting it directly, calling
implementation-specific methods that aren't on the interface, or relying on
internal behavior that was never meant to be a promise. Over time that
erodes the boundary — Inventory can no longer refactor its internals (change
persistence, add caching, swap the repository) without risking a break
somewhere in Order, because the "internal" class quietly became a public
dependency.

**3. When would you extract Inventory into its own microservice, and what
would need to change in your code to do it?**

I'd extract it when Inventory needs to scale, deploy, or be owned
independently from Order — for example, if inventory reads/writes become a
bottleneck under load that Order doesn't share, if a separate team owns
stock/warehouse logic and needs its own release cadence, or if other
services besides Order (e.g., a future Shipping or Reporting service) need
to read inventory data too, making a shared internal Java call impossible.
Code-wise: replace the `InventoryService` implementation used by Order with
an HTTP (or messaging) client that implements the same interface, so
`OrderService` itself barely changes — that's the payoff of coding to the
interface from the start. Behind that client I'd add retry/timeout policies,
map network and 4xx/5xx failures into the existing `ReservationResult`
rejection path, and replace the shared-transaction guarantee with a saga or
outbox pattern so a failed order write can compensate an already-approved
reservation. Inventory would get its own database (or at least its own
schema) instead of sharing tables directly with Order.
