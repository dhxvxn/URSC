package org.ursc.trajectory.app.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.ursc.trajectory.pipeline.RunConfig;
import org.ursc.trajectory.pipeline.RunResult;
import org.ursc.trajectory.util.Json;

/**
 * Run history on disk: {@code runs/<id>/} holds config.json, result.json, trajectory.json,
 * the OEM output and the log, so every run is reproducible and comparable later.
 */
final class RunStore {

    private final Path root;

    RunStore(final Path workspace) throws IOException {
        this.root = Files.createDirectories(workspace.resolve("runs"));
    }

    String newId() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + Integer.toHexString((int) (Math.random() * 0xFFFF));
    }

    Path dir(final String id) {
        if (!id.matches("[0-9A-Za-z\\-]+")) {
            throw new IllegalArgumentException("bad run id");
        }
        return root.resolve(id);
    }

    /** Result summary (everything except the state list, which lives in the OEM/trajectory). */
    static Map<String, Object> summary(final RunResult r) {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.status.name());
        m.put("workflow", r.workflow);
        m.put("runName", r.runName);
        m.put("label", r.label);
        m.put("objectName", r.objectName);
        m.put("objectId", r.objectId);
        m.put("cutoffUtc", r.cutoffUtc);
        m.put("errors", r.errors);
        m.put("warnings", r.warnings);
        m.put("flags", r.flags);
        m.put("defaultsUsed", r.defaultsUsed);
        m.put("log", r.log);
        m.put("inputHashes", r.inputHashes);
        m.put("productUsage", r.productUsage);
        m.put("productRejected", r.productRejected);
        m.put("productFallbacks", r.productFallbacks);
        m.put("spp", r.spp);
        m.put("od", r.od);
        m.put("states", r.states.size());
        m.put("hasCovariance", !r.states.isEmpty() && r.states.get(0).covariance != null);
        m.put("elapsedSeconds", r.elapsedSeconds);
        return m;
    }

    void save(final String id, final RunConfig cfg, final RunResult res, final Map<String, Object> trajectory,
              final String oemText, final List<String> log, final Map<String, Object> reference) throws IOException {
        final Path d = Files.createDirectories(dir(id));
        Files.writeString(d.resolve("config.json"), cfg.toJson());
        final Map<String, Object> s = summary(res);
        s.put("id", id);
        s.put("created", LocalDateTime.now().toString());
        s.put("oem", oemText == null ? null : "output.oem");
        Files.writeString(d.resolve("result.json"), Json.write(s)); // first: the run record itself
        if (trajectory != null) {
            Files.writeString(d.resolve("trajectory.json"), Json.writeCompact(trajectory));
        }
        if (oemText != null) {
            Files.writeString(d.resolve("output.oem"), oemText);
        }
        if (reference != null) {
            Files.writeString(d.resolve("reference.json"), Json.writeCompact(reference));
        }
        Files.write(d.resolve("log.txt"), log);
    }

    /** Newest first: id, name, workflow, status, label, cutoff, object, created. */
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> list() throws IOException {
        final List<Map<String, Object>> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(root)) {
            for (final Path d : (Iterable<Path>) s.filter(Files::isDirectory)::iterator) {
                final Path f = d.resolve("result.json");
                if (!Files.exists(f)) {
                    continue;
                }
                final Map<String, Object> full = Json.parseObject(Files.readString(f));
                final Map<String, Object> m = new LinkedHashMap<>();
                for (final String k : List.of("id", "runName", "workflow", "status", "label", "cutoffUtc",
                        "objectName", "created", "elapsedSeconds", "states", "hasCovariance", "flags")) {
                    m.put(k, full.get(k));
                }
                final Object od = full.get("od");
                if (od instanceof Map) {
                    m.put("postFitRms", ((Map<String, Object>) od).get("postFitRms"));
                }
                out.add(m);
            }
        }
        out.sort((a, b) -> String.valueOf(b.get("id")).compareTo(String.valueOf(a.get("id"))));
        return out;
    }

    void delete(final String id) throws IOException {
        final Path d = dir(id);
        if (Files.isDirectory(d)) {
            try (Stream<Path> s = Files.walk(d)) {
                s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }
}
