package org.ursc.trajectory.attitude;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Measured attitude read from a Sentinel-3 CPOD {@code AUX_PROQUA} quaternion file (the
 * {@code .DBL} ASCII product). Each record is
 * <pre>
 *   YYYY/MM/DD HH:MM:SS.sss  Q_COMPR Q_COMP1 Q_COMP2 Q_COMP3  ATT_MODE  SOURCE
 * </pre>
 * at 1 Hz in <b>GPS</b> time, with a scalar-first unit quaternion {@code (qr, q1, q2, q3)}.
 * GPS&rarr;UTC/TAI is handled by {@link TimeScalesFactory#getGPS()} when the epoch is built, so
 * no manual leap-second offset is applied here. The attitude quaternion is taken to rotate the
 * body frame into the (quasi-)inertial frame, so the columns of its rotation matrix are the body
 * axes expressed in inertial — i.e. the frame of the queried {@link SpacecraftState} (GCRF/J2000,
 * which differ by {@literal <}0.1&Prime;). Queries are interpolated (normalised linear interpolation,
 * exact enough at 1 Hz) and clamped to the file's coverage.
 *
 * <p>The file is read by path and never executed from; it is untrusted input.</p>
 */
public final class QuaternionFileAttitude implements AttitudeProvider {

    private final AbsoluteDate first;
    private final double[] t;      // seconds from the first sample
    private final double[][] q;    // [n][4] scalar-first, unit
    private final boolean bodyToInertial;

    /**
     * @param dbl path to the AUX_PROQUA {@code .DBL} file
     * @param bodyToInertial {@code true} if the stored quaternion rotates body&rarr;inertial
     *                       (columns of R are the body axes in inertial); {@code false} for the
     *                       inverse convention (the transpose is used)
     */
    public QuaternionFileAttitude(final Path dbl, final boolean bodyToInertial) {
        this.bodyToInertial = bodyToInertial;
        final List<AbsoluteDate> dates = new ArrayList<>();
        final List<double[]> quats = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(dbl)) {
            String line;
            while ((line = br.readLine()) != null) {
                final String s = line.trim();
                if (s.isEmpty() || s.charAt(0) == '#') {
                    continue;
                }
                final String[] tok = s.split("\\s+");
                if (tok.length < 6) {
                    continue;
                }
                final String[] ymd = tok[0].split("/");
                final String[] hms = tok[1].split(":");
                final int year = Integer.parseInt(ymd[0]);
                final int month = Integer.parseInt(ymd[1]);
                final int day = Integer.parseInt(ymd[2]);
                final int hour = Integer.parseInt(hms[0]);
                final int minute = Integer.parseInt(hms[1]);
                final double second = Double.parseDouble(hms[2]);
                dates.add(new AbsoluteDate(year, month, day, hour, minute, second,
                        TimeScalesFactory.getGPS()));
                final double qr = Double.parseDouble(tok[2]);
                final double q1 = Double.parseDouble(tok[3]);
                final double q2 = Double.parseDouble(tok[4]);
                final double q3 = Double.parseDouble(tok[5]);
                final double n = Math.sqrt(qr * qr + q1 * q1 + q2 * q2 + q3 * q3);
                quats.add(new double[] {qr / n, q1 / n, q2 / n, q3 / n});
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("cannot read quaternion file " + dbl, e);
        }
        if (dates.isEmpty()) {
            throw new IllegalArgumentException("no attitude records in " + dbl);
        }
        this.first = dates.get(0);
        final int nRec = dates.size();
        this.t = new double[nRec];
        this.q = new double[nRec][];
        for (int i = 0; i < nRec; i++) {
            t[i] = dates.get(i).durationFrom(first);
            q[i] = quats.get(i);
        }
    }

    /** @return the first sample epoch (coverage start). */
    public AbsoluteDate coverageStart() {
        return first;
    }

    /** @return the last sample epoch (coverage end). */
    public AbsoluteDate coverageEnd() {
        return first.shiftedBy(t[t.length - 1]);
    }

    /** @return {@code true} if the date lies within the file's sampled interval. */
    public boolean covers(final AbsoluteDate date) {
        final double dt = date.durationFrom(first);
        return dt >= t[0] && dt <= t[t.length - 1];
    }

    @Override
    public BodyFrame attitude(final SpacecraftState state) {
        final double[] qi = interpolate(state.getDate().durationFrom(first));
        return toBodyFrame(qi);
    }

    /** Normalised linear interpolation of the bracketing quaternions, clamped to coverage. */
    private double[] interpolate(final double dt) {
        if (dt <= t[0]) {
            return q[0];
        }
        if (dt >= t[t.length - 1]) {
            return q[t.length - 1];
        }
        int lo = 0;
        int hi = t.length - 1;
        while (hi - lo > 1) {
            final int mid = (lo + hi) >>> 1;
            if (t[mid] <= dt) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        final double span = t[hi] - t[lo];
        final double a = span > 0 ? (dt - t[lo]) / span : 0.0;
        final double[] qa = q[lo];
        double[] qb = q[hi];
        double dot = qa[0] * qb[0] + qa[1] * qb[1] + qa[2] * qb[2] + qa[3] * qb[3];
        if (dot < 0) {                  // take the shorter arc
            qb = new double[] {-qb[0], -qb[1], -qb[2], -qb[3]};
        }
        final double[] out = new double[4];
        double n2 = 0;
        for (int k = 0; k < 4; k++) {
            out[k] = qa[k] + a * (qb[k] - qa[k]);
            n2 += out[k] * out[k];
        }
        final double n = Math.sqrt(n2);
        for (int k = 0; k < 4; k++) {
            out[k] /= n;
        }
        return out;
    }

    /** Body axes (columns of the body&rarr;inertial rotation matrix) from a scalar-first quaternion. */
    private BodyFrame toBodyFrame(final double[] qq) {
        final double w = qq[0];
        final double x = qq[1];
        final double y = qq[2];
        final double z = qq[3];
        // columns of the rotation matrix mapping body-axis unit vectors into inertial
        final Vector3D cx = new Vector3D(1 - 2 * (y * y + z * z), 2 * (x * y + w * z), 2 * (x * z - w * y));
        final Vector3D cy = new Vector3D(2 * (x * y - w * z), 1 - 2 * (x * x + z * z), 2 * (y * z + w * x));
        final Vector3D cz = new Vector3D(2 * (x * z + w * y), 2 * (y * z - w * x), 1 - 2 * (x * x + y * y));
        if (bodyToInertial) {
            return new BodyFrame(cx, cy, cz);
        }
        // inverse convention: the rotation maps inertial->body, so the body axes in inertial are
        // the rows of R (equivalently the columns of its transpose).
        return new BodyFrame(
                new Vector3D(cx.getX(), cy.getX(), cz.getX()),
                new Vector3D(cx.getY(), cy.getY(), cz.getY()),
                new Vector3D(cx.getZ(), cy.getZ(), cz.getZ()));
    }
}
