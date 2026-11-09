package org.ursc.trajectory.search;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.ursc.trajectory.util.Json;

/**
 * Help search with retrieval-augmented answers. Indexes the bundled help pages, the
 * parameter registry (every setting's explanation), the project documentation and any
 * Markdown/text notes in {@code <workspace>/knowledge}. Retrieval is always offline.
 */
public final class SearchService {

    private final Path workspace;
    private final List<AnswerProvider> providers = new ArrayList<>();
    private volatile Bm25Index index;
    private volatile int documents;

    public SearchService(final Path workspace) throws IOException {
        this.workspace = workspace;
        final AiConfig ai = AiConfig.load(workspace);
        providers.add(new OfflineExtractiveProvider());
        providers.add(new OllamaProvider(ai.ollama));
        providers.add(new CloudProvider(ai.cloud));
        Files.createDirectories(workspace.resolve("knowledge"));
        reindex();
    }

    /** Rebuild the index (e.g. after the user adds notes to the knowledge folder). */
    public synchronized void reindex() throws IOException {
        final Bm25Index idx = new Bm25Index();
        int docs = 0;
        final String manifest = resource("/kb/manifest.txt");
        if (manifest != null) {
            for (final String line : manifest.split("\n")) {
                final String name = line.trim();
                if (name.isEmpty()) {
                    continue;
                }
                final String path = name.startsWith("/") ? name : "/kb/" + name;
                final String text = resource(path);
                if (text != null) {
                    addMarkdown(idx, path.startsWith("/gui/help/") ? "help:" + path.substring(10) : "doc:" + name, text);
                    docs++;
                }
            }
        }
        final String params = resource("/gui/params.json");
        if (params != null) {
            addParams(idx, params);
            docs++;
        }
        final Path k = workspace.resolve("knowledge");
        if (Files.isDirectory(k)) {
            try (Stream<Path> s = Files.walk(k)) {
                for (final Path f : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
                    final String n = f.getFileName().toString().toLowerCase();
                    if (n.endsWith(".md") || n.endsWith(".txt")) {
                        addMarkdown(idx, "knowledge:" + k.relativize(f), Files.readString(f, StandardCharsets.UTF_8));
                        docs++;
                    }
                }
            }
        }
        index = idx;
        documents = docs;
    }

    private static String resource(final String name) throws IOException {
        try (InputStream in = SearchService.class.getResourceAsStream(name)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Split Markdown into sections at headings, then into ~1200-character passages. */
    static void addMarkdown(final Bm25Index idx, final String source, final String text) {
        String title = source.replaceAll(".*[:/]", "");
        StringBuilder buf = new StringBuilder();
        for (final String line : text.split("\n")) {
            if (line.startsWith("#")) {
                flush(idx, source, title, buf);
                buf = new StringBuilder();
                title = line.replaceAll("^#+\\s*", "").trim();
                continue;
            }
            buf.append(line).append('\n');
            if (buf.length() > 1200 && line.isBlank()) {
                flush(idx, source, title, buf);
                buf = new StringBuilder();
            }
        }
        flush(idx, source, title, buf);
    }

    private static void flush(final Bm25Index idx, final String source, final String title, final StringBuilder buf) {
        final String t = buf.toString().trim();
        if (t.length() > 40) {
            idx.add(new Passage(source, title, t));
        }
    }

    @SuppressWarnings("unchecked")
    private static void addParams(final Bm25Index idx, final String json) {
        final Map<String, Object> reg = Json.parseObject(json);
        for (final Object o : (List<Object>) reg.get("params")) {
            final Map<String, Object> p = (Map<String, Object>) o;
            final String text = String.valueOf(p.getOrDefault("what", "")) + "\n\nHow to choose: " + p.getOrDefault("how", "");
            idx.add(new Passage("setting:" + p.get("key"), p.get("group") + " / " + p.get("label"), text));
        }
    }

    /** Ranked passages plus an answer from the highest-priority available provider. */
    public Map<String, Object> search(final String query, final int n) {
        final List<Passage> hits = index.search(query, Math.max(1, Math.min(n, 20)));
        final List<Map<String, Object>> results = new ArrayList<>();
        for (final Passage p : hits) {
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", p.source);
            m.put("title", p.title);
            m.put("text", p.text.length() > 1500 ? p.text.substring(0, 1500) + " ..." : p.text);
            m.put("score", Math.round(p.score * 100) / 100.0);
            results.add(m);
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", query);
        out.put("results", results);
        if (!hits.isEmpty()) {
            for (final AnswerProvider ap : providers) {
                if (!ap.available()) {
                    continue;
                }
                try {
                    final AnswerProvider.Answer a = ap.answer(query, hits);
                    if (!a.text.isEmpty()) {
                        out.put("answer", Map.of("provider", a.provider, "text", a.text));
                        break;
                    }
                } catch (final Exception e) {
                    // fall through to the next provider
                }
            }
        }
        return out;
    }

    public Map<String, Object> status() {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("documents", documents);
        m.put("passages", index.size());
        m.put("knowledgeFolder", workspace.resolve("knowledge").toString());
        final List<Map<String, Object>> ps = new ArrayList<>();
        for (final AnswerProvider p : providers) {
            ps.add(Map.of("name", p.name(), "available", p.available(), "status", p.status()));
        }
        m.put("providers", ps);
        return m;
    }
}
