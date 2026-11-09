package org.ursc.trajectory.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.ursc.trajectory.gnss.RinexObsReader;
import org.ursc.trajectory.io.ProductInput;
import org.ursc.trajectory.io.TarGz;

/** Loads GNSSRD archives / RINEX observation files and merges their epochs in time order. */
public final class ObsLoader {

    /** Merged observations plus the spacecraft identity inferred from file names. */
    public static final class Loaded {
        public final List<RinexObsReader.Epoch> epochs = new ArrayList<>();
        public String objectName = "";
        public String objectId = "";
        public final List<String> problems = new ArrayList<>();
    }

    private ObsLoader() {
    }

    public static Loaded load(final List<Path> files, final double samplingSeconds) {
        final Loaded out = new Loaded();
        final Map<Double, RinexObsReader.Epoch> byTime = new TreeMap<>();
        for (final Path f : files) {
            final String n = f.getFileName().toString();
            try {
                final RinexObsReader.Data d;
                if (n.endsWith(".tgz") || n.endsWith(".tar.gz") || n.endsWith(".TGZ")) {
                    try (InputStream in = TarGz.openEntry(f, ".DBL")) {
                        d = RinexObsReader.read(in, samplingSeconds);
                    }
                } else {
                    try (InputStream in = ProductInput.open(f)) {
                        d = RinexObsReader.read(in, samplingSeconds);
                    }
                }
                for (final RinexObsReader.Epoch e : d.getEpochs()) {
                    byTime.putIfAbsent(e.getDate().durationFromJ2000(), e); // first file wins on overlap
                }
                if (out.objectName.isEmpty()) {
                    identify(n, out);
                }
            } catch (final IOException | RuntimeException e) {
                out.problems.add(n + ": unreadable GNSS observation file (" + e.getMessage() + ")");
            }
        }
        out.epochs.addAll(byTime.values());
        out.epochs.sort(Comparator.comparingDouble(e -> e.getDate().durationFromJ2000()));
        return out;
    }

    /** Sentinel product names start with the mission code (S3A_, S3B_, S6A_...). */
    static void identify(final String fileName, final Loaded out) {
        final String u = fileName.toUpperCase(Locale.US);
        if (u.startsWith("S3A_")) {
            out.objectName = "SENTINEL-3A";
            out.objectId = "2016-011A";
        } else if (u.startsWith("S3B_")) {
            out.objectName = "SENTINEL-3B";
            out.objectId = "2018-039A";
        } else if (u.startsWith("S6A_")) {
            out.objectName = "SENTINEL-6A";
            out.objectId = "2020-086A";
        } else {
            final int us = u.indexOf('_');
            out.objectName = us > 0 ? u.substring(0, us) : "SPACECRAFT";
        }
    }
}
