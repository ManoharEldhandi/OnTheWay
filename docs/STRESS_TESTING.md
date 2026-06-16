# OnTheWay — Load & Stress Testing

This project includes a load test that simulates many customers using the platform at the same
time and proves the backend serves them without errors. It is meant both as a safety check and
as a demonstration of capacity.

## What it does

`LoadStressTest` ([src/test/java/com/ontheway/stress/LoadStressTest.java](../src/test/java/com/ontheway/stress/LoadStressTest.java))
starts the application on a random port and runs a configurable number of concurrent customer
sessions against the live HTTP server. Each session performs a realistic sequence:

1. a **discovery** search for nearby stores,
2. an **ETA quote**, and
3. for a quarter of the users, **placing an order** (a write path).

It then reports throughput and latency percentiles and asserts that at least 99% of sessions
succeed.

The test is **disabled during the normal build** (so `mvn test` stays fast) and is enabled with
the `-Dstress=true` system property.

## How to run it

Default volume (1,000 users, 250 concurrent workers):

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

mvn -s custom-m2/settings.xml test -Dstress=true -Dtest=LoadStressTest
```

Custom volume and concurrency:

```bash
mvn -s custom-m2/settings.xml test -Dstress=true -Dtest=LoadStressTest \
    -Dstress.users=2000 -Dstress.concurrency=400
```

| Property | Default | Meaning |
|----------|---------|---------|
| `stress` | (off) | Must be `true` to enable the test |
| `stress.users` | `1000` | Total simulated user sessions |
| `stress.concurrency` | `250` | Number of worker threads running sessions in parallel |

## Reading the report

The test prints a summary such as:

```
==================== OnTheWay load test ====================
Simulated users      : 1000
Worker concurrency   : 250
Successful sessions  : 1000
Failed sessions      : 0
Total wall time      : 1369 ms
Throughput           : 730 sessions/sec
Latency p50          : 305 ms
Latency p95          : 624 ms
Latency p99          : 884 ms
Latency max          : 1218 ms
============================================================
```

- **Successful / failed sessions** — the test fails if more than 1% of sessions error.
- **Throughput** — completed sessions per second across the whole run.
- **Latency p50/p95/p99** — per-session latency percentiles (each session is several HTTP calls).

## Notes

- The test runs against the in-memory `test` profile, so it measures application and framework
  behaviour rather than a production database. Against a real database, tune the connection pool
  (`DB_POOL_MAX`) and re-measure.
- Results vary with the host machine. The numbers above were captured on a developer laptop and
  are included as an indicative baseline, not a guarantee.
