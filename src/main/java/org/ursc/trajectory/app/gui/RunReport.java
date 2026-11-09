package org.ursc.trajectory.app.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Self-contained, printable HTML report of a run: summary, warnings and flags, provenance
 * (inputs, hashes, products), settings with non-default values highlighted, and inline SVG
 * plots. No scripts and no external resources, so it can be archived or printed to PDF.
 */
final class RunReport {

    private RunReport() {
    }

    static String esc(final Object o) {
        if (o instanceof Double && Double.isFinite((Double) o) && (Double) o == Math.rint((Double) o) && Math.abs((Double) o) < 1e15) {
            return Long.toString(((Double) o).longValue()); // JSON numbers parse as doubles
        }
        return String.valueOf(o == null ? "" : o).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    @SuppressWarnings("unchecked")
    static List<Double> nums(final Object o) {
        final List<Double> out = new ArrayList<>();
        if (o instanceof List) {
            for (final Object v : (List<Object>) o) {
                out.add(v instanceof Number ? ((Number) v).doubleValue() : null);
            }
        }
        return out;
    }

    /** Flatten a nested map to dotted keys. */
    @SuppressWarnings("unchecked")
    static void flatten(final String prefix, final Object v, final Map<String, Object> out) {
        if (v instanceof Map) {
            for (final Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                flatten(prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey(), e.getValue(), out);
            }
        } else {
            out.put(prefix, v);
        }
    }

    record Series(String name, List<Double> y, String color) {
    }

    /** Minimal SVG line chart; null y values break the line. */
    static String svg(final String title, final String yUnit, final List<Double> x, final String xLabel, final List<Series> series) {
        final int w = 680;
        final int h = 230;
        final int l = 64;
        final int r = 12;
        final int top = 28;
        final int b = 34;
        double x0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE;
        double y0 = Double.MAX_VALUE;
        double y1 = -Double.MAX_VALUE;
        for (final double v : x) {
            x0 = Math.min(x0, v);
            x1 = Math.max(x1, v);
        }
        for (final Series s : series) {
            for (final Double v : s.y()) {
                if (v != null && Double.isFinite(v)) {
                    y0 = Math.min(y0, v);
                    y1 = Math.max(y1, v);
                }
            }
        }
        if (y0 > y1) {
            return "";
        }
        if (y1 - y0 < 1e-9) {
            y0 -= 1;
            y1 += 1;
        }
        if (x1 - x0 < 1e-9) {
            x1 = x0 + 1;
        }
        final double fx0 = x0;
        final double fx1 = x1;
        final double fy0 = y0;
        final double fy1 = y1;
        final java.util.function.DoubleUnaryOperator px = v -> l + (v - fx0) / (fx1 - fx0) * (w - l - r);
        final java.util.function.DoubleUnaryOperator py = v -> top + (1 - (v - fy0) / (fy1 - fy0)) * (h - top - b);
        final StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "<svg viewBox=\"0 0 %d %d\" class=\"chart\" role=\"img\" aria-label=\"%s\">", w, h, esc(title)));
        sb.append(String.format(Locale.US, "<text x=\"%d\" y=\"16\" class=\"t\">%s</text>", l, esc(title)));
        for (int i = 0; i <= 4; i++) {
            final double v = y0 + i * (y1 - y0) / 4;
            final double yy = py.applyAsDouble(v);
            sb.append(String.format(Locale.US, "<line x1=\"%d\" x2=\"%d\" y1=\"%.1f\" y2=\"%.1f\" class=\"g\"/>", l, w - r, yy, yy));
            sb.append(String.format(Locale.US, "<text x=\"%d\" y=\"%.1f\" class=\"a\" text-anchor=\"end\">%s</text>", l - 6, yy + 4, fmt(v)));
            final double xv = x0 + i * (x1 - x0) / 4;
            sb.append(String.format(Locale.US, "<text x=\"%.1f\" y=\"%d\" class=\"a\" text-anchor=\"middle\">%s</text>",
                    px.applyAsDouble(xv), h - b + 16, fmt(xv)));
        }
        sb.append(String.format(Locale.US, "<text x=\"%d\" y=\"%d\" class=\"a\" text-anchor=\"end\">%s</text>", w - r, h - 4, esc(xLabel)));
        sb.append(String.format(Locale.US, "<text x=\"12\" y=\"%d\" class=\"a\">%s</text>", top - 6, esc(yUnit)));
        int lx = w - r;
        for (int k = series.size() - 1; k >= 0; k--) {
            final Series s = series.get(k);
            lx -= 8 + 7 * s.name().length();
            sb.append(String.format(Locale.US, "<rect x=\"%d\" y=\"8\" width=\"10\" height=\"3\" fill=\"%s\"/><text x=\"%d\" y=\"14\" class=\"a\">%s</text>",
                    lx - 14, s.color(), lx, esc(s.name())));
            lx -= 18;
        }
        final int stride = Math.max(1, x.size() / 1500);
        for (final Series s : series) {
            final StringBuilder d = new StringBuilder();
            boolean pen = false;
            for (int i = 0; i < x.size() && i < s.y().size(); i += stride) {
                final Double v = s.y().get(i);
                if (v == null || !Double.isFinite(v)) {
                    pen = false;
                    continue;
                }
                d.append(pen ? 'L' : 'M').append(String.format(Locale.US, "%.1f %.1f ", px.applyAsDouble(x.get(i)), py.applyAsDouble(v)));
                pen = true;
            }
            sb.append("<path d=\"").append(d).append("\" fill=\"none\" stroke=\"").append(s.color()).append("\" stroke-width=\"1.6\"/>");
        }
        return sb.append("</svg>").toString();
    }

    static String fmt(final double v) {
        final double a = Math.abs(v);
        return a >= 1000 ? String.format(Locale.US, "%.0f", v) : a >= 10 ? String.format(Locale.US, "%.1f", v)
                : String.format(Locale.US, "%.3g", v);
    }

    private static String list(final Object o) {
        if (!(o instanceof List) || ((List<?>) o).isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder("<ul>");
        for (final Object v : (List<?>) o) {
            sb.append("<li>").append(esc(v)).append("</li>");
        }
        return sb.append("</ul>").toString();
    }

    @SuppressWarnings("unchecked")
    static String build(final Map<String, Object> result, final Map<String, Object> config, final Map<String, Object> defaults,
                        final Map<String, Object> traj, final Map<String, Object> ref,
                        final List<Map<String, Object>> passes) {
        final StringBuilder sb = new StringBuilder();
        final String id = esc(result.get("id"));
        sb.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
          .append("<title>URSC run ").append(id).append("</title><style>")
          .append("body{font:14px/1.45 system-ui,-apple-system,Segoe UI,sans-serif;color:#1b2430;background:#fff;max-width:960px;margin:24px auto;padding:0 16px}")
          .append("h1{font-size:22px;margin:0 0 4px}h2{font-size:16px;margin:26px 0 8px;border-bottom:1px solid #d8dee6;padding-bottom:4px}")
          .append("table{border-collapse:collapse;width:100%;font-size:12.5px}td,th{border-bottom:1px solid #e6eaf0;padding:4px 6px;text-align:left;vertical-align:top}")
          .append(".mono{font-family:ui-monospace,Menlo,Consolas,monospace;font-size:12px;word-break:break-all}.muted{color:#5d6a7a}")
          .append(".chg{background:#fff4d6}.flag{background:#fdecea;border:1px solid #f1b0a8;padding:8px 10px;border-radius:6px;margin:6px 0}")
          .append(".ok{color:#1a7f45;font-weight:600}.bad{color:#b3261e;font-weight:600}")
          .append("svg.chart{width:100%;height:auto;margin:8px 0}svg .t{font-size:13px;font-weight:600;fill:#1b2430}svg .a{font-size:11px;fill:#5d6a7a}svg .g{stroke:#e6eaf0}")
          .append("@media print{body{margin:0}h2{break-after:avoid}svg{break-inside:avoid}}</style></head><body>");
        sb.append("<h1>URSC run ").append(id).append(result.get("runName") == null || "".equals(result.get("runName")) ? "" : " — " + esc(result.get("runName"))).append("</h1>");
        sb.append("<div class=\"muted\">Generated by the URSC GUI. Print this page (Ctrl+P) to save it as PDF.</div>");

        final String status = String.valueOf(result.get("status"));
        final Map<String, Object> od = result.get("od") instanceof Map ? (Map<String, Object>) result.get("od") : null;
        sb.append("<h2>Summary</h2><table>");
        row(sb, "Status", "<span class=\"" + ("SUCCESS".equals(status) ? "ok" : "bad") + "\">" + esc(status) + "</span>");
        row(sb, "Workflow / mode", esc(result.get("workflow")) + " — " + esc(result.get("label")));
        row(sb, "Object", esc(result.get("objectName")) + " " + esc(result.get("objectId")));
        row(sb, "UTC cutoff / initial epoch", esc(result.get("cutoffUtc")));
        row(sb, "Output states", esc(result.get("states")) + (Boolean.TRUE.equals(result.get("hasCovariance")) ? " (with formal covariance)" : ""));
        if (od != null) {
            row(sb, "Orbit determination", esc(od.get("observations")) + " SPP fixes, " + esc(od.get("iterations")) + " iterations, "
                    + (Boolean.TRUE.equals(od.get("converged")) ? "converged" : "<span class=\"bad\">not converged</span>"));
            row(sb, "Post-fit RMS", od.get("postFitRms") instanceof Number ? fmt(((Number) od.get("postFitRms")).doubleValue()) + " m" : "");
            row(sb, "Variance factor", od.get("varianceFactor") instanceof Number ? fmt(((Number) od.get("varianceFactor")).doubleValue()) : "");
        }
        row(sb, "Run time", result.get("elapsedSeconds") instanceof Number
                ? String.format(Locale.US, "%.1f s", ((Number) result.get("elapsedSeconds")).doubleValue()) : "");
        if (ref != null) {
            final List<Double> e = nums(ref.get("err3d"));
            double mx = 0;
            for (final Double v : e) {
                mx = Math.max(mx, v == null ? 0 : v);
            }
            row(sb, "Error vs POEORB (display only)", "max " + fmt(mx) + " m, final " + (e.isEmpty() ? "" : fmt(e.get(e.size() - 1))) + " m");
        }
        sb.append("</table>");

        final Object flags = result.get("flags");
        if (flags instanceof List && !((List<?>) flags).isEmpty()) {
            sb.append("<h2>Flags</h2>");
            for (final Object f : (List<?>) flags) {
                sb.append("<div class=\"flag\">⚠ ").append(esc(f)).append("</div>");
            }
        }
        for (final String[] k : new String[][] {{"errors", "Errors"}, {"warnings", "Warnings"}, {"defaultsUsed", "Generic defaults used"}}) {
            final String l = list(result.get(k[0]));
            if (!l.isEmpty()) {
                sb.append("<h2>").append(k[1]).append("</h2>").append(l);
            }
        }

        // plots
        final StringBuilder plots = new StringBuilder();
        if (traj != null) {
            final List<Double> hrs = new ArrayList<>();
            for (final Double s : nums(traj.get("t"))) {
                hrs.add(s / 3600.0);
            }
            plots.append(svg("Altitude", "km", hrs, "hours", List.of(new Series("altitude", nums(traj.get("alt")), "#2f6fd0"))));
            if (traj.get("sigmaRtn") instanceof List) {
                final List<Double> sr = new ArrayList<>();
                final List<Double> st = new ArrayList<>();
                final List<Double> sn = new ArrayList<>();
                for (final Object o : (List<Object>) traj.get("sigmaRtn")) {
                    final List<Double> v = nums(o);
                    sr.add(v.isEmpty() ? null : v.get(0));
                    st.add(v.isEmpty() ? null : v.get(1));
                    sn.add(v.isEmpty() ? null : v.get(2));
                }
                plots.append(svg("Formal 1-sigma position uncertainty", "m", hrs, "hours",
                        List.of(new Series("radial", sr, "#1a8f55"), new Series("along", st, "#c98a1c"), new Series("cross", sn, "#7a55d8"))));
            }
            if (ref != null) {
                final List<Double> x = new ArrayList<>();
                for (final Double i : nums(ref.get("runIndex"))) {
                    x.add(hrs.get(i.intValue()));
                }
                plots.append(svg("Error vs POEORB reference (display only)", "m", x, "hours", List.of(
                        new Series("3D", nums(ref.get("err3d")), "#d0453a"), new Series("radial", nums(ref.get("radial")), "#1a8f55"),
                        new Series("along", nums(ref.get("along")), "#c98a1c"), new Series("cross", nums(ref.get("cross")), "#7a55d8"))));
            }
        }
        if (od != null && od.get("residualNorm") instanceof List && !((List<?>) od.get("residualNorm")).isEmpty()) {
            final List<Double> y = nums(od.get("residualNorm"));
            final List<Double> x = new ArrayList<>();
            for (int i = 0; i < y.size(); i++) {
                x.add((double) i);
            }
            plots.append(svg("OD post-fit residuals", "m", x, "fix index", List.of(new Series("|residual|", y, "#c4427f"))));
        }
        if (plots.length() > 0) {
            sb.append("<h2>Plots</h2>").append(plots);
        }

        if (passes != null && !passes.isEmpty()) {
            sb.append("<h2>Ground-station passes</h2><table><tr><th>Station</th><th>AOS (UTC)</th><th>LOS (UTC)</th><th>Duration</th><th>Max elevation</th></tr>");
            for (final Map<String, Object> p : passes) {
                sb.append("<tr><td>").append(esc(p.get("station"))).append("</td><td class=\"mono\">").append(esc(p.get("aosUtc")))
                  .append("</td><td class=\"mono\">").append(esc(p.get("losUtc"))).append("</td><td>")
                  .append(String.format(Locale.US, "%.1f min", ((Number) p.get("durationS")).doubleValue() / 60)).append("</td><td>")
                  .append(String.format(Locale.US, "%.1f°", ((Number) p.get("maxElevationDeg")).doubleValue()))
                  .append(Boolean.TRUE.equals(p.get("truncated")) ? " (cut by span)" : "").append("</td></tr>");
            }
            sb.append("</table>");
        }

        // provenance
        sb.append("<h2>Inputs and provenance</h2>");
        if (result.get("inputHashes") instanceof Map) {
            sb.append("<table><tr><th>File</th><th>SHA-256</th></tr>");
            for (final Map.Entry<String, Object> e : ((Map<String, Object>) result.get("inputHashes")).entrySet()) {
                sb.append("<tr><td class=\"mono\">").append(esc(e.getKey())).append("</td><td class=\"mono\">").append(esc(e.getValue())).append("</td></tr>");
            }
            sb.append("</table>");
        }
        if (result.get("productUsage") instanceof Map && !((Map<?, ?>) result.get("productUsage")).isEmpty()) {
            sb.append("<p><b>GPS products used</b></p><ul>");
            for (final Map.Entry<String, Object> e : ((Map<String, Object>) result.get("productUsage")).entrySet()) {
                sb.append("<li>").append(esc(e.getKey())).append(" — ").append(esc(e.getValue())).append(" epochs</li>");
            }
            sb.append("</ul>");
        }
        final String rej = list(result.get("productRejected"));
        if (!rej.isEmpty()) {
            sb.append("<p><b>Products not used</b></p>").append(rej);
        }

        // settings
        final Map<String, Object> cur = new LinkedHashMap<>();
        final Map<String, Object> def = new LinkedHashMap<>();
        flatten("", config, cur);
        flatten("", defaults, def);
        sb.append("<h2>Settings</h2><p class=\"muted\">Highlighted rows differ from the generic defaults.</p><table><tr><th>Setting</th><th>Value</th><th>Default</th></tr>");
        for (final Map.Entry<String, Object> e : cur.entrySet()) {
            final Object d = def.get(e.getKey());
            final boolean changed = !String.valueOf(e.getValue()).equals(String.valueOf(d));
            sb.append("<tr").append(changed ? " class=\"chg\"" : "").append("><td class=\"mono\">").append(esc(e.getKey()))
              .append("</td><td class=\"mono\">").append(esc(e.getValue())).append("</td><td class=\"mono muted\">")
              .append(changed ? esc(d) : "").append("</td></tr>");
        }
        sb.append("</table></body></html>");
        return sb.toString();
    }

    private static void row(final StringBuilder sb, final String k, final String v) {
        sb.append("<tr><th style=\"width:32%\">").append(esc(k)).append("</th><td>").append(v).append("</td></tr>");
    }
}
