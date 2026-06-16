# Phase 0 — Stabilization & Security

This document describes the foundational hardening delivered in Phase 0. It is
written for developers joining the project and for reviewers evaluating the codebase.

## Summary

Phase 0 turned the original CRUD prototype into a **secure, migratable, tested**
baseline. No feature value was added yet — instead we removed correctness and
security defects and stood up the engineering scaffolding everything else relies on.

## What changed

### 1. Configuration & secrets (profiles)
- Configuration is split into Spring profiles: `dev` (MySQL), `test` (H2), `prod` (strict).
- **No secrets in source.** Every sensitive value (`JWT_SECRET`, DB password, payment
  keys) is read from an environment variable with a safe local-dev fallback. See
  [.env.example](../.env.example).
- Switch profiles with `SPRING_PROFILES_ACTIVE`.

### 2. Database migrations (Flyway)
- Flyway is the **single source of truth** for the schema; Hibernate never generates
  or mutates it (`ddl-auto: none`).
- Migrations live in `src/main/resources/db/migration` (`V1__baseline_schema.sql`,
  `V2__order_events.sql`) and are written in a portable SQL subset that runs on both
  MySQL 8 and H2 — so the test suite exercises the real migrations.

### 3. Authentication & JWT
- The JWT filter now **validates the signature and expiry _before_ trusting any claim**,
  and never lets an auth failure surface as a 500 — invalid/expired tokens yield `401`.
- Access-token lifetime is short (15 min by default); refresh-token config is in place
  for a later phase.
- Login uses a dedicated `LoginRequest` DTO and returns a generic `401` on bad
  credentials (no account-existence leak).

### 4. Authorization & registration
- **Privilege escalation closed:** registration can never create an `ADMIN`. The role is
  optional and defaults to `USER`; `MERCHANT` self-signup is allowed, `ADMIN` is not.
- **IDOR closed:** order, payment, and menu-item endpoints now enforce **ownership** —
  a user can only read their own orders/payments; only the serving merchant (or an
  admin) can manage an order or a store's menu items.
- Duplicate email registration returns a clean `409 Conflict`.

### 5. Order integrity
- A real **state machine** governs order status: `PLACED → PREPARING → READY → PICKED`,
  with `→ CANCELLED` from non-terminal states. Illegal transitions return `400`.
- **Server-authoritative validation** at placement: each item must belong to the target
  merchant and be available; quantities are positive; prices come from the catalog.
- Every status change writes an immutable **`order_events`** audit row (who, from, to, when).

### 6. Transport hardening
- **CORS** is an explicit allowlist (no `*`), wired into Spring Security.
- Security response headers (frame options, a baseline content-security-policy) are set.

## HTTP error contract

| Situation | Status |
|---|---|
| Validation error / illegal transition / malformed input | `400` |
| Missing or invalid/expired token, bad credentials | `401` |
| Authenticated but not the owner | `403` |
| Resource not found | `404` |
| Duplicate email | `409` |
| Unexpected server error | `500` |

## How to run

```bash
# Build (Java 17 + Maven)
export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17)"
mvn -s custom-m2/settings.xml clean test     # hermetic: H2 + Flyway, no Docker needed
mvn -s custom-m2/settings.xml spring-boot:run # dev profile, needs local MySQL
```

## Tests

| Test | Proves |
|---|---|
| `OnthewayApplicationTests` | Context boots; Flyway migrations apply on H2; entities map. |
| `OrderStatusTest` | State-machine transition rules. |
| `UserServiceImplTest` | No ADMIN self-register; duplicate → 409; default USER; password hashed. |
| `OrderServiceImplTest` | Place-order validation; ownership; transition + audit. |
| `AuthSecurityIntegrationTest` | Register/login; ADMIN→400; dup→409; bad token→401; bad password→401. |
| `OrderFlowIntegrationTest` | Full lifecycle; IDOR→403; illegal transition→400; role checks. |

All 27 tests pass hermetically (`mvn test`).
