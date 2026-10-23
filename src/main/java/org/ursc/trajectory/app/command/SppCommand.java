package org.ursc.trajectory.app.command;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.ursc.trajectory.app.Args;
import org.ursc.trajectory.gnss.RinexNavReader;
import org.ursc.trajectory.gnss.RinexObsReader;
import org.ursc.trajectory.gnss.SppEngine;
import org.ursc.trajectory.gnss.SppSolution;
import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.io.TarGz;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Single Point Positioning from a GNSS observation file and a GPS broadcast
 * navigation file: computes a per-epoch receiver position and clock, optionally
 * scores against a POEORB precise-orbit truth file, and writes a per-epoch CSV.
 *
 * <pre>
 *   spp --obs &lt;file&gt; --nav &lt;file&gt; [--poeorb &lt;eof&gt;] [--decimate 30] [--out out.csv]
 * </pre>
 *
 * <p>{@code --obs} accepts a RINEX observation file (optionally {@code .gz}) or a
 * Sentinel-3A GNSSRD {@code .tgz} bundle (the {@code .DBL} is streamed out). The
 * ionosphere-free pseudorange is used. Positions are WGS-84 ECEF, directly
 * comparable to the ITRF POEORB truth; note the POEORB is the satellite centre of
 * mass while SPP solves the GPS antenna phase centre, a roughly constant few-metre
 * offset (attitude-based antenna correction is a later refinement).</p>
 */
public final class SppCommand implements Command {

    @Override
    public String name() {
        return "spp";
    }

    @Override
    public String description() {
        return "Single Point Positioning from GNSS observations + broadcast ephemeris";
    }

    @Override
    public int run(final Args args) throws Exception {
        final String obs = args.get("obs", null);
        final String nav = args.get("nav", null);
        if (obs == null || nav == null) {
            System.err.println("usage: spp --obs <rinex|.tgz> --nav <rinex-nav> "
                    + "[--poeorb <eof>] [--decimate 30] [--out out.csv]");
            return 2;
        }
        final double decimate = args.getDouble("decimate", 30.0);
        final String poeorb = args.get("poeorb", null);
        final String out = args.get("out", null);

        final RinexObsReader.Data obsData = readObs(Paths.get(obs), decimate);
        final RinexNavReader.Data navData = RinexNavReader.read(Paths.get(nav));
        System.out.printf(Locale.US, "Loaded %d obs epochs, %d nav messages for %d PRNs%n",
                obsData.size(), navData.messageCount(), navData.prns().size());

        final List<SppEngine.Fix> fixes = new SppEngine().run(obsData, navData);

        PoeOrbReader.Ephemeris truth = null;
        if (poeorb != null) {
            truth = PoeOrbReader.read(Paths.get(poeorb));
        }

        BufferedWriter csv = null;
        if (out != null) {
            csv = Files.newBufferedWriter(Paths.get(out), StandardCharsets.UTF_8);
            csv.write("epoch_gps,x_m,y_m,z_m,clock_m,nsat,postfit_rms_m,pdop,err_m");
            csv.newLine();
        }

        int solved = 0;
        final List<Double> residuals = new ArrayList<>();
        final List<Double> errors = new ArrayList<>();
        for (final SppEngine.Fix fix : fixes) {
            final SppSolution s = fix.getSolution();
            double err = Double.NaN;
            if (s != null) {
                solved++;
                residuals.add(s.getPostFitRms());
                if (truth != null) {
                    final Vector3D t = interpolate(truth, fix.getDate());
                    if (t != null) {
                        err = s.getPosition().distance(t);
                        errors.add(err);
                    }
                }
            }
            if (csv != null) {
                writeRow(csv, fix, s, err);
            }
        }
        if (csv != null) {
            csv.close();
            System.out.println("Wrote " + out);
        }

        System.out.printf(Locale.US, "Solved %d / %d epochs%n", solved, fixes.size());
        if (!residuals.isEmpty()) {
            System.out.printf(Locale.US, "Post-fit pseudorange residual: median %.2f m, RMS %.2f m%n",
                    median(residuals), rms(residuals));
        }
        if (!errors.isEmpty()) {
            System.out.printf(Locale.US,
                    "Position error vs POEORB: median %.2f m, RMS %.2f m, max %.2f m  (n=%d)%n",
                    median(errors), rms(errors), max(errors), errors.size());
            System.out.println("  (includes the ~constant GPS antenna-to-CoM offset; "
                    + "attitude-based antenna correction not yet applied)");
        }
        return 0;
    }

    private static RinexObsReader.Data readObs(final Path path, final double decimate)
            throws IOException {
        final String n = path.getFileName().toString();
        if (n.endsWith(".tgz") || n.endsWith(".tar.gz")) {
            try (InputStream in = TarGz.openEntry(path, ".DBL")) {
                return RinexObsReader.read(in, decimate);
            }
        }
        return RinexObsReader.read(path, decimate);
    }

    private static void writeRow(final BufferedWriter csv, final SppEngine.Fix fix,
                                 final SppSolution s, final double err) throws IOException {
        final double[] c = fix.getDate().getComponents(TimeScalesFactory.getGPS());
        final String epoch = String.format(Locale.US, "%04d-%02d-%02dT%02d:%02d:%06.3f",
                (int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4], c[5]);
        if (s == null) {
            csv.write(epoch + ",,,,,0,,,");
        } else {
            final Vector3D p = s.getPosition();
            csv.write(String.format(Locale.US, "%s,%.4f,%.4f,%.4f,%.4f,%d,%.3f,%.2f,%s",
                    epoch, p.getX(), p.getY(), p.getZ(), s.getClockBias(),
                    s.getUsedSatellites(), s.getPostFitRms(), s.getPdop(),
                    Double.isNaN(err) ? "" : String.format(Locale.US, "%.3f", err)));
        }
        csv.newLine();
    }

    /** Linear interpolation of the POEORB ITRF position at the given epoch (null if out of span). */
    private static Vector3D interpolate(final PoeOrbReader.Ephemeris truth, final AbsoluteDate t) {
        final List<AbsoluteDate> d = truth.dates;
        if (t.durationFrom(d.get(0)) < 0 || t.durationFrom(d.get(d.size() - 1)) > 0) {
            return null;
        }
        int lo = 0;
        int hi = d.size() - 1;
        while (hi - lo > 1) {
            final int mid = (lo + hi) >>> 1;
            if (d.get(mid).durationFrom(t) <= 0) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        final double span = d.get(hi).durationFrom(d.get(lo));
        final double f = span == 0.0 ? 0.0 : t.durationFrom(d.get(lo)) / span;
        final Vector3D a = truth.positionItrf.get(lo);
        final Vector3D b = truth.positionItrf.get(hi);
        return a.add(f, b.subtract(a));
    }

    private static double median(final List<Double> v) {
        final List<Double> s = new ArrayList<>(v);
        s.sort(Double::compareTo);
        final int n = s.size();
        return n % 2 == 1 ? s.get(n / 2) : 0.5 * (s.get(n / 2 - 1) + s.get(n / 2));
    }

    private static double rms(final List<Double> v) {
        double sum = 0.0;
        for (final double x : v) {
            sum += x * x;
        }
        return Math.sqrt(sum / v.size());
    }

    private static double max(final List<Double> v) {
        double m = 0.0;
        for (final double x : v) {
            m = Math.max(m, x);
        }
        return m;
    }
}
