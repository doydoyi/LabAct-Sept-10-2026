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
**1. How does the Spring Boot backend determine which user owns a Service Request?**
Basically, when I log in, the backend sends back a JWT token to my React app. Every time I try to do something after that (like view or edit a request), I have to attach that token to the request. The backend reads the token and pulls out my username from it, so that's how it knows it's me and not someone else.

**2. Why should ReactJS not send a userId to determine which records a user can access?**
Because that would be super easy to fake. Like, if my React app just sent "userId: 1" with every request, anyone could just open the browser console and change it to "userId: 2" and boom, they're looking at someone else's data. So the frontend can't be trusted with that kind of thing — the backend has to figure out who's really asking by checking the token itself.

**3. What happens when a request is sent without a valid JWT?**
It just gets rejected with a 401 error. The backend checks for the token first before it even lets the request go through to the actual controller/logic, so if there's no token or it's expired/fake, it stops right there.

**4. Where is authorization enforced in your implementation?**
It's in my service and repository layer. Whenever I try to get, edit, or delete a service request, the query specifically checks that the "createdBy" field matches whoever's logged in right now. So even if I somehow tried to access someone else's request by ID, it just won't show up because the query wouldn't match.
