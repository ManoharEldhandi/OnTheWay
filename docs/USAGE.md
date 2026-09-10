# OnTheWay developer guide

This guide covers local development, configuration, verification, and deployment. The application has a zero-setup local mode for evaluating the complete customer-to-merchant flow, plus a MySQL-backed Docker deployment for persistent development data.

## Prerequisites

| Tool | Version | Purpose |
| --- | --- | --- |
| Java | 17 | Backend runtime and build target |
| Maven | 3.9+ | Backend build and test runner |
| Node.js | 22.12+ (24 LTS recommended) | Frontend tooling |
| Docker | Optional | Persistent MySQL stack |

On macOS with Homebrew, point a shell session at Java 17 if another JDK is active:

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

## Start the complete application

From the repository root:

```bash
# Browser on this computer
./scripts/start local

# Browser on another device connected to the same network
./scripts/start network
```

The launcher starts Spring Boot and Vite together, applies the normal Flyway migrations to a clean in-memory database, and prints the application URL. It installs missing frontend dependencies and terminates both processes when you press `Ctrl-C`.

The `network` mode binds Vite to all interfaces and adds the detected LAN address to CORS. If a preferred port is occupied, use explicit overrides:

```bash
BACKEND_PORT=8081 FRONTEND_PORT=5174 ./scripts/start local
```

## Run the services manually

### Local in-memory profile

The `demo` Spring profile is intentionally isolated from persistent environments. It is useful for local product evaluation because it starts with a clean, representative catalog every time.

```bash
mvn -s custom-m2/settings.xml spring-boot:run -Dspring-boot.run.profiles=demo

cd frontend
npm install
npm run dev
```

The frontend proxies `/api` and `/ws` to `http://localhost:8080` by default. To point Vite at another backend:

```bash
VITE_API_TARGET=http://127.0.0.1:8081 npm run dev
```

### Persistent MySQL profile

Create a local database and configure it through environment variables:

```bash
SPRING_PROFILES_ACTIVE=dev \
DB_URL="jdbc:mysql://localhost:3306/onthewaydb?useSSL=false&serverTimezone=UTC&createDatabaseIfNotExist=true" \
DB_USERNAME=root \
DB_PASSWORD=your-password \
mvn -s custom-m2/settings.xml spring-boot:run
```

The application applies Flyway migrations at startup. Do not place credentials in committed files; use a local `.env` file or your deployment secret manager.

### Docker Compose

```bash
docker compose up --build
```

This starts MySQL, the backend, and an nginx-served frontend. Data persists in the named `ontheway-db` Docker volume.

| Service | Address |
| --- | --- |
| Frontend | http://localhost:5173 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health check | http://localhost:8080/actuator/health |
| OpenAPI JSON | http://localhost:8080/api-docs |

## Configuration

The application uses Spring profiles and environment variables. The production profile requires explicit values for database access, JWT signing, allowed origins, and payment credentials.

| Variable | Local default | Notes |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev`, `test`, `prod`, or `demo` |
| `DB_URL` | Profile-specific | JDBC URL for MySQL in persistent environments |
| `DB_USERNAME` / `DB_PASSWORD` | Profile-specific | Database credentials |
| `JWT_SECRET` | Development-only value | Supply a long random secret in production |
| `CORS_ALLOWED_ORIGINS` | Local frontend addresses | Comma-separated allowed browser origins |
| `PAYMENT_PROVIDER` | `mock` | `mock`, `stripe`, or `razorpay` |
| `RAZORPAY_API_KEY` / `RAZORPAY_API_SECRET` | Unset | Required only for Razorpay |
| `STRIPE_SECRET_KEY` / webhook secret | Unset | Required only for Stripe |

For payment providers, keep all private keys and webhook secrets server-side. The client only receives the public configuration required to launch the provider checkout.

## Verify changes

```bash
# Backend: unit, controller, integration, persistence, and realtime tests
mvn -s custom-m2/settings.xml clean test

# Frontend: TypeScript type-check and production bundle
cd frontend
npm ci
npm run build
```

The CI workflow runs the backend verification suite, a real-MySQL migration test, the frontend production build, and dependency auditing on every pull request.

## Deployment notes

- Use the `prod` profile and a managed MySQL instance for production.
- Store JWT, database, payment, and webhook secrets in the platform secret manager.
- Set `CORS_ALLOWED_ORIGINS` to the exact public application origins.
- Configure signed payment webhooks before enabling a live payment provider.
- Kubernetes manifests and environment templates are in [`k8s/`](../k8s/).

For architectural rationale, see [ARCHITECTURE.md](../ARCHITECTURE.md). For focused implementation notes, browse the remaining documents in this directory.
