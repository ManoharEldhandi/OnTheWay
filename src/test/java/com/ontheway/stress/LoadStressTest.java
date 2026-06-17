package com.ontheway.stress;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency / load test that simulates many customers using the platform at the same
 * time and proves the backend serves them without errors.
 *
 * <p>It is disabled during the normal test run (to keep the build fast) and is enabled with
 * the {@code -Dstress=true} system property. Volume and concurrency are configurable:
 *
 * <pre>
 *   mvn -s custom-m2/settings.xml test -Dstress=true -Dtest=LoadStressTest
 *   mvn -s custom-m2/settings.xml test -Dstress=true -Dtest=LoadStressTest \
 *       -Dstress.users=1000 -Dstress.concurrency=250
 * </pre>
 *
 * <p>Each simulated user runs a realistic session against a live HTTP server:
 * a discovery search, an ETA quote, and (for a quarter of them) placing an order.
 * The test reports throughput and latency percentiles and asserts a near-zero error rate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "stress", matches = "true")
class LoadStressTest {

    @LocalServerPort
    private int port;

    @org.springframework.beans.factory.annotation.Autowired
    private com.ontheway.repository.MerchantRepository merchantRepository;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    private String baseUrl;
    private long merchantId;
    private long menuItemId;
    private final List<String> tokens = new ArrayList<>();

    // A distinctive store location so discovery returns this test's store deterministically.
    private static final double STORE_LAT = 1.234567;
    private static final double STORE_LNG = 1.234567;
    private static final int TOKEN_POOL = 40;

    @BeforeAll
    void setUp() throws Exception {
        baseUrl = "http://localhost:" + port;

        // Onboard one merchant, apply for a shop, and approve it so it is discoverable.
        String merchantToken = registerAndLogin("stress-merchant@ontheway.app", "MERCHANT");
        String merchantBody = postJson("/api/merchant/shops", """
                {"storeName":"Stress Test Store","storeType":"RESTAURANT","address":"Load Lane",
                 "latitude":%s,"longitude":%s,"prepTimeMins":10,"etaBufferMins":5}
                """.formatted(STORE_LAT, STORE_LNG), merchantToken).body();
        merchantId = mapper.readTree(merchantBody).get("merchantId").asLong();
        com.ontheway.support.TestFixtures.approve(merchantRepository, merchantId);

        String itemBody = postJson("/api/menu-items/" + merchantId, """
                {"name":"Load Test Meal","price":9.99,"availability":true}
                """, merchantToken).body();
        menuItemId = mapper.readTree(itemBody).get("menuItemId").asLong();

        // Pre-authenticate a pool of customers; concurrent sessions reuse these tokens.
        for (int i = 0; i < TOKEN_POOL; i++) {
            tokens.add(registerAndLogin("stress-user-" + i + "@ontheway.app", "USER"));
        }
    }

    @Test
    void handlesManyConcurrentUsersWithoutErrors() throws Exception {
        int totalUsers = Integer.getInteger("stress.users", 1000);
        int concurrency = Integer.getInteger("stress.concurrency", 250);

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(totalUsers);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        List<Long> latenciesMs = new CopyOnWriteArrayList<>();

        for (int i = 0; i < totalUsers; i++) {
            final int userIndex = i;
            pool.submit(() -> {
                try {
                    startGate.await();
                    long start = System.nanoTime();
                    boolean ok = runUserSession(userIndex);
                    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                    latenciesMs.add(elapsedMs);
                    if (ok) {
                        successes.incrementAndGet();
                    } else {
                        failures.incrementAndGet();
                    }
                } catch (Exception ex) {
                    failures.incrementAndGet();
                } finally {
                    finished.countDown();
                }
            });
        }

        long wallStart = System.nanoTime();
        startGate.countDown(); // release all simulated users at once
        boolean completed = finished.await(2, TimeUnit.MINUTES);
        long wallMs = (System.nanoTime() - wallStart) / 1_000_000;
        pool.shutdownNow();

        printReport(totalUsers, concurrency, successes.get(), failures.get(), wallMs, latenciesMs);

        assertThat(completed).as("all simulated users finished within the time budget").isTrue();
        assertThat(successes.get())
                .as("successful sessions out of %d", totalUsers)
                .isGreaterThanOrEqualTo((int) Math.ceil(totalUsers * 0.99)); // allow <=1% tolerance
    }

    /** One realistic customer session: discover, quote an ETA, and sometimes place an order. */
    private boolean runUserSession(int userIndex) throws Exception {
        String token = tokens.get(userIndex % tokens.size());

        HttpResponse<String> discovery = get(
                "/api/discovery/nearby?lat=" + STORE_LAT + "&lng=" + STORE_LNG + "&radiusKm=5", token);
        if (discovery.statusCode() != 200) {
            return false;
        }

        HttpResponse<String> quote = postJson("/api/eta/quote", """
                {"merchantId":%d,"latitude":%s,"longitude":%s}
                """.formatted(merchantId, STORE_LAT + 0.05, STORE_LNG + 0.05), token);
        if (quote.statusCode() != 200) {
            return false;
        }

        // A quarter of the users complete a purchase (write path).
        if (userIndex % 4 == 0) {
            HttpResponse<String> order = postJson("/api/orders", """
                    {"merchantId":%d,"latitude":%s,"longitude":%s,"paymentMethod":"CARD",
                     "items":[{"menuItemId":%d,"quantity":1}]}
                    """.formatted(merchantId, STORE_LAT + 0.05, STORE_LNG + 0.05, menuItemId), token);
            return order.statusCode() == 201;
        }
        return true;
    }

    // ----- HTTP helpers --------------------------------------------------

    private String registerAndLogin(String email, String role) throws Exception {
        postJson("/api/auth/register", """
                {"email":"%s","password":"password123","name":"Load Tester","role":"%s"}
                """.formatted(email, role), null);
        HttpResponse<String> login = postJson("/api/auth/login", """
                {"email":"%s","password":"password123"}
                """.formatted(email), null);
        JsonNode node = mapper.readTree(login.body());
        return node.get("token").asText();
    }

    private HttpResponse<String> postJson(String path, String json, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // ----- Reporting -----------------------------------------------------

    private void printReport(int users, int concurrency, int ok, int failed,
                             long wallMs, List<Long> latenciesMs) {
        List<Long> sorted = new ArrayList<>(latenciesMs);
        sorted.sort(Long::compareTo);
        double throughput = wallMs > 0 ? (users * 1000.0 / wallMs) : users;

        StringBuilder report = new StringBuilder();
        report.append(System.lineSeparator());
        report.append("==================== OnTheWay load test ====================").append(System.lineSeparator());
        report.append(String.format("Simulated users      : %d%n", users));
        report.append(String.format("Worker concurrency   : %d%n", concurrency));
        report.append(String.format("Successful sessions  : %d%n", ok));
        report.append(String.format("Failed sessions      : %d%n", failed));
        report.append(String.format("Total wall time      : %d ms%n", wallMs));
        report.append(String.format("Throughput           : %.0f sessions/sec%n", throughput));
        report.append(String.format("Latency p50          : %d ms%n", percentile(sorted, 50)));
        report.append(String.format("Latency p95          : %d ms%n", percentile(sorted, 95)));
        report.append(String.format("Latency p99          : %d ms%n", percentile(sorted, 99)));
        report.append(String.format("Latency max          : %d ms%n", sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1)));
        report.append("============================================================");
        System.out.println(report);
    }

    private long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
