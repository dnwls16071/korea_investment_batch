package com.project.korea_investment_batch.support;

import com.project.korea_investment_batch.shared.batch.CollectionCheckpoint;
import com.project.korea_investment_batch.shared.CollectionRun;
import com.project.korea_investment_batch.shared.Market;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@Import({BatchIntegrationSupport.Checkpoints.class, LocalCollectionConfiguration.class})
@EnabledIfEnvironmentVariable(named = "BATCH_TEST_JDBC_URL", matches = ".+")
public abstract class BatchIntegrationSupport {
    protected static final Map<String, Function<Request, Reply>> responses = new ConcurrentHashMap<>();
    protected static final List<Request> requests = new CopyOnWriteArrayList<>();
    private static final HttpServer server = startServer();
    private static final Path temporary = tempDirectory();

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected Fault fault;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        settings().forEach((key, value) -> registry.add(key, () -> value));
    }

    @BeforeEach
    void resetFixtures() throws IOException {
        fault.at.set(null);
        responses.clear();
        requests.clear();
        // This profile runs against the disposable database supplied through BATCH_TEST_JDBC_URL.
        jdbc.update("UPDATE kis_market_state SET latest_snapshot_id = NULL, last_collected_at = NULL");
        for (String table : List.of("kis_change", "kis_instrument", "kis_snapshot", "kis_stage", "kis_input",
                "dart_stage", "dart_page", "dart_attempt", "dart_scan", "dart_disclosure",
                "BATCH_STEP_EXECUTION_CONTEXT", "BATCH_STEP_EXECUTION", "BATCH_JOB_EXECUTION_CONTEXT",
                "BATCH_JOB_EXECUTION_PARAMS", "BATCH_JOB_EXECUTION", "BATCH_JOB_INSTANCE")) {
            jdbc.update("DELETE FROM " + table);
        }
        Path archives = temporary.resolve("archive");
        if (Files.exists(archives)) {
            try (var paths = Files.walk(archives)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    protected static CollectionRun run(Market market, LocalDate date) {
        return new CollectionRun(market, date, 1);
    }

    protected static List<Integer> requestedPages() {
        return requests.stream().filter(request -> request.path().equals("/dart"))
                .map(request -> Integer.valueOf(request.query().get("page_no"))).toList();
    }

    protected static void archive(Market market, String... rows) {
        archive(market, Map.of(), rows);
    }

    protected static void archive(Market market, Map<String, String> headers, String... rows) {
        String entry = KisFixtures.archiveEntry(market);
        var reply = new Reply(200, zip(entry, rows), headers);
        responses.put(archivePath(market), ignored -> reply);
    }

    protected static String archivePath(Market market) {
        return "/kis/" + KisFixtures.archiveEntry(market).toLowerCase(java.util.Locale.ROOT) + ".zip";
    }

    protected static byte[] zip(String entry, String... rows) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var zip = new ZipOutputStream(bytes)) {
                zip.putNextEntry(new ZipEntry(entry));
                if (rows.length != 0) {
                    zip.write((String.join("\r\n", rows) + "\r\n").getBytes(Charset.forName("x-windows-949")));
                }
                zip.closeEntry();
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected static String id(int number) {
        return "20261120%06d".formatted(number);
    }

    protected static Reply page(Request request, int total, int... numbers) {
        return page(request, Integer.parseInt(request.query().get("page_no")), total, numbers);
    }

    protected static Reply page(Request request, int reportedPageNo, int total, int[] numbers) {
        String body = Arrays.stream(numbers).mapToObj(number -> """
                {"rcept_no":"%s","rcept_dt":"%s","corp_cls":"%s","stock_code":"000999",
                 "corp_name":"회사","report_nm":"%s 주요사항보고"}
                """.formatted(id(number), request.query().get("bgn_de"), request.query().get("corp_cls"),
                number % 2 == 0 ? "[정정]" : "")).collect(java.util.stream.Collectors.joining(","));
        return json("""
                {"status":"000","page_no":%d,"page_count":2,"total_page":%d,"total_count":%d,"list":[%s]}
                """.formatted(reportedPageNo, (total + 1) / 2, total, body));
    }

    protected static Reply json(String json) {
        return new Reply(200, json.getBytes(StandardCharsets.UTF_8), Map.of("Content-Type", "application/json"));
    }

    protected record Reply(int status, byte[] body, Map<String, String> headers) {
        public static Reply error(int status) {
            return new Reply(status, new byte[0], Map.of());
        }
    }

    protected record Request(String path, Map<String, String> query) {}

    protected Worker worker(String source, CollectionRun run, String checkpoint) throws IOException {
        Path directory = Files.createTempDirectory(temporary, "worker-");
        Path ready = directory.resolve("ready");
        Path log = directory.resolve("worker.log");
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("batch.test.classpath", System.getProperty("java.class.path")),
                BatchWorker.class.getName(),
                checkpoint, ready.toString(), source, run.market().name(), run.date().toString(),
                Long.toString(run.revision())));
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        settings().forEach((key, value) -> {
            if (key.startsWith("spring.datasource.")) {
                builder.environment().put(key.replace('.', '_').toUpperCase(java.util.Locale.ROOT), value);
            } else {
                command.add("--" + key + "=" + value);
            }
        });
        Process process = builder.command(command).start();
        String job = source.equals("kis") ? "marketMasterImportJob" : "dartDisclosureImportJob";
        String scope = job + "/" + run.market() + "/" + run.date();
        return new Worker(process, ready, log, () -> Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT IS_FREE_LOCK(SHA2(CONCAT(DATABASE(), '/', ?), 256))", Boolean.class, scope)));
    }

    protected record Worker(Process process, Path ready, Path log, BooleanSupplier lockIsFree) implements AutoCloseable {
        public void awaitCheckpoint() throws IOException, InterruptedException {
            for (int attempt = 0; attempt < 200 && process.isAlive() && !Files.exists(ready); attempt++) {
                Thread.sleep(100);
            }
            if (!Files.exists(ready) || !process.isAlive()) {
                throw new AssertionError("Worker failed to reach checkpoint:\n" + Files.readString(log));
            }
        }

        @Override
        public void close() throws InterruptedException {
            if (process.isAlive()) {
                process.destroyForcibly();
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("Worker did not exit");
                }
            }
            // MySQL can observe the socket closure after the OS has reaped the worker.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!lockIsFree.getAsBoolean()) {
                if (System.nanoTime() >= deadline) {
                    throw new AssertionError("MySQL did not release the exited worker's collection lock");
                }
                Thread.sleep(25);
            }
        }
    }

    protected static Map<String, String> settings() {
        var settings = new HashMap<String, String>();
        settings.put("spring.datasource.url", System.getenv("BATCH_TEST_JDBC_URL"));
        settings.put("spring.datasource.username", System.getenv().getOrDefault("BATCH_TEST_DB_USER", "root"));
        settings.put("spring.datasource.password", System.getenv().getOrDefault("BATCH_TEST_DB_PASSWORD", ""));
        settings.put("test.kis.archive-root", temporary.resolve("archive").toString());
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        settings.put("test.dart.endpoint", base + "/dart");
        settings.put("test.kis.archive-base-url", base + "/kis");
        return settings;
    }

    private static HttpServer startServer() {
        try {
            HttpServer local = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            local.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            local.createContext("/", BatchIntegrationSupport::reply);
            Thread.ofVirtual().start(local::start).join();
            return local;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void reply(HttpExchange exchange) throws IOException {
        try (exchange) {
            var uri = exchange.getRequestURI();
            Map<String, String> query = new HashMap<>();
            if (uri.getRawQuery() != null) {
                Arrays.stream(uri.getRawQuery().split("&")).forEach(entry -> {
                    var pair = entry.split("=", 2);
                    query.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
                });
            }
            var request = new Request(uri.getPath(), Map.copyOf(query));
            requests.add(request);
            Reply response = responses.getOrDefault(request.path(), ignored -> Reply.error(404)).apply(request);
            response.headers().forEach(exchange.getResponseHeaders()::set);
            exchange.sendResponseHeaders(response.status(), response.body().length);
            exchange.getResponseBody().write(response.body());
        }
    }

    private static Path tempDirectory() {
        try {
            return Files.createTempDirectory("collection-tests-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Checkpoints {
        @Bean
        Fault fault() {
            return new Fault();
        }
    }

    protected static class Fault implements CollectionCheckpoint {
        private final AtomicReference<String> at = new AtomicReference<>();

        public void failAt(String checkpoint) {
            at.set(checkpoint);
        }

        @Override
        public void reached(String name) {
            String configured = at.get();
            if (name.equals(configured) && at.compareAndSet(configured, null)) {
                throw new IllegalStateException("Injected failure at " + name);
            }
        }
    }
}
