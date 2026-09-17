# Modular Monolith — Order + Inventory + Notification (Lab 2)

Extends the Lab 1 Order/Inventory monolith with multi-item orders,
cancellation, live read endpoints, and an event-driven Notification module.

- `edu.cit.alvarado.shop` — Order module (unchanged package)
- `edu.cit.alvarado.inventory` — Inventory module (unchanged package)
- `edu.cit.alvarado.notification` — **new** Notification module

Four integration styles now live side by side in one deployable:
1. **In-process, direct call** — Order → Inventory (`InventoryService` interface, plain Java method call)
2. **Service-to-database** — every module talks to Supabase/Postgres via Spring Data JPA
3. **External REST client** — the React frontend calls the backend over HTTP
4. **In-process publish/subscribe** — Order/Inventory publish domain events; Notification listens, with zero direct dependency between them

## Project layout

```
backend/    Spring Boot app (Order + Inventory + Notification modules)
frontend/   React (Vite) client
sql/        schema.sql - full schema recreation script, run this before starting the backend
```

## Setup

### 1. Supabase (carried over from Lab 1, re-run for Lab 2's schema)

1. Create a free project at supabase.com (skip if reusing your Lab 1 project).
2. Open the SQL editor and run `sql/schema.sql`. **This drops and recreates
   every table** (`inventory`, `orders`, `order_items`, `notifications`) and
   reseeds `inventory` with P100 (Wireless Mouse, stock 25), P200
   (Mechanical Keyboard, stock 10), and P300 (USB-C Hub, stock 0). The
   script is the single source of truth for the schema — don't hand-edit
   tables in the Supabase UI.
3. From the green "Connect" button → Direct/Session pooler tab, grab your
   host, port, database, and user. If your network doesn't support IPv6,
   use the **Session pooler** connection (works over IPv4).

### 2. Backend

```bash
cd backend
cp .env.example .env   # then fill in your real Supabase values
export $(cat .env | xargs)   # PowerShell: see note below
mvn spring-boot:run
```

Required environment variables (never committed — see `.gitignore`):
`SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`. Optional:
`LOW_STOCK_THRESHOLD` (default 5).

**Windows PowerShell** doesn't have `export`/`xargs`. Load `.env` into the
session instead:
```powershell
Get-Content .env | ForEach-Object { if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }; $name, $value = $_ -split '=', 2; [System.Environment]::SetEnvironmentVariable($name, $value) }
mvn spring-boot:run
```

The backend starts on `http://localhost:8080`.

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Opens on `http://localhost:5173`. Build a cart (add one or more products +
quantities), submit, and watch the inventory table, order history, and
activity feed update live.

## API surface

| Method | Path                      | Purpose                                            |
|--------|---------------------------|-----------------------------------------------------|
| POST   | `/api/orders`              | Place a multi-item order (all-or-nothing)          |
| POST   | `/api/orders/{orderId}/cancel` | Cancel a CONFIRMED order and restock its items |
| GET    | `/api/orders`               | Order history with status + line items             |
| GET    | `/api/inventory`            | Current stock for every product                    |
| GET    | `/api/notifications`        | Notification log (confirmations, rejections, cancellations, low-stock alerts) |

`POST /api/orders` request/response shape:
```json
// Request
{ "items": [ { "productId": "P100", "quantity": 2 }, { "productId": "P200", "quantity": 1 } ] }

// Response
{ "status": "CONFIRMED", "reason": "All items reserved",
  "items": [ { "productId": "P100", "outcome": "RESERVED" }, { "productId": "P200", "outcome": "RESERVED" } ],
  "inventory": [ { "productId": "P100", "name": "Wireless Mouse", "stock": 23 }, ... ] }
```

## Testing the four required scenarios + capturing Network tab evidence

1. **Multi-item order, all succeed (CONFIRMED)** — add P100 qty 2 and P200
   qty 1 to the cart (both well within stock), submit.
2. **Multi-item order, one item fails (REJECTED, no partial reservation)**
   — add P100 qty 2 and P300 qty 1 (P300 has 0 stock) to the cart, submit.
   Confirm in the Network tab response that `items` shows
   `INSUFFICIENT_STOCK` for P300 and `AVAILABLE` for P100 — and then check
   the Inventory table: P100's stock must be **unchanged**, proving nothing
   was reserved.
3. **Cancel + restock reflected in GET /api/inventory** — cancel a
   CONFIRMED order from Order history, then check the Inventory table (or
   the Network tab response of the `GET /api/inventory` call the frontend
   fires afterward) to confirm the cancelled quantities were added back.
4. **Notification feed with all three event types** — after doing the
   above, open the Activity feed and confirm you see a confirmed-order
   entry, a rejected-order entry, and (if any reserve pushed a product
   below 5 units) a low-stock alert. To reliably trigger low stock, order
   enough P200 (starts at 10) to drop it under 5, e.g. two separate orders
   of quantity 4 each.

For each scenario, open DevTools → Network tab, click the relevant request,
and screenshot the Request payload and Response body.

## Reflection (~450 words)

**1. Multi-item orders now touch InventoryService several times within one
request. What ensures this stays atomic in-process, and what would you need
to add (e.g. sagas, compensating transactions) if Order and Inventory were
split across a network?**

In-process, atomicity comes from a single `@Transactional` boundary around
`OrderService.placeOrder()`. Every `InventoryService.reserve()` call for
each line item joins that same transaction (Spring's default propagation is
REQUIRED, so a nested `@Transactional` method doesn't start a new
transaction — it participates in the caller's). Validation happens first,
against every item, with nothing reserved yet; only if all items pass does
the loop actually call `reserve()`. If any reserve unexpectedly fails
(a concurrent order raced us between validation and reservation), I throw,
which rolls back every reserve already made in that loop, plus the order
row itself — so the database never shows a half-reserved order. All of this
is free because it's one JVM, one connection, one commit/rollback.

If Order and Inventory were split into separate services, that transaction
boundary disappears — a network call can't participate in a local database
transaction. I'd need a saga: Order would call Inventory's API to reserve
each item (or one batch reservation), and if a later item in the sequence
fails, Order would have to explicitly call a compensating "release/restock"
endpoint on Inventory for every item it had already reserved, rather than
relying on a rollback. I'd also want reservations to expire automatically
(a TTL) in case Order crashes mid-saga and never sends the compensation
call, plus idempotency keys on the reserve/release calls so retries after a
timeout don't double-reserve or double-release.

**2. How does publishing an event instead of calling Notification directly
change the coupling between OrderService and Notification? What would you
need if Notification became a separate microservice (message broker,
delivery guarantees)?**

Right now `OrderService` calls `eventPublisher.publishEvent(...)` and has
no idea Notification exists — it doesn't import `NotificationService`, and
the compiler would still let this code build even if the entire
`notification` package were deleted. That's much looser coupling than a
direct method call: Order depends only on Spring's generic
`ApplicationEventPublisher` and its own event record types, not on any
concrete listener. Today the listener runs synchronously in the same
thread and the same database transaction, so a notification write is as
reliable as the order write itself (though it also means a failing
notification save could roll back the order — a trade-off worth knowing).

If Notification became a separate microservice, in-process events
disappear entirely. I'd need a message broker (e.g. RabbitMQ or Kafka) that
Order publishes to and Notification consumes from, decoupling their
lifecycles — Notification being down wouldn't block order placement. I'd
also need to pick delivery guarantees: at-least-once delivery with an
idempotent consumer (dedupe by order ID + event type) is usually the
pragmatic choice, since exactly-once messaging is hard to guarantee end to
end. I'd likely combine this with the transactional outbox pattern — write
the event to an outbox table in the same transaction as the order, then a
separate process publishes from the outbox to the broker — so a crash
between "order saved" and "event published" can't silently drop a
notification.

**3. You now have three modules and two distinct event types. If forced to
extract exactly one module into its own microservice first, which would you
pick and why — and what changes in your code to do it?**

I'd extract **Notification** first. It's a pure consumer with no other
module depending on it — nothing calls into Notification, so extracting it
can't break Order or Inventory's public contracts at all. It also has the
lowest consistency requirements of the three: if a notification arrives a
few seconds late, or is briefly unavailable, nobody's order is blocked or
incorrect, unlike Inventory (which genuinely needs strong consistency with
Order to avoid overselling). Code-wise, the change is almost entirely
additive rather than disruptive: keep the `OrderPlacedEvent`,
`OrderRejectedEvent`, `OrderCancelledEvent`, and `LowStockEvent` record
shapes as a shared contract (as JSON schemas, not Java classes, once truly
cross-process), add a message broker publisher in Order/Inventory
alongside (or instead of) `ApplicationEventPublisher`, and rewrite
`NotificationServiceImpl`'s `@EventListener` methods as message consumers
that deserialize the same event shapes. `OrderService` and
`InventoryServiceImpl` themselves barely change, since they were already
only "shouting into the void" rather than calling Notification directly —
that decoupling is exactly what makes this extraction the easy one to do
first.
