# OnTheWay — Usage Guide

This guide explains how to set up, run, test, and use the OnTheWay platform end to end.

---

## 1. Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java (JDK) | 17 | The project targets Java 17. A newer system JDK is fine as long as the build uses 17 (see below). |
| Maven | 3.9+ | Or use the system `mvn`. |
| Node.js | 18+ | For the web frontend. |
| Docker | optional | Only needed for the MySQL-backed full-stack run. |

### Pointing the build at JDK 17

If your default `java` is newer than 17, set `JAVA_HOME` for the build session. On macOS with
Homebrew:

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
java -version   # should report 17.x
```

> Editor note: a committed `.vscode/settings.json` pins the Java language server to JDK 17 so
> in-editor analysis matches the build. If you change machines, update the path there.

---

## 2. Run the backend

There are two ways to run the backend. For evaluation, use the **demo profile** — it needs no
database and seeds realistic data.

### Option A — Demo profile (zero setup, in-memory)

```bash
mvn -s custom-m2/settings.xml spring-boot:run -Dspring-boot.run.profiles=demo
```

Or run the packaged jar:

```bash
mvn -s custom-m2/settings.xml -DskipTests package
SPRING_PROFILES_ACTIVE=demo java -jar target/OnTheWay-1.0.0.jar
```

- The backend starts on **http://localhost:8080**.
- It uses an in-memory H2 database, applies the real Flyway migrations, and seeds demo data.
- Data resets on every restart.

### Option B — Dev profile (local MySQL)

1. Start MySQL and create the database (or let the URL create it):
   ```sql
   CREATE DATABASE onthewaydb;
   ```
2. Provide connection settings via environment variables (see [.env.example](../.env.example)),
   then run:
   ```bash
   SPRING_PROFILES_ACTIVE=dev \
   DB_URL="jdbc:mysql://localhost:3306/onthewaydb?useSSL=false&serverTimezone=UTC&createDatabaseIfNotExist=true" \
   DB_USERNAME=root DB_PASSWORD=yourpassword \
   mvn -s custom-m2/settings.xml spring-boot:run
   ```

### Option C — Full stack with Docker (backend + MySQL)

```bash
docker compose up --build
```

This builds the backend image and starts it together with MySQL. The backend runs the `dev`
profile and applies migrations on startup.

### Useful URLs

| URL | Purpose |
|-----|---------|
| http://localhost:8080/swagger-ui.html | Interactive API documentation |
| http://localhost:8080/actuator/health | Health check |
| http://localhost:8080/api-docs | OpenAPI JSON |

### Request tracing

Every response includes an `X-Request-Id` header, and every log line for that request carries the
same id (shown in brackets, e.g. `[trace-XYZ-789]`). To trace a specific call, send your own
`X-Request-Id` request header and it will be honoured and echoed back.

---

## 3. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

- The app starts on **http://localhost:5173**.
- API calls to `/api` are proxied to the backend on port 8080, so run the backend first.

To produce a production build:

```bash
npm run build      # outputs to frontend/dist
npm run preview    # serve the production build locally
```

---

## 4. Demo accounts

When the backend runs with the `demo` profile, these accounts are seeded
(password for all: `password123`). The login screen has one-click buttons for them.

| Email | Role | Use |
|-------|------|-----|
| alice@ontheway.app | Customer | Browse, order, track |
| biryani@ontheway.app | Merchant | Manage incoming orders |
| medplus@ontheway.app | Merchant | Pharmacy storefront |
| cafe@ontheway.app | Merchant | Café storefront |
| admin@ontheway.app | Admin | Administrative access |

---

## 5. Walkthrough — the customer journey

1. **Log in** as the customer (`alice@ontheway.app`).
2. **Discover**: pick a location (presets or the browser's location), set a radius, and
   optionally filter by category. The map shows your position and nearby stores, nearest first.
3. **Open a store** and add items to the cart.
4. **Checkout**: the ETA panel shows when the order will be **ready** and when the store should
   **start preparing**, timed to your arrival. Place the order; payment runs through the
   configured gateway (the mock gateway by default).
5. **Track**: the order page updates automatically as the order moves through
   `PLACED → PREPARING → READY → PICKED`.

## 6. Walkthrough — the merchant console

1. **Log in** as a merchant (e.g. `biryani@ontheway.app`).
2. Open **Merchant Console** to see incoming orders.
3. Advance each order through its legal lifecycle, or cancel it. Illegal transitions are
   rejected by the backend.

> The platform also advances orders from `PLACED` to `PREPARING` automatically when the
> ETA-computed prep-start time arrives, so a store can rely on the timing without watching
> the console.

---

## 7. Run the tests

Hermetic test suite (no database or Docker required — uses H2 with the real migrations):

```bash
mvn -s custom-m2/settings.xml clean test
```

Frontend type-check and build:

```bash
cd frontend && npm run build
```

For the load/stress test, see [STRESS_TESTING.md](STRESS_TESTING.md).

---

## 8. Configuration reference

All settings have safe local defaults and can be overridden by environment variables. The full
list is in [.env.example](../.env.example). The most common ones:

| Variable | Default | Purpose |
|----------|---------|---------|
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev`, `test`, `prod`, or `demo` |
| `SERVER_PORT` | `8080` | Backend port |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | local MySQL | Database connection (dev/prod) |
| `JWT_SECRET` | dev placeholder | Signing secret — set a long random value outside dev |
| `CORS_ALLOWED_ORIGINS` | localhost dev ports | Comma-separated allowlist of frontends |
| `PAYMENT_PROVIDER` | `mock` | `mock`, `stripe`, or `razorpay` |
| `ROUTE_PROVIDER` | `mock` | `mock`, `google`, `mapbox`, or `osrm` |

---

## 9. Troubleshooting

| Symptom | Cause and fix |
|---------|---------------|
| Editor shows many "cannot find symbol" / "never read" errors on Lombok classes | The language server is using a JDK newer than 17. The committed `.vscode/settings.json` pins it to JDK 17; reload the window after opening the project. The Maven build is unaffected. |
| `mvn` uses the wrong Java version | Set `JAVA_HOME` to a JDK 17 (section 1). |
| Frontend cannot reach the API | Start the backend first; the dev server proxies `/api` to port 8080. |
| Port already in use | Stop the existing process or change `SERVER_PORT` (backend) / the Vite port. |
| Docker build cannot reach MySQL | Wait for the `db` health check; the backend depends on it and retries. |
