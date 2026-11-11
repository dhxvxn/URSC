package org.ursc.trajectory.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ursc.trajectory.frames.Finals2000AEopProvider;
import org.ursc.trajectory.gnss.products.IgsProductName;
import org.ursc.trajectory.io.ProductInput;
import org.ursc.trajectory.io.TarGz;
import org.ursc.trajectory.pipeline.InputFiles;
import org.ursc.trajectory.pipeline.OdForecastPipeline;
import org.ursc.trajectory.pipeline.TimeUtil;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;
import org.ursc.trajectory.util.Json;

/**
 * Catalog of every input file the user has added (single files, many files, folders, many
 * folders). Each file is identified from its <b>content</b>; names are only used for metadata
 * (coverage, product class). Results are persisted as {@code catalog.json} in the workspace,
 * with SHA-256 hashes cached by size and modification time so rescans are cheap.
 */
public final class DataCatalog {

    /** What a file is, which also decides the run input it feeds. */
    public enum Kind {
        GNSS_OBSERVATIONS("GNSS observations (GNSSRD / RINEX obs)", "gnssrd"),
        GPS_BROADCAST_NAV("GPS broadcast navigation", "gpsProducts"),
        IGS_ORBIT_SP3("IGS precise orbit (SP3)", "gpsProducts"),
        IGS_CLOCK("IGS precise clock (RINEX CLK)", "gpsProducts"),
        POEORB("Precise orbit ephemeris (POEORB, reference only)", "poeorb"),
        ATTITUDE_PROQUA("Measured attitude (AUX_PROQUA)", "proqua"),
        EOP("Earth orientation parameters", "eopFile"),
        SPACE_WEATHER("Space weather (CSSI)", "spaceWeatherFile"),
        DE_EPHEMERIS("JPL planetary ephemeris (DE4xx)", "de440File"),
        GRAVITY_FIELD("Gravity field (ICGEM .gfc)", "gravityFile"),
        OEM("CCSDS orbit ephemeris message (OEM)", "initialState"),
        UNKNOWN("Unrecognised", "");

        public final String label;
        /** RunConfig input this kind feeds. */
        public final String role;

        Kind(final String label, final String role) {
            this.label = label;
            this.role = role;
        }
    }

    /** One catalogued file. Plain fields for JSON. */
    public static final class Entry {
        public String path;
        public String name;
        public Kind kind = Kind.UNKNOWN;
        public String satellite = "";
        public String productClass = "";
        public String provider = "";
        /** Coverage in UTC ISO-8601, blank if not derivable. */
        public String coverageStart = "";
        public String coverageEnd = "";
        public String nominalIssueTime = "";
        public long sizeBytes;
        public long modified;
        public String sha256 = "";
        public String note = "";
        public String added = "";

        public AbsoluteDate start() {
            return coverageStart.isEmpty() ? null : TimeUtil.parseUtc(coverageStart);
        }

        public AbsoluteDate end() {
            return coverageEnd.isEmpty() ? null : TimeUtil.parseUtc(coverageEnd);
        }
    }

    private static final Pattern SENTINEL = Pattern.compile(
            "^(S\\d[A-Z_])_OPER_(AUX_[A-Z]+)_[A-Z_]*?_(\\d{8}T\\d{6})_V(\\d{8}T\\d{6})_(\\d{8}T\\d{6})");
    private static final Pattern RINEX_LONG = Pattern.compile(
            "^[A-Z0-9]{9}_[A-Z]_(\\d{4})(\\d{3})(\\d{2})(\\d{2})_(\\d{2})([MHDY])_");

    private final Path catalogFile;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final List<String> watched = new ArrayList<>();

    public DataCatalog(final Path workspace) throws IOException {
        Files.createDirectories(workspace);
        this.catalogFile = workspace.resolve("catalog.json");
        if (Files.exists(catalogFile)) {
            load();
        }
    }

    @SuppressWarnings("unchecked")
    private void load() throws IOException {
        final Map<String, Object> m = Json.parseObject(Files.readString(catalogFile));
        for (final Object o : (List<Object>) m.getOrDefault("entries", List.of())) {
            final Entry e = new Entry();
            Json.bind((Map<String, Object>) o, e);
            if (e.kind == Kind.POEORB && e.satellite.isEmpty() && Files.isRegularFile(Paths.get(e.path))) {
                // catalogued before the spacecraft was read from the header: classify again
                try {
                    classify(Paths.get(e.path), e);
                } catch (final IOException | RuntimeException ignored) {
                    // keep the old entry
                }
            }
            entries.put(e.path, e);
        }
        for (final Object o : (List<Object>) m.getOrDefault("watched", List.of())) {
            watched.add(String.valueOf(o));
        }
    }

    public synchronized void save() throws IOException {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("entries", new ArrayList<>(entries.values()));
        m.put("watched", watched);
        Files.writeString(catalogFile, Json.write(m));
    }

    public synchronized List<Entry> entries() {
        final List<Entry> l = new ArrayList<>(entries.values());
        l.sort(Comparator.comparing((Entry e) -> e.kind).thenComparing(e -> e.coverageStart).thenComparing(e -> e.name));
        return l;
    }

    public synchronized List<String> watchedFolders() {
        return new ArrayList<>(watched);
    }

    public synchronized void addWatched(final String folder) throws IOException {
        final String abs = Paths.get(folder).toAbsolutePath().normalize().toString();
        if (!watched.contains(abs)) {
            watched.add(abs);
            save();
        }
    }

    public synchronized void removeWatched(final String folder) throws IOException {
        watched.remove(Paths.get(folder).toAbsolutePath().normalize().toString());
        save();
    }

    /** Forget an entry (the file itself is not touched). */
    public synchronized void remove(final String path) throws IOException {
        entries.remove(path);
        save();
    }

    /**
     * Add files and/or folders (recursively). Already known, unchanged files are skipped.
     *
     * @return the entries that were new or changed
     */
    public List<Entry> add(final List<String> paths) throws IOException {
        final List<Entry> changed = new ArrayList<>();
        for (final Path p : InputFiles.expand(paths)) {
            final Entry e = index(p);
            if (e != null) {
                changed.add(e);
            }
        }
        save();
        return changed;
    }

    /** Re-check every catalogued file and watched folder; drop files that disappeared. */
    public List<Entry> rescan() throws IOException {
        synchronized (this) {
            entries.values().removeIf(e -> !Files.isRegularFile(Paths.get(e.path)));
        }
        final List<String> roots = new ArrayList<>(watchedFolders());
        for (final Entry e : entries()) {
            roots.add(e.path);
        }
        final List<String> existing = new ArrayList<>();
        for (final String r : roots) {
            if (Files.exists(Paths.get(r))) {
                existing.add(r);
            }
        }
        return add(existing);
    }

    /** Index one file; returns the entry if new or changed, else null. */
    synchronized Entry index(final Path file) throws IOException {
        final String key = file.toAbsolutePath().normalize().toString();
        final long size = Files.size(file);
        final long mtime = Files.getLastModifiedTime(file).toMillis();
        final Entry old = entries.get(key);
        if (old != null && old.sizeBytes == size && old.modified == mtime) {
            return null;
        }
        final Entry e = new Entry();
        e.path = key;
        e.name = file.getFileName().toString();
        e.sizeBytes = size;
        e.modified = mtime;
        e.added = java.time.Instant.now().toString();
        try {
            classify(file, e);
        } catch (final IOException | RuntimeException ex) {
            e.kind = Kind.UNKNOWN;
            e.note = "unreadable: " + ex.getMessage();
        }
        e.sha256 = OdForecastPipeline.sha256(file);
        entries.put(key, e);
        return e;
    }

    // ---------------------------------------------------------------- classification

    static void classify(final Path file, final Entry e) throws IOException {
        final String n = e.name;
        final String lower = n.toLowerCase(Locale.US);
        sentinelName(n, e);
        if (lower.endsWith(".tgz") || lower.endsWith(".tar.gz")) {
            try (InputStream in = TarGz.openEntry(file, ".DBL")) {
                final List<String> head = head(in, 40);
                if (isRinex(head, 'O')) {
                    e.kind = Kind.GNSS_OBSERVATIONS;
                } else if (String.join("\n", head).contains("Q_COMPR")) {
                    e.kind = Kind.ATTITUDE_PROQUA;
                } else {
                    e.note = "archive content not recognised";
                }
            }
            return;
        }
        if (lower.matches("^l[nm]x[mp]\\d{4}\\.4\\d\\d$") || binaryDe(file)) {
            e.kind = Kind.DE_EPHEMERIS;
            e.note = "JPL DE binary";
            return;
        }
        final List<String> head;
        try (InputStream in = ProductInput.open(file)) {
            head = head(in, 400);
        }
        if (head.isEmpty()) {
            e.note = "empty file";
            return;
        }
        final String first = head.get(0);
        final String all = String.join("\n", head);
        if (first.startsWith("CCSDS_OEM_VERS")) {
            e.kind = Kind.OEM;
        } else if (first.length() > 2 && first.charAt(0) == '#' && "cdCD".indexOf(first.charAt(1)) >= 0) {
            e.kind = Kind.IGS_ORBIT_SP3;
            e.satellite = "GPS";
            sp3Coverage(head, e);
            igsName(n, e);
        } else if (isRinex(head, 'O')) {
            e.kind = Kind.GNSS_OBSERVATIONS;
            rinexLongName(n, e);
            if (e.satellite.isEmpty()) {
                e.satellite = org.ursc.trajectory.pipeline.ObsLoader.spacecraftOf(file);
            }
            if (e.coverageStart.isEmpty()) {
                obsCoverage(file, e);
            }
        } else if (isRinex(head, 'N')) {
            e.kind = Kind.GPS_BROADCAST_NAV;
            e.satellite = "GPS";
            e.productClass = "BROADCAST";
            rinexLongName(n, e);
        } else if (isRinex(head, 'C')) {
            e.kind = Kind.IGS_CLOCK;
            e.satellite = "GPS";
            igsName(n, e);
        } else if ((all.contains("AUX_POEORB") || all.contains("AUX_RESORB") || all.contains("AUX_PREORB"))
                && all.contains("Earth_Explorer")) {
            e.kind = Kind.POEORB;
            final Matcher ms = Pattern.compile("<Mission>\\s*Sentinel-?(\\d)([AB])\\s*<", Pattern.CASE_INSENSITIVE).matcher(all);
            if (ms.find()) {
                e.satellite = "S" + ms.group(1) + ms.group(2).toUpperCase(java.util.Locale.ROOT);
            }
            final Matcher vs = Pattern.compile("<Validity_Start>UTC=([0-9T:\\-.]+)<").matcher(all);
            final Matcher ve = Pattern.compile("<Validity_Stop>UTC=([0-9T:\\-.]+)<").matcher(all);
            if (vs.find() && ve.find()) {
                e.coverageStart = TimeUtil.formatUtc(TimeUtil.parseUtc(vs.group(1)));
                e.coverageEnd = TimeUtil.formatUtc(TimeUtil.parseUtc(ve.group(1)));
            }
            if (!all.contains("AUX_POEORB")) {
                e.note = "restituted/predicted orbit, less accurate than POEORB";
            }
        } else if (first.startsWith("DATATYPE CssiSpaceWeather") || first.startsWith("DATE,BSRN")) {
            e.kind = Kind.SPACE_WEATHER;
        } else if (all.contains("Q_COMPR")) {
            e.kind = Kind.ATTITUDE_PROQUA;
        } else if (all.contains("end_of_head") && (all.contains("gravity_field") || all.contains("product_type"))) {
            e.kind = Kind.GRAVITY_FIELD;
        } else if (finals(file)) {
            e.kind = Kind.EOP;
            e.note = "IERS finals2000A";
        } else if (all.contains("EOP") && all.contains("C04")) {
            e.kind = Kind.EOP;
            e.note = "IERS EOP C04";
        } else {
            e.note = "content not recognised";
        }
    }

    private static boolean isRinex(final List<String> head, final char type) {
        for (int i = 0; i < Math.min(3, head.size()); i++) {
            final String l = head.get(i);
            if (l.contains("RINEX VERSION / TYPE") && l.length() > 20) {
                return Character.toUpperCase(l.charAt(20)) == type;
            }
        }
        return false;
    }

    private static boolean finals(final Path f) {
        try (InputStream in = Files.newInputStream(f)) {
            return Finals2000AEopProvider.looksLikeFinals2000A(in);
        } catch (final IOException | RuntimeException ex) {
            return false;
        }
    }

    private static boolean binaryDe(final Path f) {
        try (InputStream in = Files.newInputStream(f)) {
            final String title = new String(in.readNBytes(84), StandardCharsets.US_ASCII);
            return title.startsWith("JPL Planetary Ephemeris");
        } catch (final IOException ex) {
            return false;
        }
    }

    private static List<String> head(final InputStream in, final int lines) throws IOException {
        final BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
        final List<String> out = new ArrayList<>();
        String l;
        while (out.size() < lines && (l = br.readLine()) != null) {
            out.add(l);
        }
        return out;
    }

    private static void sentinelName(final String n, final Entry e) {
        final Matcher m = SENTINEL.matcher(n);
        if (m.find()) {
            e.satellite = m.group(1).replace("_", "");
            e.coverageStart = stamp(m.group(4));
            e.coverageEnd = stamp(m.group(5));
        }
    }

    private static String stamp(final String s) {
        return s.substring(0, 4) + "-" + s.substring(4, 6) + "-" + s.substring(6, 8) + "T" + s.substring(9, 11)
                + ":" + s.substring(11, 13) + ":" + s.substring(13, 15) + ".000000";
    }

    private static void igsName(final String n, final Entry e) {
        final IgsProductName p = IgsProductName.parse(ProductInput.baseName(Paths.get(n)));
        if (!p.recognised()) {
            e.note = "file name not a recognised IGS product name; product class unknown";
            return;
        }
        e.productClass = p.productClass().name();
        e.provider = p.provider();
        if (e.coverageStart.isEmpty()) {
            e.coverageStart = TimeUtil.formatUtc(p.start());
            e.coverageEnd = TimeUtil.formatUtc(p.start().shiftedBy(p.spanSeconds()));
        }
        e.nominalIssueTime = TimeUtil.formatUtc(p.nominalIssueTime());
    }

    private static void sp3Coverage(final List<String> head, final Entry e) {
        try {
            final String[] h = head.get(0).substring(3).trim().split("\\s+");
            final AbsoluteDate start = new AbsoluteDate(Integer.parseInt(h[0]), Integer.parseInt(h[1]),
                    Integer.parseInt(h[2]), Integer.parseInt(h[3]), Integer.parseInt(h[4]),
                    Double.parseDouble(h[5]), TimeScalesFactory.getGPS());
            final int n = Integer.parseInt(h[6]);
            double step = 900.0;
            for (final String l : head) {
                if (l.startsWith("##")) {
                    step = Double.parseDouble(l.substring(2).trim().split("\\s+")[2]);
                    break;
                }
            }
            e.coverageStart = TimeUtil.formatUtc(start);
            e.coverageEnd = TimeUtil.formatUtc(start.shiftedBy((n - 1) * step));
        } catch (final RuntimeException ignored) {
            // leave coverage blank
        }
    }

    /** First and last epoch of a plain RINEX observation file (GPS time), if it is small enough to scan. */
    private static void obsCoverage(final Path f, final Entry e) {
        try {
            if (Files.size(f) > 200L * 1024 * 1024) {
                return;
            }
            String first = null;
            String last = null;
            try (InputStream in = ProductInput.open(f);
                 BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII))) {
                String l;
                while ((l = br.readLine()) != null) {
                    if (l.startsWith(">")) {
                        if (first == null) {
                            first = l;
                        }
                        last = l;
                    }
                }
            }
            if (first != null) {
                e.coverageStart = TimeUtil.formatUtc(epochLine(first));
                e.coverageEnd = TimeUtil.formatUtc(epochLine(last));
            }
        } catch (final IOException | RuntimeException ignored) {
            // leave coverage blank
        }
    }

    private static AbsoluteDate epochLine(final String l) {
        final String[] h = l.substring(1).trim().split("\\s+");
        return new AbsoluteDate(Integer.parseInt(h[0]), Integer.parseInt(h[1]), Integer.parseInt(h[2]),
                Integer.parseInt(h[3]), Integer.parseInt(h[4]), Double.parseDouble(h[5]), TimeScalesFactory.getGPS());
    }

    private static void rinexLongName(final String n, final Entry e) {
        final Matcher m = RINEX_LONG.matcher(n);
        if (m.find() && e.coverageStart.isEmpty()) {
            final AbsoluteDate start = new AbsoluteDate(Integer.parseInt(m.group(1)), 1, 1,
                    Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), 0.0, TimeScalesFactory.getGPS())
                    .shiftedBy((Integer.parseInt(m.group(2)) - 1) * 86400.0);
            final double unit = "M".equals(m.group(6)) ? 60 : "H".equals(m.group(6)) ? 3600 : "Y".equals(m.group(6)) ? 365.25 * 86400 : 86400;
            e.coverageStart = TimeUtil.formatUtc(start);
            e.coverageEnd = TimeUtil.formatUtc(start.shiftedBy(Integer.parseInt(m.group(5)) * unit));
        }
    }

    // ---------------------------------------------------------------- suggestions

    /**
     * Suggest run inputs for a cutoff: observations and GPS products overlapping the arc,
     * POEORB overlapping the forecast span (display only), attitude overlapping the arc (and
     * the span in reconstruction mode), and the most recent EOP / weather / DE / gravity files.
     * Files whose coverage is unknown are included where coverage cannot be checked.
     */
    public Map<String, List<String>> suggest(final AbsoluteDate cutoff, final double arcHours,
                                             final double spanDays, final boolean reconstruction) {
        return suggest(cutoff, arcHours, spanDays, reconstruction, null);
    }

    /**
     * As {@link #suggest(AbsoluteDate, double, double, boolean)}, restricted to one spacecraft's
     * observations ({@code satellite} null = the spacecraft with the most matching files). The
     * result also lists every spacecraft with matching observations under "satellites".
     */
    public Map<String, List<String>> suggest(final AbsoluteDate cutoff, final double arcHours,
                                             final double spanDays, final boolean reconstruction,
                                             final String satellite) {
        final AbsoluteDate a0 = cutoff.shiftedBy(-arcHours * 3600.0);
        final AbsoluteDate f1 = cutoff.shiftedBy(spanDays * 86400.0);
        final Map<String, List<String>> out = new LinkedHashMap<>();
        for (final String role : List.of("gnssrd", "gpsProducts", "poeorb", "proqua", "eopFile",
                "spaceWeatherFile", "de440File", "gravityFile")) {
            out.put(role, new ArrayList<>());
        }
        final Map<String, Entry> newestSingle = new LinkedHashMap<>();
        final Map<String, List<String>> obsBySat = new java.util.TreeMap<>();
        final List<Entry> perCraft = new ArrayList<>();
        for (final Entry e : entries()) {
            switch (e.kind) {
                case GNSS_OBSERVATIONS:
                    if (overlaps(e, a0, cutoff, false)) {
                        obsBySat.computeIfAbsent(e.satellite.isEmpty() ? e.name : e.satellite, k -> new ArrayList<>()).add(e.path);
                    }
                    break;
                case GPS_BROADCAST_NAV:
                case IGS_ORBIT_SP3:
                case IGS_CLOCK:
                    if (overlaps(e, a0, cutoff, true)) {
                        out.get("gpsProducts").add(e.path);
                    }
                    break;
                case POEORB:
                    if (overlaps(e, cutoff, f1, false)) {
                        perCraft.add(e);
                    }
                    break;
                case ATTITUDE_PROQUA:
                    if (overlaps(e, a0, reconstruction ? f1 : cutoff, false)) {
                        perCraft.add(e);
                    }
                    break;
                case EOP:
                case SPACE_WEATHER:
                case DE_EPHEMERIS:
                case GRAVITY_FIELD:
                    final Entry prev = newestSingle.get(e.kind.role);
                    if (prev == null || e.modified > prev.modified) {
                        newestSingle.put(e.kind.role, e);
                    }
                    break;
                default:
                    break;
            }
        }
        for (final Map.Entry<String, Entry> s : newestSingle.entrySet()) {
            out.get(s.getKey()).add(s.getValue().path);
        }
        String pick = satellite != null && obsBySat.containsKey(satellite) ? satellite : null;
        if (pick == null) {
            for (final Map.Entry<String, List<String>> s : obsBySat.entrySet()) {
                if (pick == null || s.getValue().size() > obsBySat.get(pick).size()) {
                    pick = s.getKey();
                }
            }
        }
        if (pick != null) {
            out.get("gnssrd").addAll(obsBySat.get(pick));
        }
        // reference orbits and attitude belong to one spacecraft: never offer another one's
        for (final Entry e : perCraft) {
            if (pick == null || e.satellite.isEmpty() || e.satellite.equalsIgnoreCase(pick)) {
                out.get(e.kind.role).add(e.path);
            }
        }
        out.put("satellites", new ArrayList<>(obsBySat.keySet()));
        out.put("satellite", pick == null ? List.of() : List.of(pick));
        return out;
    }

    private static boolean overlaps(final Entry e, final AbsoluteDate a, final AbsoluteDate b,
                                    final boolean unknownCounts) {
        if (e.coverageStart.isEmpty() || e.coverageEnd.isEmpty()) {
            return unknownCounts;
        }
        return !e.end().isBefore(a) && !e.start().isAfter(b);
    }
}
