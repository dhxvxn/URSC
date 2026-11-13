import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.ursc.trajectory.data.DataCatalog;
import org.ursc.trajectory.pipeline.*;
import org.ursc.trajectory.util.Json;

/** Headless replica of the GUI window run (showcase profile + catalogue inputs), scored against POEORB. */
public class Variant {
    @SuppressWarnings("unchecked")
    static void merge(Map<String, Object> into, Map<String, Object> from) {
        for (var e : from.entrySet()) {
            Object cur = into.get(e.getKey());
            if (cur instanceof Map && e.getValue() instanceof Map) merge((Map<String, Object>) cur, (Map<String, Object>) e.getValue());
            else if (into.containsKey(e.getKey())) into.put(e.getKey(), e.getValue());
        }
    }
    public static void main(String[] a) throws Exception {
        final String method = a[0]; final int start = Integer.parseInt(a[1]), step = Integer.parseInt(a[2]);
        final Path out = Path.of(a[3]); final Path ws = Path.of(a[4]);
        final Map<String, Object> base = Json.parseObject(new RunConfig().toJson());
        final Map<String, Object> prof = Json.parseObject(Files.readString(Path.of("/home/user/URSC/src/main/resources/gui/profiles/showcase.json")));
        prof.remove("_about"); merge(base, prof);
        final String baseJson = Json.write(base);
        final DataCatalog catalog = new DataCatalog(ws);
        if (catalog.entries().size() < 500) { catalog.add(List.of("/home/user/URSC/data")); catalog.save(); }
        if (a.length > 5) { System.out.println("catalogued " + catalog.entries().size()); return; }
        final double[] hrs = {0, 1, 6, 24, 72, 168};
        final boolean header = !Files.exists(out);
        try (PrintWriter pw = new PrintWriter(new FileWriter(out.toFile(), true))) {
            if (header) pw.println("date,method,status,odMethod,postFitRms,sppRms,phaseRms,codeRms,phaseObs,arcs,slips,changeFromSpp,seconds,e0h,e1h,e6h,e1d,e3d,e7d,a1d,a7d,note");
            String satellite = null;
            final LocalDate d0 = LocalDate.parse("2024-09-03");
            final java.util.Set<String> done = new java.util.HashSet<>();
            try (var st = Files.newDirectoryStream(out.getParent(), "ok-*.csv")) {
                for (Path f : st) for (String l : Files.readAllLines(f)) done.add(l.split(",")[0]);
            }
            final String[] dayList = System.getenv("DAYS") == null ? new String[0] : System.getenv("DAYS").split(",");
            for (int i = 0; i < (dayList.length > 0 ? dayList.length : 61); i++) {
                final String day = dayList.length > 0 ? dayList[i] : d0.plusDays(i).toString();
                if (dayList.length == 0 && (i % step != start || done.contains(day))) continue;
                final RunConfig c = RunConfig.fromJson(baseJson, new ArrayList<>());
                c.cutoffUtc = day + "T05:59:42";
                if (method.contains("cd")) c.od.estimateCd = true;
                if (method.contains("cr")) c.od.estimateCr = true;
                if (method.contains("arc12")) { c.od.arcHours = 12; c.od.phaseRateSeconds = 60; }
                if (method.contains("arc24")) { c.od.arcHours = 24; c.od.phaseRateSeconds = 120; }
                if (method.contains("jb")) c.forces.dragModel = RunConfig.DragModel.JB2008;
                if (method.contains("nopco")) c.od.gpsAntennaPhaseCentre = false;
                if (method.contains("nowu")) c.od.phaseWindUp = false;
                if (method.contains("box")) { c.spacecraft.shape = RunConfig.ShapeModel.BOX_WING; c.spacecraft.attitude = RunConfig.AttitudeSource.MEASURED_PROQUA; }
                final Map<String, List<String>> s = catalog.suggest(TimeUtil.parseUtc(c.cutoffUtc), c.od.arcHours,
                        c.forecast.spanDays, false, satellite);
                if (satellite == null && !s.get("satellite").isEmpty()) satellite = s.get("satellite").get(0);
                c.inputs.gnssrd = s.get("gnssrd");
                if (c.spacecraft.shape == RunConfig.ShapeModel.BOX_WING) c.inputs.proqua = s.get("proqua"); c.inputs.gpsProducts = s.get("gpsProducts"); c.inputs.poeorb = s.get("poeorb");
                if (c.inputs.eopFile.isBlank() && !s.get("eopFile").isEmpty()) c.inputs.eopFile = s.get("eopFile").get(0);
                if (c.inputs.spaceWeatherFile.isBlank() && !s.get("spaceWeatherFile").isEmpty()) c.inputs.spaceWeatherFile = s.get("spaceWeatherFile").get(0);
                final RunResult r = OdForecastPipeline.run(c, ProgressListener.NONE);
                final double[] e = new double[hrs.length]; Arrays.fill(e, Double.NaN);
                double a1 = Double.NaN, a7 = Double.NaN;
                if (r.ok() && !c.inputs.poeorb.isEmpty()) {
                    final ReferenceComparison.Result cr = ReferenceComparison.compare(InputFiles.expand(c.inputs.poeorb), r.states);
                    for (int k = 0; k < cr.err3d.size(); k++) {
                        final double h = r.states.get(cr.runIndex.get(k)).date.durationFrom(r.states.get(0).date) / 3600.0;
                        for (int j = 0; j < hrs.length; j++) if (Math.abs(h - hrs[j]) < 1e-6) { e[j] = cr.err3d.get(k); if (j == 3) a1 = cr.along.get(k); if (j == 5) a7 = cr.along.get(k); }
                    }
                }
                final RunResult.Od od = r.od;
                final String note = String.join(" | ", r.errors).replace(',', ';') + (r.warnings.stream().anyMatch(w -> w.contains("arrier-phase OD")) ? " CPWARN" : "");
                pw.printf(Locale.US, "%s,%s,%s,%s,%.4f,%.4f,%.4f,%.4f,%d,%d,%d,%.3f,%.0f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%s%n",
                        day, method, r.status, od == null ? "" : od.method, od == null ? Double.NaN : od.postFitRms,
                        od == null ? Double.NaN : od.sppPostFitRms, od == null ? Double.NaN : od.phaseRms, od == null ? Double.NaN : od.codeRms,
                        od == null ? 0 : od.phaseObservations, od == null ? 0 : od.phaseArcs, od == null ? 0 : od.cycleSlips,
                        od == null ? Double.NaN : od.changeFromSpp, r.elapsedSeconds, e[0], e[1], e[2], e[3], e[4], e[5], a1, a7, note);
                pw.flush();
                System.out.println(day + " " + method + " " + r.status + " e1d=" + e[3] + " e7d=" + e[5]);
            }
        }
    }
}
