package org.ursc.trajectory.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Offline answer: the sentences from the top passages that best cover the question terms,
 * each cited to its passage. No model, no network.
 */
final class OfflineExtractiveProvider implements AnswerProvider {

    @Override
    public String name() {
        return "offline";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String status() {
        return "built in (no network)";
    }

    /** Rejoin hard-wrapped Markdown lines into paragraphs and drop formatting marks. */
    static String plain(final String md) {
        return md.replaceAll("(?m)^\\s*([-*]|\\d+\\.)\\s+", "")
                .replaceAll("(?<!\\n)\\n(?!\\n)", " ")
                .replace("**", "").replace("`", "")
                .replaceAll("[ \\t]+", " ");
    }

    @Override
    public Answer answer(final String question, final List<Passage> passages) {
        final Set<String> q = new HashSet<>(Bm25Index.tokens(question));
        // "which/should/how ..." questions want guidance: favour the "How to choose" parts
        final boolean advice = question.toLowerCase(java.util.Locale.US)
                .matches("(?s).*\\b(should|which|how|recommend|best|choose|pick|when)\\b.*");
        final List<double[]> scored = new ArrayList<>(); // {passage, sentence, score}
        final List<List<String>> sentences = new ArrayList<>();
        final List<Set<Integer>> adviceIdx = new ArrayList<>();
        for (int p = 0; p < Math.min(3, passages.size()); p++) {
            final List<String> ss = new ArrayList<>();
            final Set<Integer> adv = new HashSet<>();
            for (String para : plain(passages.get(p).text).split("\\n\\s*\\n")) {
                final boolean howPart = para.trim().startsWith("How to choose:");
                para = para.replaceFirst("^\\s*How to choose:\\s*", "");
                for (final String s : para.split("(?<=[.!?])\\s+(?=[A-Z0-9(])")) {
                    final String t = s.trim();
                    if (t.length() >= 20 && !t.startsWith("|") && !t.contains("```") && t.matches("(?s).*[.!?]$")) {
                        if (howPart) {
                            adv.add(ss.size());
                        }
                        ss.add(t);
                    }
                }
            }
            adviceIdx.add(adv);
            sentences.add(ss);
            final Set<Integer> adv2 = adviceIdx.get(p);
            for (int i = 0; i < ss.size(); i++) {
                final List<String> toks = Bm25Index.tokens(ss.get(i));
                int hit = 0;
                for (final String tok : new HashSet<>(toks)) {
                    if (q.contains(tok)) {
                        hit++;
                    }
                }
                // the top passage is already the best match: its sentences qualify even without
                // repeating the query words, and its opening sentences usually carry the gist
                if (hit > 0 || (p == 0 && ss.get(i).length() >= 40)) {
                    scored.add(new double[] {p, i, hit / Math.sqrt(Math.max(8, toks.size()))
                            + (p == 0 ? 0.6 : (2 - p) * 0.25) + (i < 2 ? 0.25 : 0.0)
                            + (advice && adv2.contains(i) ? 0.8 : 0.0)});
                }
            }
        }
        scored.sort((a, b) -> Double.compare(b[2], a[2]));
        final List<double[]> best = new ArrayList<>(scored.subList(0, Math.min(3, scored.size())));
        best.sort((a, b) -> a[0] != b[0] ? Double.compare(a[0], b[0]) : Double.compare(a[1], b[1]));
        final StringBuilder sb = new StringBuilder();
        for (final double[] s : best) {
            sb.append(sentences.get((int) s[0]).get((int) s[1])).append(" [").append((int) s[0] + 1).append("] ");
        }
        return new Answer(name(), sb.toString().trim());
    }
}
