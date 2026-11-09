package org.ursc.trajectory.app.gui;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.ursc.trajectory.data.DataCatalog;
import org.ursc.trajectory.data.FolderWatcher;
import org.ursc.trajectory.pipeline.DemoDataset;
import org.ursc.trajectory.pipeline.InputFiles;
import org.ursc.trajectory.pipeline.OemExport;
import org.ursc.trajectory.pipeline.ReferenceComparison;
import org.ursc.trajectory.pipeline.ProgressListener;
import org.ursc.trajectory.pipeline.RunConfig;
import org.ursc.trajectory.pipeline.RunResult;
import org.ursc.trajectory.pipeline.Runs;
import org.ursc.trajectory.pipeline.TimeUtil;
import org.ursc.trajectory.search.SearchService;
import org.ursc.trajectory.util.Json;

/**
 * Local web GUI server. Binds to the loopback interface only, so nothing is exposed to the
 * network and no internet access is ever needed; every page asset ships inside the jar.
 * API calls require the per-session token from the launch URL and a loopback Host header
 * (defeats cross-site requests and DNS rebinding from other web pages).
 */
public final class GuiServer implements AutoCloseable {

    private final HttpServer http;
    private final String token;
    private final Path workspace;
    private final DataCatalog catalog;
    private final FolderWatcher watcher;
    private final RunStore runs;
    private final SearchService search;
    private final ExecutorService jobs = Executors.newSingleThreadExecutor(r -> daemon(r, "ursc-run"));
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "ursc-sse"));
    private final List<OutputStream> clients = new CopyOnWriteArrayList<>();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private volatile Job current;

    /** Running job state. */
    private static final class Job {
        final String id;
        volatile boolean cancel;
        volatile String stage = "queued";
        volatile double fraction;

        Job(final String id) {
            this.id = id;
        }
    }

    private static Thread daemon(final Runnable r, final String name) {
        final Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    public GuiServer(final int port, final Path workspace) throws IOException {
        this.workspace = Files.createDirectories(workspace);
        final byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        this.token = HexFormat.of().formatHex(b);
        this.catalog = new DataCatalog(workspace);
        this.runs = new RunStore(workspace);
        this.search = new SearchService(workspace);
        this.watcher = new FolderWatcher(catalog,
                added -> broadcast(Map.of("type", "catalog", "added", added)),
                err -> broadcast(Map.of("type", "error", "message", err)));
        for (final String f : catalog.watchedFolders()) {
            try {
                watcher.watch(f);
            } catch (final IOException e) {
                System.err.println("cannot watch " + f + ": " + e.getMessage());
            }
        }
        this.http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        this.http.setExecutor(Executors.newCachedThreadPool(r -> daemon(r, "ursc-http")));
        this.http.createContext("/", this::handle);
        this.ticker.scheduleAtFixedRate(() -> send(": keep-alive\n\n"), 15, 15, TimeUnit.SECONDS);
    }

    public void start() {
        http.start();
    }

    public int port() {
        return http.getAddress().getPort();
    }

    public String token() {
        return token;
    }

    /** URL to open: the token travels in the fragment, which browsers never send to servers. */
    public String url() {
        return "http://127.0.0.1:" + port() + "/#t=" + token;
    }

    public void awaitShutdown() throws InterruptedException {
        stopped.await();
    }

    @Override
    public void close() {
        try {
            watcher.close();
        } catch (final IOException ignored) {
            // shutting down
        }
        ticker.shutdownNow();
        jobs.shutdownNow();
        http.stop(0);
        stopped.countDown();
    }

    // ---------------------------------------------------------------- routing

    private void handle(final HttpExchange ex) throws IOException {
        try {
            final String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                if (!loopbackHost(ex)) {
                    json(ex, 403, Map.of("error", "requests must address 127.0.0.1 or localhost"));
                    return;
                }
                if (!token.equals(ex.getRequestHeaders().getFirst("X-URSC-Token"))
                        && !token.equals(query(ex).get("t"))) {
                    json(ex, 401, Map.of("error", "missing or wrong session token; reopen the URL printed at start-up"));
                    return;
                }
                api(ex, path.substring(5), ex.getRequestMethod().toUpperCase(Locale.US));
            } else {
                staticFile(ex, path);
            }
        } catch (final IllegalArgumentException e) {
            json(ex, 400, Map.of("error", String.valueOf(e.getMessage())));
        } catch (final Exception e) {
            json(ex, 500, Map.of("error", e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    private boolean loopbackHost(final HttpExchange ex) {
        final String host = ex.getRequestHeaders().getFirst("Host");
        if (host == null) {
            return false;
        }
        final String h = host.replaceAll(":\\d+$", "");
        return h.equals("127.0.0.1") || h.equals("localhost") || h.equals("[::1]");
    }

    @SuppressWarnings("unchecked")
    private void api(final HttpExchange ex, final String route, final String method) throws Exception {
        final String[] p = route.split("/");
        switch (p[0]) {
            case "status": {
                final Map<String, Object> m = new LinkedHashMap<>();
                m.put("workspace", workspace.toString());
                final Job j = current;
                m.put("running", j == null ? null : Map.of("id", j.id, "stage", j.stage, "fraction", j.fraction));
                m.put("search", search.status());
                json(ex, 200, m);
                return;
            }
            case "events":
                sse(ex);
                return;
            case "fs":
                json(ex, 200, listDir(query(ex).get("path")));
                return;
            case "config":
                if (p.length > 1 && "default".equals(p[1])) {
                    json(ex, 200, Json.parseObject(new RunConfig().toJson()));
                } else if (p.length > 1 && "validate".equals(p[1])) {
                    final List<String> warn = new ArrayList<>();
                    final RunConfig cfg = RunConfig.fromJson(body(ex), warn);
                    json(ex, 200, Map.of("errors", Runs.validate(cfg), "warnings", warn));
                } else {
                    json(ex, 404, Map.of("error", "unknown config route"));
                }
                return;
            case "catalog":
                catalogApi(ex, p.length > 1 ? p[1] : "", method);
                return;
            case "run":
                startRun(ex);
                return;
            case "demo":
                startDemo(ex);
                return;
            case "runs":
                runsApi(ex, p, method);
                return;
            case "presets":
                presetsApi(ex, p, method);
                return;
            case "search": {
                final Map<String, String> q = query(ex);
                json(ex, 200, search.search(q.getOrDefault("q", ""), Integer.parseInt(q.getOrDefault("n", "8"))));
                return;
            }
            case "shutdown":
                json(ex, 200, Map.of("ok", true));
                new Thread(this::close).start();
                return;
            default:
                json(ex, 404, Map.of("error", "unknown API route " + route));
        }
    }

    @SuppressWarnings("unchecked")
    private void catalogApi(final HttpExchange ex, final String op, final String method) throws Exception {
        switch (op) {
            case "": {
                final Map<String, Object> m = new LinkedHashMap<>();
                m.put("entries", catalog.entries());
                m.put("watched", catalog.watchedFolders());
                final List<Map<String, String>> kinds = new ArrayList<>();
                for (final DataCatalog.Kind k : DataCatalog.Kind.values()) {
                    kinds.add(Map.of("name", k.name(), "label", k.label, "role", k.role));
                }
                m.put("kinds", kinds);
                json(ex, 200, m);
                return;
            }
            case "add": {
                final List<String> paths = (List<String>) (List<?>) Json.parseObject(body(ex)).get("paths");
                json(ex, 200, Map.of("added", catalog.add(paths)));
                return;
            }
            case "watch":
                watcher.watch(String.valueOf(Json.parseObject(body(ex)).get("folder")));
                json(ex, 200, Map.of("watched", catalog.watchedFolders()));
                return;
            case "unwatch":
                catalog.removeWatched(String.valueOf(Json.parseObject(body(ex)).get("folder")));
                json(ex, 200, Map.of("watched", catalog.watchedFolders()));
                return;
            case "rescan":
                json(ex, 200, Map.of("changed", catalog.rescan()));
                return;
            case "remove":
                catalog.remove(String.valueOf(Json.parseObject(body(ex)).get("path")));
                json(ex, 200, Map.of("ok", true));
                return;
            case "suggest": {
                final Map<String, Object> b = Json.parseObject(body(ex));
                json(ex, 200, catalog.suggest(TimeUtil.parseUtc(String.valueOf(b.get("cutoffUtc"))),
                        num(b.get("arcHours"), 6), num(b.get("spanDays"), 7), "RECONSTRUCTION".equals(b.get("mode")),
                        b.get("satellite") == null ? null : String.valueOf(b.get("satellite"))));
                return;
            }
            default:
                json(ex, 404, Map.of("error", "unknown catalog route"));
        }
    }

    private static double num(final Object o, final double def) {
        return o instanceof Number ? ((Number) o).doubleValue() : def;
    }

    private void startRun(final HttpExchange ex) throws IOException {
        final List<String> warn = new ArrayList<>();
        final RunConfig cfg = RunConfig.fromJson(body(ex), warn);
        final List<String> errors = Runs.validate(cfg);
        if (!errors.isEmpty()) {
            json(ex, 400, Map.of("errors", errors));
            return;
        }
        if (current != null) {
            json(ex, 409, Map.of("error", "a run is already in progress (" + current.id + ")"));
            return;
        }
        final Job job = new Job(runs.newId());
        current = job;
        jobs.submit(() -> execute(job, cfg, warn));
        json(ex, 202, Map.of("id", job.id));
    }

    private void execute(final Job job, final RunConfig cfg, final List<String> configWarnings) {
        final List<String> log = new CopyOnWriteArrayList<>(configWarnings);
        final ProgressListener pl = new ProgressListener() {
            public void stage(final String name, final double fraction) {
                job.stage = name;
                job.fraction = fraction;
                broadcast(Map.of("type", "progress", "id", job.id, "stage", name, "fraction", fraction));
            }

            public void log(final String message) {
                log.add(message);
                broadcast(Map.of("type", "log", "id", job.id, "message", message));
            }

            public boolean cancelled() {
                return job.cancel;
            }
        };
        RunResult res = null;
        try {
            broadcast(Map.of("type", "started", "id", job.id));
            res = Runs.execute(cfg, pl);
        } catch (final Exception e) {
            res = new RunResult();
            res.status = RunResult.Status.ERROR;
            res.errors.add("internal error: " + e);
            e.printStackTrace();
        }
        Map<String, Object> traj = null;
        Map<String, Object> ref = null;
        String oem = null;
        if (res.ok() && !res.states.isEmpty()) {
            pl.stage("Writing OEM and visualization data", 0.99);
            // each output is attempted on its own: one failing never loses the others or the run record
            try {
                oem = OemExport.toOem(res, cfg).toKvn();
            } catch (final Exception e) {
                res.warnings.add("OEM could not be written: " + e);
                e.printStackTrace();
            }
            try {
                traj = TrajectoryView.build(res);
            } catch (final Exception e) {
                res.warnings.add("Visualization data could not be built: " + e);
                e.printStackTrace();
            }
            if (!cfg.inputs.poeorb.isEmpty()) {
                ref = reference(cfg, res, log);
            }
        }
        log.addAll(res.log);
        try {
            runs.save(job.id, cfg, res, traj, oem, log, ref);
        } catch (final Exception e) {
            e.printStackTrace();
            broadcast(Map.of("type", "error", "message", "Run " + job.id + " could not be saved: " + e));
        } finally {
            current = null;
            broadcast(Map.of("type", "done", "id", job.id, "status", res.status.name()));
        }
    }

    /** POEORB comparison for display: never fed back into the run. */
    private static Map<String, Object> reference(final RunConfig cfg, final RunResult res, final List<String> log) {
        try {
            final ReferenceComparison.Result c = ReferenceComparison.compare(InputFiles.expand(cfg.inputs.poeorb), res.states);
            if (c.runIndex.isEmpty()) {
                log.add("POEORB reference does not overlap the output span; no comparison");
                return null;
            }
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("runIndex", c.runIndex);
            m.put("err3d", c.err3d);
            m.put("radial", c.radial);
            m.put("along", c.along);
            m.put("cross", c.cross);
            m.put("track", TrajectoryView.build(c.reference));
            return m;
        } catch (final Exception e) {
            log.add("POEORB comparison failed: " + e);
            e.printStackTrace();
            return null;
        }
    }

    private void startDemo(final HttpExchange ex) throws IOException {
        final String folder = String.valueOf(Json.parseObject(body(ex)).get("folder"));
        final Path dir = Paths.get(folder).resolve("ursc-demo-data");
        if (current != null) {
            json(ex, 409, Map.of("error", "a run is in progress; try again when it finishes"));
            return;
        }
        jobs.submit(() -> {
            try {
                DemoDataset.write(dir, ProgressListener.NONE);
                watcher.watch(dir.toString());
                broadcast(Map.of("type", "demo", "status", "ok", "folder", dir.toString(), "cutoffUtc", DemoDataset.CUTOFF_UTC));
            } catch (final Exception e) {
                broadcast(Map.of("type", "demo", "status", "error", "message", String.valueOf(e.getMessage())));
            }
        });
        json(ex, 202, Map.of("folder", dir.toString()));
    }

    private void runsApi(final HttpExchange ex, final String[] p, final String method) throws Exception {
        if (p.length == 1) {
            json(ex, 200, Map.of("runs", runs.list()));
            return;
        }
        final Path d = runs.dir(p[1]);
        if ("DELETE".equals(method)) {
            runs.delete(p[1]);
            json(ex, 200, Map.of("ok", true));
            return;
        }
        final String what = p.length > 2 ? p[2] : "";
        switch (what) {
            case "": {
                final Map<String, Object> m = new LinkedHashMap<>();
                m.put("result", Json.parse(Files.readString(d.resolve("result.json"))));
                m.put("config", Json.parse(Files.readString(d.resolve("config.json"))));
                json(ex, 200, m);
                return;
            }
            case "trajectory":
                raw(ex, d.resolve("trajectory.json"), "application/json", null);
                return;
            case "reference":
                raw(ex, d.resolve("reference.json"), "application/json", null);
                return;
            case "oem":
                raw(ex, d.resolve("output.oem"), "text/plain; charset=us-ascii", p[1] + ".oem");
                return;
            case "log":
                raw(ex, d.resolve("log.txt"), "text/plain; charset=utf-8", null);
                return;
            case "cancel": {
                final Job j = current;
                if (j != null && j.id.equals(p[1])) {
                    j.cancel = true;
                }
                json(ex, 200, Map.of("ok", true));
                return;
            }
            default:
                json(ex, 404, Map.of("error", "unknown run route"));
        }
    }

    private void presetsApi(final HttpExchange ex, final String[] p, final String method) throws IOException {
        final Path dir = Files.createDirectories(workspace.resolve("presets"));
        if (p.length == 1 && "GET".equals(method)) {
            final List<String> names = new ArrayList<>();
            try (Stream<Path> s = Files.list(dir)) {
                s.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".json")).sorted()
                        .forEach(n -> names.add(n.substring(0, n.length() - 5)));
            }
            json(ex, 200, Map.of("presets", names));
            return;
        }
        if (p.length == 1 && "POST".equals(method)) {
            final Map<String, Object> b = Json.parseObject(body(ex));
            final String name = safeName(String.valueOf(b.get("name")));
            final RunConfig cfg = RunConfig.fromJson(Json.write(b.get("config")), new ArrayList<>());
            cfg.save(dir.resolve(name + ".json"));
            json(ex, 200, Map.of("ok", true, "name", name));
            return;
        }
        final Path f = dir.resolve(safeName(URLDecoder.decode(p[1], StandardCharsets.UTF_8)) + ".json");
        if ("DELETE".equals(method)) {
            Files.deleteIfExists(f);
            json(ex, 200, Map.of("ok", true));
        } else {
            json(ex, 200, Json.parse(Files.readString(f)));
        }
    }

    private static String safeName(final String n) {
        final String s = n.replaceAll("[^0-9A-Za-z _.\\-]", "_").trim();
        if (s.isEmpty() || s.startsWith(".")) {
            throw new IllegalArgumentException("invalid preset name");
        }
        return s;
    }

    private Map<String, Object> listDir(final String requested) throws IOException {
        Path dir = requested == null || requested.isBlank()
                ? Paths.get(System.getProperty("user.home")) : Paths.get(requested);
        dir = dir.toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            dir = dir.getParent();
        }
        final List<Map<String, Object>> items = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            for (final Path c : (Iterable<Path>) s::iterator) {
                final String n = c.getFileName().toString();
                if (n.startsWith(".")) {
                    continue;
                }
                final Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", n);
                m.put("path", c.toString());
                final boolean isDir = Files.isDirectory(c);
                m.put("dir", isDir);
                m.put("size", isDir ? 0 : Files.size(c));
                items.add(m);
            }
        }
        items.sort((a, b) -> {
            final int c = Boolean.compare((Boolean) b.get("dir"), (Boolean) a.get("dir"));
            return c != 0 ? c : String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name")));
        });
        final List<String> roots = new ArrayList<>();
        for (final File r : File.listRoots()) {
            roots.add(r.getPath());
        }
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("path", dir.toString());
        m.put("parent", dir.getParent() == null ? null : dir.getParent().toString());
        m.put("home", System.getProperty("user.home"));
        m.put("roots", roots);
        m.put("items", items);
        return m;
    }

    // ---------------------------------------------------------------- server-sent events

    private void sse(final HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, 0);
        final OutputStream os = ex.getResponseBody();
        os.write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
        os.flush();
        clients.add(os); // the exchange stays open; send() removes it when the browser goes away
    }

    void broadcast(final Map<String, Object> event) {
        send("data: " + Json.writeCompact(event) + "\n\n");
    }

    private void send(final String frame) {
        final byte[] b = frame.getBytes(StandardCharsets.UTF_8);
        for (final OutputStream os : clients) {
            try {
                synchronized (os) {
                    os.write(b);
                    os.flush();
                }
            } catch (final IOException e) {
                clients.remove(os);
                try {
                    os.close();
                } catch (final IOException ignored) {
                    // already closed
                }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private static String body(final HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> query(final HttpExchange ex) {
        final Map<String, String> m = new LinkedHashMap<>();
        final String q = ex.getRequestURI().getRawQuery();
        if (q != null) {
            for (final String kv : q.split("&")) {
                final int i = kv.indexOf('=');
                if (i > 0) {
                    m.put(URLDecoder.decode(kv.substring(0, i), StandardCharsets.UTF_8),
                            URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8));
                }
            }
        }
        return m;
    }

    private static void json(final HttpExchange ex, final int code, final Object body) throws IOException {
        final byte[] b = Json.writeCompact(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    private static void raw(final HttpExchange ex, final Path f, final String type, final String download)
            throws IOException {
        if (!Files.isRegularFile(f)) {
            json(ex, 404, Map.of("error", "not available for this run"));
            return;
        }
        ex.getResponseHeaders().set("Content-Type", type);
        if (download != null) {
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + download + "\"");
        }
        ex.sendResponseHeaders(200, Files.size(f));
        try (OutputStream os = ex.getResponseBody()) {
            Files.copy(f, os);
        }
    }

    private void staticFile(final HttpExchange ex, final String path) throws IOException {
        String p = "/".equals(path) ? "/index.html" : path;
        if (p.contains("..") || p.contains("//")) {
            json(ex, 400, Map.of("error", "bad path"));
            return;
        }
        try (InputStream in = GuiServer.class.getResourceAsStream("/gui" + p)) {
            if (in == null) {
                json(ex, 404, Map.of("error", "not found: " + p));
                return;
            }
            final byte[] b = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", contentType(p));
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            ex.getResponseHeaders().set("Content-Security-Policy",
                    "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; connect-src 'self'");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(b);
            }
        }
    }

    private static String contentType(final String p) {
        if (p.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (p.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        }
        if (p.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (p.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (p.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (p.endsWith(".md") || p.endsWith(".txt")) {
            return "text/plain; charset=utf-8";
        }
        return "application/octet-stream";
    }
}
