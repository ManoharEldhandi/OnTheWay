# Persistence & Portability — develop here, run there

Goal: build on one machine and run the whole product on any other machine that has only Docker —
with **durable** data (MySQL), not an in-memory demo.

## Two ways to run, by intent

| Mode | Database | Data | Use it for |
| --- | --- | --- | --- |
| `demo` profile | H2 (in-memory) | Seeded: 115 shops / 507 items, wiped on restart | Zero-setup local demo |
| `dev`/`prod` profile | **MySQL** (durable) | Whatever you create; survives restarts | Real use, develop-here-run-there |

The fast test suite uses H2 in **MySQL-compatibility mode** and runs the **real Flyway migrations**,
so the migrations and JPA mappings are verified on every build without needing Docker.

## One command, full stack

```bash
docker compose up --build
```

This starts three services on a private network:

- **db** — MySQL 8, data in a named volume (`ontheway-db`) so it **persists across restarts**.
- **backend** — Spring Boot (`dev` profile), applies Flyway migrations on startup, waits for the
  database to be healthy first.
- **frontend** — the built React app served by **nginx**, which also **proxies `/api`** to the
  backend. So the browser talks to a single origin (`http://localhost:5173`) — no CORS, no
  hard-coded backend host. Client-side routes fall back to `index.html`.

Move the repo to another machine, run the same command, and you have the same product.

### First-run admin
A fresh MySQL has no data, and self-registration as `ADMIN` is deliberately blocked, so the stack
**bootstraps an administrator** from environment variables on first boot
(`AdminBootstrap`). The compose file sets safe local defaults:

```yaml
ONTHEWAY_ADMIN_EMAIL: admin@ontheway.app
ONTHEWAY_ADMIN_PASSWORD: change-me-admin     # override in any real deployment
```

The bootstrap is idempotent: it does nothing if an admin already exists or if no credentials are
supplied. The demo profile seeds its own admin, so the bootstrap is inactive there.

> Change `ONTHEWAY_ADMIN_PASSWORD` and `JWT_SECRET` before exposing the stack to a network.

## MySQL specifics that bit us (and the fixes)

- **`caching_sha2_password` over a non-TLS connection.** MySQL 8's default auth plugin refuses to
  send its public key unless allowed, failing with *"Public Key Retrieval is not allowed"*. Fixed by
  adding `allowPublicKeyRetrieval=true` (alongside `useSSL=false`) to the dev/compose JDBC URL. In
  production you would instead enable TLS.
- **The V6 migration drops a uniqueness constraint differently on H2 vs MySQL.** On both engines the
  unique constraint and the foreign key on `merchants.user_id` are served by the same index, so the
  migration drops the constraint, drops the FK, drops the leftover unique index, then recreates the
  FK (non-unique). Names are discovered from JDBC metadata, and the migration runs outside a
  transaction. See `db/migration/V6__shop_lifecycle.java`.

## Verifying migrations on real MySQL

Beyond the H2 suite, an opt-in Testcontainers test boots a real MySQL 8 and applies V1–V6:

```bash
mvn -s custom-m2/settings.xml -Dmysql.it=true -Dtest=MySqlMigrationIntegrationTest test
```

It asserts the migrations apply cleanly and that one owner can hold multiple shops (the V6
outcome). It is excluded from the default suite because it needs a Docker daemon.

> On Docker Desktop, only `-Dmysql.it=true` is needed. With a very new engine (e.g. Colima/Docker
> 29+) whose minimum API is 1.44, also pass `-DargLine="-Dapi.version=1.44"` and set
> `TESTCONTAINERS_RYUK_DISABLED=true` so the bundled Docker client can negotiate the API version.
