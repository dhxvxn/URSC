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
import org.ursc.trajectory.io.Oem;
import org.ursc.trajectory.io.TrackExport;
import org.ursc.trajectory.pipeline.OemExport;
import org.ursc.trajectory.pipeline.Passes;
import org.ursc.trajectory.pipeline.RunCompare;
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
    private volatile boolean sweeping;
    private volatile boolean sweepCancel;
    private volatile String indexing;

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
                m.put("indexing", indexing);
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
                    json(ex, 200, showcaseDefaults());
                } else if (p.length > 1 && "generic".equals(p[1])) {
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
            case "compare":
                json(ex, 200, compare(query(ex).getOrDefault("ids", "")));
                return;
            case "stations":
                stationsApi(ex, method);
                return;
            case "sweep":
                startSweep(ex);
                return;
            case "window":
                startWindow(ex);
                return;
            case "windows":
                windowsApi(ex, p, method);
                return;
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
        if (current != null || sweeping) {
            json(ex, 409, Map.of("error", "a run is already in progress" + (current == null ? "" : " (" + current.id + ")")));
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
            case "kml":
            case "czml": {
                final Map<String, Object> t = trajectory(d);
                if (t == null) {
                    json(ex, 404, Map.of("error", "no trajectory for this run"));
                    return;
                }
                final Object on = Json.parseObject(Files.readString(d.resolve("result.json"))).get("objectName");
                final String name = on == null || String.valueOf(on).isBlank() ? "satellite" : String.valueOf(on);
                final String text = "kml".equals(what)
                        ? TrackExport.kml(name, strings(t.get("epochs")), RunReport.nums(t.get("lat")), RunReport.nums(t.get("lon")), RunReport.nums(t.get("alt")))
                        : TrackExport.czml(name, strings(t.get("epochs")), RunReport.nums(t.get("t")), RunReport.nums(t.get("lat")),
                                RunReport.nums(t.get("lon")), RunReport.nums(t.get("alt")));
                text(ex, "kml".equals(what) ? "application/vnd.google-earth.kml+xml" : "application/json", text,
                        p[1] + ("kml".equals(what) ? ".kml" : ".czml"));
                return;
            }
            case "passes":
                json(ex, 200, Map.of("passes", passes(d), "stations", stations()));
                return;
            case "report": {
                final Map<String, Object> res = Json.parseObject(Files.readString(d.resolve("result.json")));
                final Map<String, Object> cfg = Json.parseObject(Files.readString(d.resolve("config.json")));
                final Path rf = d.resolve("reference.json");
                final Map<String, Object> ref = Files.isRegularFile(rf) ? Json.parseObject(Files.readString(rf)) : null;
                text(ex, "text/html; charset=utf-8", RunReport.build(res, cfg, showcaseDefaults(),
                        trajectory(d), ref, passes(d)), null);
                return;
            }
            case "cancel": {
                final Job j = current;
                if (j != null && j.id.equals(p[1])) {
                    j.cancel = true;
                    sweepCancel = true;
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

    // ---- run extras: trajectory exports, passes, comparison, sweep ----

    private static Map<String, Object> trajectory(final Path runDir) throws IOException {
        final Path f = runDir.resolve("trajectory.json");
        return Files.isRegularFile(f) ? Json.parseObject(Files.readString(f)) : null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(final Object o) {
        final List<String> out = new ArrayList<>();
        for (final Object v : (List<Object>) o) {
            out.add(String.valueOf(v));
        }
        return out;
    }

    private static void text(final HttpExchange ex, final String type, final String body, final String download)
            throws IOException {
        final byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        if (download != null) {
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + download + "\"");
        }
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    private Path stationsFile() {
        return workspace.resolve("stations.json");
    }

    @SuppressWarnings("unchecked")
    private List<Object> stations() throws IOException {
        final Path f = stationsFile();
        if (!Files.isRegularFile(f)) {
            return new ArrayList<>();
        }
        final Object o = Json.parseObject(Files.readString(f)).get("stations");
        return o instanceof List ? (List<Object>) o : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private void stationsApi(final HttpExchange ex, final String method) throws IOException {
        if ("GET".equals(method)) {
            json(ex, 200, Map.of("stations", stations()));
            return;
        }
        final Object list = Json.parseObject(body(ex)).get("stations");
        if (!(list instanceof List)) {
            json(ex, 400, Map.of("error", "expected {stations:[...]}"));
            return;
        }
        final List<Object> clean = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        for (final Object o : (List<Object>) list) {
            final Map<String, Object> m = (Map<String, Object>) o;
            final double lat = num(m.get("latDeg"), Double.NaN);
            final double lon = num(m.get("lonDeg"), Double.NaN);
            final String name = String.valueOf(m.getOrDefault("name", "")).trim();
            if (name.isEmpty() || !(Math.abs(lat) <= 90) || !(Math.abs(lon) <= 360)) {
                errors.add("station '" + name + "': name, latitude (-90..90) and longitude are required");
                continue;
            }
            final Map<String, Object> c = new LinkedHashMap<>();
            c.put("name", name);
            c.put("latDeg", lat);
            c.put("lonDeg", lon > 180 ? lon - 360 : lon);
            c.put("heightM", num(m.get("heightM"), 0));
            c.put("minElevationDeg", num(m.get("minElevationDeg"), 5));
            clean.add(c);
        }
        if (!errors.isEmpty()) {
            json(ex, 400, Map.of("errors", errors));
            return;
        }
        Files.writeString(stationsFile(), Json.write(Map.of("stations", clean)));
        json(ex, 200, Map.of("stations", clean));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> passes(final Path runDir) throws IOException {
        final List<Map<String, Object>> out = new ArrayList<>();
        final Map<String, Object> t = trajectory(runDir);
        final List<Object> st = stations();
        if (t == null || st.isEmpty()) {
            return out;
        }
        final List<Double> tt = RunReport.nums(t.get("t"));
        final List<Double> xk = RunReport.nums(t.get("itrf"));
        final double[] ta = new double[tt.size()];
        final double[] xa = new double[xk.size()];
        for (int i = 0; i < ta.length; i++) {
            ta[i] = tt.get(i);
        }
        for (int i = 0; i < xa.length; i++) {
            xa[i] = xk.get(i) * 1000.0;
        }
        final org.ursc.trajectory.time.AbsoluteDate t0 = TimeUtil.parseUtc(String.valueOf(((List<Object>) t.get("epochs")).get(0)));
        for (final Object o : st) {
            final Map<String, Object> m = (Map<String, Object>) o;
            final Passes.Station s = new Passes.Station();
            s.name = String.valueOf(m.get("name"));
            s.latDeg = num(m.get("latDeg"), 0);
            s.lonDeg = num(m.get("lonDeg"), 0);
            s.heightM = num(m.get("heightM"), 0);
            s.minElevationDeg = num(m.get("minElevationDeg"), 5);
            for (final Passes.Pass p : Passes.compute(s, ta, xa)) {
                final Map<String, Object> r = new LinkedHashMap<>();
                r.put("station", p.station);
                r.put("aosUtc", TimeUtil.formatUtc(t0.shiftedBy(p.aos)));
                r.put("losUtc", TimeUtil.formatUtc(t0.shiftedBy(p.los)));
                r.put("maxUtc", TimeUtil.formatUtc(t0.shiftedBy(p.maxTime)));
                r.put("aos", p.aos);
                r.put("los", p.los);
                r.put("durationS", p.los - p.aos);
                r.put("maxElevationDeg", Math.round(p.maxElevationDeg * 100) / 100.0);
                r.put("truncated", p.truncated);
                out.add(r);
            }
        }
        out.sort((a, b) -> Double.compare((Double) a.get("aos"), (Double) b.get("aos")));
        return out;
    }

    private static List<Double> thin(final List<Double> v, final int stride) {
        final List<Double> o = new ArrayList<>();
        for (int i = 0; i < v.size(); i += stride) {
            o.add(Math.round(v.get(i) * 1000.0) / 1000.0);
        }
        return o;
    }

    private Map<String, Object> compare(final String idList) throws IOException {
        final List<String> ids = new ArrayList<>();
        for (final String s : idList.split(",")) {
            if (!s.isBlank()) {
                ids.add(s.trim());
            }
        }
        if (ids.size() < 2) {
            throw new IllegalArgumentException("choose at least two runs");
        }
        final List<Map<String, Object>> runInfo = new ArrayList<>();
        final List<Map<String, Object>> flat = new ArrayList<>();
        for (final String id : ids) {
            final Path d = runs.dir(id);
            final Map<String, Object> res = Json.parseObject(Files.readString(d.resolve("result.json")));
            final Map<String, Object> m = new LinkedHashMap<>();
            for (final String k : List.of("id", "runName", "status", "label", "cutoffUtc", "states", "flags")) {
                m.put(k, res.get(k));
            }
            if (res.get("od") instanceof Map) {
                m.put("postFitRms", ((Map<?, ?>) res.get("od")).get("postFitRms"));
            }
            runInfo.add(m);
            final Map<String, Object> f = new LinkedHashMap<>();
            RunReport.flatten("", Json.parseObject(Files.readString(d.resolve("config.json"))), f);
            flat.add(f);
        }
        final List<Map<String, Object>> settings = new ArrayList<>();
        for (final String k : flat.get(0).keySet()) {
            if (k.equals("runName")) {
                continue;
            }
            final List<Object> vals = new ArrayList<>();
            boolean differs = false;
            for (final Map<String, Object> f : flat) {
                vals.add(f.get(k));
                differs |= !String.valueOf(f.get(k)).equals(String.valueOf(flat.get(0).get(k)));
            }
            if (differs) {
                settings.add(Map.of("key", k, "values", vals));
            }
        }
        final Path baseOem = runs.dir(ids.get(0)).resolve("output.oem");
        final List<Map<String, Object>> diffs = new ArrayList<>();
        for (int i = 1; i < ids.size(); i++) {
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ids.get(i));
            try {
                final Path o = runs.dir(ids.get(i)).resolve("output.oem");
                if (!Files.isRegularFile(baseOem) || !Files.isRegularFile(o)) {
                    throw new IllegalArgumentException("a run has no OEM output");
                }
                final RunCompare.Result r = RunCompare.compare(Oem.read(baseOem), Oem.read(o));
                final int stride = Math.max(1, r.t.size() / 2000);
                m.put("frame", r.frame);
                m.put("t", thin(r.t, stride));
                m.put("err3d", thin(r.err3d, stride));
                m.put("radial", thin(r.radial, stride));
                m.put("along", thin(r.along, stride));
                m.put("cross", thin(r.cross, stride));
                m.put("max3d", r.max3d);
                m.put("rms3d", r.rms3d);
                m.put("final3d", r.final3d);
                m.put("firstEpoch", r.epochs.get(0));
            } catch (final RuntimeException e) {
                m.put("error", e.getMessage());
            }
            diffs.add(m);
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("base", ids.get(0));
        out.put("runs", runInfo);
        out.put("settings", settings);
        out.put("diffs", diffs);
        return out;
    }

    /** Set a dotted key in a nested JSON map. */
    @SuppressWarnings("unchecked")
    private static void setPath(final Map<String, Object> root, final String key, final Object value) {
        final String[] parts = key.split("\\.");
        Map<String, Object> m = root;
        for (int i = 0; i < parts.length - 1; i++) {
            final Object n = m.get(parts[i]);
            if (!(n instanceof Map)) {
                throw new IllegalArgumentException("unknown setting " + key);
            }
            m = (Map<String, Object>) n;
        }
        if (!m.containsKey(parts[parts.length - 1])) {
            throw new IllegalArgumentException("unknown setting " + key);
        }
        m.put(parts[parts.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private void startSweep(final HttpExchange ex) throws IOException {
        final Map<String, Object> b = Json.parseObject(body(ex));
        final String key = String.valueOf(b.get("key"));
        final Object vals = b.get("values");
        if (!(vals instanceof List) || ((List<?>) vals).isEmpty() || ((List<?>) vals).size() > 12) {
            json(ex, 400, Map.of("error", "give 1 to 12 values to sweep"));
            return;
        }
        if (current != null || sweeping) {
            json(ex, 409, Map.of("error", "a run is already in progress"));
            return;
        }
        final String baseJson = Json.write(b.get("config"));
        final List<RunConfig> cfgs = new ArrayList<>();
        final List<List<String>> warns = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        for (final Object v : (List<Object>) vals) {
            final Map<String, Object> m = Json.parseObject(baseJson);
            try {
                setPath(m, key, v);
            } catch (final IllegalArgumentException e) {
                json(ex, 400, Map.of("error", e.getMessage()));
                return;
            }
            final String base = String.valueOf(m.getOrDefault("runName", ""));
            m.put("runName", (base.isBlank() ? "sweep" : base) + " [" + key + "=" + v + "]");
            final List<String> w = new ArrayList<>();
            final RunConfig c = RunConfig.fromJson(Json.write(m), w);
            for (final String e : Runs.validate(c)) {
                errors.add(key + "=" + v + ": " + e);
            }
            cfgs.add(c);
            warns.add(w);
        }
        if (!errors.isEmpty()) {
            json(ex, 400, Map.of("errors", errors));
            return;
        }
        sweeping = true;
        sweepCancel = false;
        final List<String> ids = new ArrayList<>();
        for (int i = 0; i < cfgs.size(); i++) {
            ids.add(runs.newId() + "-s" + i);
        }
        jobs.submit(() -> {
            try {
                for (int i = 0; i < cfgs.size() && !sweepCancel; i++) {
                    final Job job = new Job(ids.get(i));
                    current = job;
                    broadcast(Map.of("type", "sweep-progress", "index", i, "count", cfgs.size(), "id", job.id));
                    execute(job, cfgs.get(i), warns.get(i));
                }
            } finally {
                sweeping = false;
                broadcast(Map.of("type", "sweep", "ids", ids, "key", key, "cancelled", sweepCancel));
            }
        });
        json(ex, 202, Map.of("ids", ids));
    }

    // ---- startup defaults, project data, window runs ----

    /** Startup defaults: the library defaults overlaid with the bundled showcase profile (tested window). */
    @SuppressWarnings("unchecked")
    Map<String, Object> showcaseDefaults() throws IOException {
        final Map<String, Object> base = Json.parseObject(new RunConfig().toJson());
        try (InputStream in = GuiServer.class.getResourceAsStream("/gui/profiles/showcase.json")) {
            if (in != null) {
                final Map<String, Object> prof = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                prof.remove("_about");
                merge(base, prof);
            }
        }
        return base;
    }

    @SuppressWarnings("unchecked")
    private static void merge(final Map<String, Object> into, final Map<String, Object> from) {
        for (final Map.Entry<String, Object> e : from.entrySet()) {
            final Object cur = into.get(e.getKey());
            if (cur instanceof Map && e.getValue() instanceof Map) {
                merge((Map<String, Object>) cur, (Map<String, Object>) e.getValue());
            } else if (into.containsKey(e.getKey())) {
                into.put(e.getKey(), e.getValue());
            }
        }
    }

    /**
     * Adds folders to the catalog in the background (first start with project data next to the
     * program). Progress is reported through the status route and SSE events.
     */
    public void registerInBackground(final List<String> folders) {
        final Thread t = new Thread(() -> {
            final List<DataCatalog.Entry> added = new ArrayList<>();
            for (final String f : folders) {
                indexing = f;
                broadcast(Map.of("type", "indexing", "folder", f));
                try {
                    added.addAll(catalog.add(List.of(f)));
                } catch (final Exception e) {
                    broadcast(Map.of("type", "error", "message", "Could not index " + f + ": " + e.getMessage()));
                }
            }
            indexing = null;
            final List<Map<String, String>> names = new ArrayList<>();
            for (final DataCatalog.Entry e : added) {
                names.add(Map.of("name", e.name, "kind", e.kind.name()));
            }
            broadcast(Map.of("type", "catalog", "added", names));
        }, "ursc-index");
        t.setDaemon(true);
        t.start();
    }

    public boolean catalogEmpty() {
        return catalog.entries().isEmpty();
    }

    private Path windowsDir() throws IOException {
        return Files.createDirectories(workspace.resolve("windows"));
    }

    private synchronized void saveWindow(final Map<String, Object> w) throws IOException {
        Files.writeString(windowsDir().resolve(w.get("id") + ".json"), Json.write(w));
    }

    @SuppressWarnings("unchecked")
    private void windowsApi(final HttpExchange ex, final String[] p, final String method) throws IOException {
        final Path dir = windowsDir();
        if (p.length == 1) {
            final List<Map<String, Object>> out = new ArrayList<>();
            try (Stream<Path> s = Files.list(dir)) {
                for (final Path f : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".json"))::iterator) {
                    final Map<String, Object> w = Json.parseObject(Files.readString(f));
                    w.remove("days");
                    out.add(w);
                }
            }
            out.sort((a, b) -> String.valueOf(b.get("id")).compareTo(String.valueOf(a.get("id"))));
            json(ex, 200, Map.of("windows", out));
            return;
        }
        final String id = p[1];
        if (!id.matches("[0-9A-Za-z\\-]+")) {
            json(ex, 400, Map.of("error", "bad window id"));
            return;
        }
        final Path f = dir.resolve(id + ".json");
        if ("DELETE".equals(method)) {
            Files.deleteIfExists(f);
            json(ex, 200, Map.of("ok", true));
            return;
        }
        if (!Files.isRegularFile(f)) {
            json(ex, 404, Map.of("error", "no such window run"));
            return;
        }
        json(ex, 200, Json.parseObject(Files.readString(f)));
    }

    @SuppressWarnings("unchecked")
    private void startWindow(final HttpExchange ex) throws IOException {
        if (current != null || sweeping) {
            json(ex, 409, Map.of("error", "a run is already in progress"));
            return;
        }
        final List<String> warn = new ArrayList<>();
        final RunConfig base = RunConfig.fromJson(body(ex), warn);
        final java.time.LocalDate a;
        final java.time.LocalDate b;
        final java.time.LocalTime tod;
        try {
            a = java.time.LocalDate.parse(base.window.startDate.trim());
            b = java.time.LocalDate.parse(base.window.endDate.trim());
            tod = java.time.LocalTime.parse(base.window.cutoffTimeUtc.trim());
        } catch (final RuntimeException e) {
            json(ex, 400, Map.of("error", "set the window's first date, last date (yyyy-mm-dd) and cutoff time (hh:mm:ss)"));
            return;
        }
        final int step = Math.max(1, base.window.stepDays);
        final List<java.time.LocalDate> days = new ArrayList<>();
        for (java.time.LocalDate d = a; !d.isAfter(b); d = d.plusDays(step)) {
            days.add(d);
        }
        if (days.isEmpty() || days.size() > 400) {
            json(ex, 400, Map.of("error", "the window must hold 1 to 400 cutoffs (has " + days.size() + ")"));
            return;
        }
        final String todText = tod.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        final Map<String, Object> w = new LinkedHashMap<>();
        w.put("id", "W" + runs.newId());
        w.put("name", base.runName.isBlank() ? "window" : base.runName);
        w.put("created", java.time.LocalDateTime.now().toString());
        w.put("startDate", a.toString());
        w.put("endDate", b.toString());
        w.put("cutoffTimeUtc", todText);
        w.put("stepDays", step);
        w.put("count", days.size());
        w.put("done", 0);
        w.put("finished", false);
        w.put("cancelled", false);
        w.put("settings", Json.parseObject(base.toJson()));
        final List<Map<String, Object>> rows = new ArrayList<>();
        w.put("days", rows);
        saveWindow(w);
        sweeping = true;
        sweepCancel = false;
        jobs.submit(() -> {
            String satellite = null;
            try {
                for (int i = 0; i < days.size() && !sweepCancel; i++) {
                    final String cutoff = days.get(i) + "T" + todText;
                    final RunConfig c = RunConfig.fromJson(base.toJson(), new ArrayList<>());
                    c.cutoffUtc = cutoff;
                    c.runName = w.get("name") + " " + days.get(i);
                    final Map<String, List<String>> s = catalog.suggest(TimeUtil.parseUtc(cutoff), c.od.arcHours,
                            c.forecast.spanDays, c.products.mode == RunConfig.ProductMode.RECONSTRUCTION, satellite);
                    if (satellite == null && !s.get("satellite").isEmpty()) {
                        satellite = s.get("satellite").get(0); // keep one spacecraft for the whole window
                    }
                    c.inputs.gnssrd = s.get("gnssrd");
                    c.inputs.gpsProducts = s.get("gpsProducts");
                    c.inputs.poeorb = s.get("poeorb");
                    c.inputs.proqua = c.spacecraft.shape == RunConfig.ShapeModel.CANNONBALL ? new ArrayList<>() : s.get("proqua");
                    if (c.inputs.eopFile.isBlank() && !s.get("eopFile").isEmpty()) {
                        c.inputs.eopFile = s.get("eopFile").get(0);
                    }
                    if (c.inputs.spaceWeatherFile.isBlank() && !s.get("spaceWeatherFile").isEmpty()) {
                        c.inputs.spaceWeatherFile = s.get("spaceWeatherFile").get(0);
                    }
                    final Map<String, Object> row = new LinkedHashMap<>();
                    row.put("date", days.get(i).toString());
                    row.put("cutoffUtc", cutoff);
                    final List<String> errs = Runs.validate(c);
                    if (!errs.isEmpty()) {
                        row.put("status", "NO_DATA");
                        row.put("note", String.join("; ", errs));
                    } else {
                        final Job job = new Job(runs.newId());
                        current = job;
                        broadcast(Map.of("type", "window-progress", "window", w.get("id"), "index", i, "count", days.size(),
                                "date", days.get(i).toString(), "id", job.id));
                        execute(job, c, warn);
                        row.put("runId", job.id);
                        windowRow(runs.dir(job.id), row);
                    }
                    synchronized (this) {
                        rows.add(row);
                        w.put("done", rows.size());
                    }
                    saveWindow(w);
                    broadcast(Map.of("type", "window-day", "window", w.get("id"), "index", i, "count", days.size(), "row", row));
                }
            } catch (final Exception e) {
                e.printStackTrace();
                broadcast(Map.of("type", "error", "message", "Window run stopped: " + e));
            } finally {
                sweeping = false;
                w.put("finished", true);
                w.put("cancelled", sweepCancel);
                try {
                    saveWindow(w);
                } catch (final IOException ignored) {
                    // reported below
                }
                broadcast(Map.of("type", "window-done", "window", w.get("id"), "cancelled", sweepCancel));
            }
        });
        json(ex, 202, Map.of("id", w.get("id"), "count", days.size()));
    }

    /** Summary of one window day from the saved run files. */
    @SuppressWarnings("unchecked")
    private static void windowRow(final Path d, final Map<String, Object> row) throws IOException {
        final Map<String, Object> res = Json.parseObject(Files.readString(d.resolve("result.json")));
        row.put("status", res.get("status"));
        row.put("label", res.get("label"));
        row.put("flags", res.get("flags"));
        row.put("elapsedSeconds", res.get("elapsedSeconds"));
        row.put("errors", res.get("errors"));
        if (res.get("productUsage") instanceof Map) {
            row.put("products", new ArrayList<>(((Map<String, Object>) res.get("productUsage")).keySet()));
        }
        if (res.get("od") instanceof Map) {
            final Map<String, Object> od = (Map<String, Object>) res.get("od");
            row.put("fixes", od.get("observations"));
            row.put("postFitRms", od.get("postFitRms"));
            row.put("varianceFactor", od.get("varianceFactor"));
            row.put("converged", od.get("converged"));
        }
        final Path rf = d.resolve("reference.json");
        final Map<String, Object> traj = trajectory(d);
        if (Files.isRegularFile(rf) && traj != null) {
            final Map<String, Object> ref = Json.parseObject(Files.readString(rf));
            final List<Double> t = RunReport.nums(traj.get("t"));
            final List<Double> idx = RunReport.nums(ref.get("runIndex"));
            final List<Double> e = RunReport.nums(ref.get("err3d"));
            for (int k = 0; k < idx.size(); k++) {
                final double tk = t.get(idx.get(k).intValue());
                for (final Object[] h : new Object[][] {{"errCutoff", 0.0}, {"err1d", 86400.0}, {"err7d", 604800.0}}) {
                    if (Math.abs(tk - (Double) h[1]) < 1.0) {
                        row.put((String) h[0], e.get(k));
                    }
                }
            }
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
        String p = path.endsWith("/") ? path + "index.html" : path;
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
        if (p.endsWith(".png")) {
            return "image/png";
        }
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (p.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (p.endsWith(".webm")) {
            return "video/webm";
        }
        if (p.endsWith(".md") || p.endsWith(".txt")) {
            return "text/plain; charset=utf-8";
        }
        return "application/octet-stream";
    }
}
