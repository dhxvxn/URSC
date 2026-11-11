package org.ursc.trajectory.gnss.products;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.ursc.trajectory.gnss.RinexNavReader;
import org.ursc.trajectory.gnss.products.GnssOrbitClockSource.ProductClass;
import org.ursc.trajectory.io.ProductInput;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Builds {@link GnssOrbitClockSource}s from GPS product files of any supported kind.
 * File kind is detected from content (SP3 header, RINEX type letter); product class,
 * provider and span come from the IGS file name. SP3 and CLK files of the same class,
 * provider and start are paired so a source's orbit and clock always come from the same
 * product. Unrecognised precise files are reported, never guessed.
 */
public final class ProductLoader {

    /** Content kinds the loader recognises. */
    public enum Kind { SP3, CLOCK, NAVIGATION, OTHER }

    /** Result: usable sources plus human-readable problems. */
    public static final class Loaded {
        public final List<GnssOrbitClockSource> sources = new ArrayList<>();
        public final List<String> problems = new ArrayList<>();
    }

    private ProductLoader() {
    }

    /** Detect the content kind of a (possibly compressed) file from its first lines. */
    public static Kind sniff(final Path path) {
        try (InputStream in = ProductInput.open(path);
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII))) {
            final String first = br.readLine();
            if (first == null) {
                return Kind.OTHER;
            }
            if (first.length() > 2 && first.charAt(0) == '#' && "cdCD".indexOf(first.charAt(1)) >= 0) {
                return Kind.SP3;
            }
            if (first.contains("RINEX VERSION / TYPE") && first.length() > 20) {
                final char type = Character.toUpperCase(first.charAt(20));
                if (type == 'C') {
                    return Kind.CLOCK;
                }
                if (type == 'N') {
                    return Kind.NAVIGATION;
                }
            }
        } catch (final IOException | RuntimeException e) {
            return Kind.OTHER;
        }
        return Kind.OTHER;
    }

    /**
     * Load every GPS orbit/clock product among {@code files}.
     *
     * @param files         candidate files (non-product files are ignored)
     * @param issueOverride verified issue times keyed by file name (e.g. from an archive log);
     *                      may be empty
     */
    public static Loaded load(final List<Path> files, final Map<String, AbsoluteDate> issueOverride) {
        final Loaded out = new Loaded();
        final Map<String, Path> sp3 = new LinkedHashMap<>();
        final Map<String, Path> clk = new LinkedHashMap<>();
        final List<RinexNavReader.Data> navParts = new java.util.ArrayList<>();
        final List<String> navNames = new java.util.ArrayList<>();
        for (final Path f : files) {
            final Kind kind = sniff(f);
            if (kind == Kind.NAVIGATION) {
                try {
                    final RinexNavReader.Data nav = readNav(f);
                    if (nav.messageCount() == 0) {
                        out.problems.add(f.getFileName() + ": navigation file has no GPS LNAV messages");
                    } else {
                        navParts.add(nav);
                        navNames.add(f.getFileName().toString());
                    }
                } catch (final IOException | RuntimeException e) {
                    out.problems.add(f.getFileName() + ": unreadable navigation file (" + e.getMessage() + ")");
                }
                continue;
            }
            if (kind != Kind.SP3 && kind != Kind.CLOCK) {
                continue;
            }
            final IgsProductName name = IgsProductName.parse(ProductInput.baseName(f));
            if (!name.recognised()) {
                out.problems.add(f.getFileName() + ": " + kind
                        + " file name is not a recognised IGS Final/Rapid/Ultra-rapid name; product class unknown, not used");
                continue;
            }
            final String key = name.productClass() + "|" + name.provider() + "|" + name.start();
            (kind == Kind.SP3 ? sp3 : clk).put(key, f);
        }
        if (!navParts.isEmpty()) {
            // all broadcast files form ONE source: otherwise the first file (e.g. the previous
            // day) would serve epochs with its old messages while a newer message exists
            out.sources.add(new BroadcastSource(RinexNavReader.Data.merge(navParts),
                    "broadcast " + String.join(" + ", navNames)));
        }
        for (final Map.Entry<String, Path> e : sp3.entrySet()) {
            final Path orbitFile = e.getValue();
            final Path clockFile = clk.remove(e.getKey());
            final IgsProductName name = IgsProductName.parse(ProductInput.baseName(orbitFile));
            try {
                final Sp3Reader.Data orbit = Sp3Reader.read(orbitFile);
                final RinexClkReader.Data clock = clockFile == null ? null : RinexClkReader.read(clockFile);
                AbsoluteDate issue = name.nominalIssueTime();
                boolean verified = false;
                final AbsoluteDate o = issueOverride.get(orbitFile.getFileName().toString());
                if (o != null) {
                    issue = o;
                    verified = true;
                }
                final String desc = name.productClass().label + " (" + name.provider() + ") "
                        + orbitFile.getFileName() + (clockFile == null ? " [SP3 clocks]" : " + " + clockFile.getFileName());
                out.sources.add(new PreciseSource(orbit, clock, name.productClass(), desc, issue, verified,
                        name.productClass() == ProductClass.ULTRA_RAPID ? name.observedEnd() : null));
            } catch (final IOException | RuntimeException ex) {
                out.problems.add(orbitFile.getFileName() + ": unreadable SP3 (" + ex.getMessage() + ")");
            }
        }
        for (final Path orphan : clk.values()) {
            out.problems.add(orphan.getFileName() + ": clock file without a matching SP3 orbit; not used");
        }
        return out;
    }

    private static RinexNavReader.Data readNav(final Path f) throws IOException {
        try (InputStream in = ProductInput.open(f)) {
            return RinexNavReader.read(in);
        }
    }
}
