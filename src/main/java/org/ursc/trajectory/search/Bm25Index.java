package org.ursc.trajectory.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Okapi BM25 ranking over passages (title terms weighted double), fully offline. */
final class Bm25Index {

    private static final double K1 = 1.4;
    private static final double B = 0.75;
    private static final Set<String> STOP = Set.of("a", "an", "the", "and", "or", "of", "to", "in", "on", "for",
            "is", "are", "be", "by", "with", "as", "at", "it", "this", "that", "from", "if", "not", "no", "can",
            "do", "does", "how", "what", "which", "when", "why", "i", "we", "you", "my", "should", "use", "used");
    /** Domain synonyms so short questions still match the documentation vocabulary. */
    private static final Map<String, List<String>> SYN = Map.of(
            "od", List.of("orbit", "determination"),
            "oem", List.of("ephemeris", "ccsds"),
            "sp3", List.of("igs", "precise", "orbit"),
            "nav", List.of("broadcast", "navigation"),
            "cd", List.of("drag", "coefficient"),
            "cr", List.of("radiation", "reflectivity"),
            "srp", List.of("solar", "radiation", "pressure"),
            "eop", List.of("earth", "orientation"),
            "msis", List.of("nrlmsise00", "density"),
            "cutoff", List.of("cut", "forecast"));

    private final List<Passage> docs = new ArrayList<>();
    private final List<Map<String, Integer>> tf = new ArrayList<>();
    private final List<Integer> len = new ArrayList<>();
    private final Map<String, Integer> df = new HashMap<>();
    private double avgLen;

    static List<String> tokens(final String text) {
        final List<String> out = new ArrayList<>();
        for (final String raw : text.toLowerCase(Locale.US).split("[^a-z0-9.]+")) {
            String t = raw.replaceAll("^\\.+|\\.+$", "");
            if (t.length() < 2 || STOP.contains(t)) {
                continue;
            }
            if (t.length() > 4 && t.endsWith("s") && !t.endsWith("ss")) {
                t = t.substring(0, t.length() - 1);
            }
            out.add(t);
        }
        return out;
    }

    void add(final Passage p) {
        final Map<String, Integer> f = new HashMap<>();
        final List<String> toks = tokens(p.text);
        for (final String t : toks) {
            f.merge(t, 1, Integer::sum);
        }
        for (final String t : tokens(p.title)) {
            f.merge(t, 2, Integer::sum);
        }
        for (final String t : f.keySet()) {
            df.merge(t, 1, Integer::sum);
        }
        docs.add(p);
        tf.add(f);
        len.add(toks.size() + 2 * tokens(p.title).size());
        double s = 0;
        for (final int l : len) {
            s += l;
        }
        avgLen = s / len.size();
    }

    int size() {
        return docs.size();
    }

    List<Passage> search(final String query, final int n) {
        final Set<String> q = new HashSet<>(tokens(query));
        for (final String t : new ArrayList<>(q)) {
            q.addAll(SYN.getOrDefault(t, List.of()));
        }
        final List<Passage> hits = new ArrayList<>();
        final int count = docs.size();
        for (int i = 0; i < count; i++) {
            double s = 0;
            for (final String t : q) {
                final Integer f = tf.get(i).get(t);
                if (f == null) {
                    continue;
                }
                final double idf = Math.log(1 + (count - df.get(t) + 0.5) / (df.get(t) + 0.5));
                s += idf * f * (K1 + 1) / (f + K1 * (1 - B + B * len.get(i) / avgLen));
            }
            if (s > 0) {
                final Passage p = docs.get(i);
                final Passage hit = new Passage(p.source, p.title, p.text);
                hit.score = s;
                hits.add(hit);
            }
        }
        hits.sort((a, b) -> Double.compare(b.score, a.score));
        return hits.subList(0, Math.min(n, hits.size()));
    }
}
