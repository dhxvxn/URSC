package org.ursc.trajectory.bodies;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Reader for a JPL DE4xx binary ephemeris (the little-endian {@code lnxp*.4xx} "Linux" export),
 * providing geocentric Sun and Moon positions by Chebyshev interpolation. Only positions are
 * evaluated (velocities are not needed for third-body / radiation modelling).
 *
 * <p>The file is a sequence of fixed-length records: record 1 is a header (titles, the ephemeris
 * span {@code SS}, {@code AU}, {@code EMRAT}, and the {@code IPT} pointer table giving each body's
 * coefficient offset / count / sub-interval count), record 2 holds constant values, and records 3+
 * are the data records, each beginning with its {@code [startJD, endJD]} and holding the Chebyshev
 * coefficients. Positions come out in ICRF (&asymp; GCRF) in km and are converted to metres;
 * geocentric Sun = Sun<sub>SSB</sub> &minus; Earth<sub>SSB</sub>, with
 * Earth<sub>SSB</sub> = EMB &minus; Moon&#8338;&#8336;&#8338;/(1+EMRAT) and Moon geocentric taken
 * directly from the Moon body. DE time is TDB, approximated here by TT (|TDB&minus;TT|{@literal <}2 ms,
 * negligible for these perturbations).</p>
 *
 * <p>The file is read by path and never executed from; it is untrusted input.</p>
 */
public final class De440Ephemeris implements EphemerisSource {

    private static final int EMB = 3;   // Earth-Moon barycenter (body 3)
    private static final int MOON = 10; // Moon, geocentric (body 10)
    private static final int SUN = 11;  // Sun (body 11)

    private final ByteBuffer buf;
    private final double startJd;
    private final double spanDays;
    private final int recLenBytes;
    private final double emrat;
    private final int[][] ipt = new int[13][3]; // 1-indexed body -> (offset, ncoeff, nsub)

    public De440Ephemeris(final Path file) {
        final byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (final IOException e) {
            throw new UncheckedIOException("cannot read DE ephemeris " + file, e);
        }
        this.buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        int o = 252 + 2400;                 // skip 3x84-char titles + 400x6-char constant names
        this.startJd = buf.getDouble(o);
        o += 8;
        o += 8;                             // end JD (unused)
        this.spanDays = buf.getDouble(o);
        o += 8;
        o += 4;                             // NCON (unused)
        o += 8;                             // AU (unused)
        this.emrat = buf.getDouble(o);
        o += 8;
        int lastDouble = 0;
        for (int b = 1; b <= 12; b++) {
            final int off = buf.getInt(o);
            final int nc = buf.getInt(o + 4);
            final int ns = buf.getInt(o + 8);
            o += 12;
            ipt[b][0] = off;
            ipt[b][1] = nc;
            ipt[b][2] = ns;
            final int comp = (b == 12) ? 2 : 3;      // body 12 (nutation) has 2 components
            lastDouble = Math.max(lastDouble, off - 1 + nc * ns * comp);
        }
        o += 4;                             // NUMDE (unused)
        final int lo = buf.getInt(o);
        final int lnc = buf.getInt(o + 4);
        final int lns = buf.getInt(o + 8);
        lastDouble = Math.max(lastDouble, lo - 1 + lnc * lns * 3); // libration pointer
        this.recLenBytes = lastDouble * 8;
        if (bytes.length % recLenBytes != 0) {
            throw new IllegalArgumentException("DE ephemeris record length " + recLenBytes
                    + " does not divide file size " + bytes.length);
        }
    }

    /** @return the covered interval start (Julian date, TDB). */
    public double startJulianDate() {
        return startJd;
    }

    @Override
    public Vector3D sunGeocentric(final AbsoluteDate date) {
        final double jd = julianDateTT(date);
        final Vector3D sunSsb = position(SUN, jd);
        final Vector3D embSsb = position(EMB, jd);
        final Vector3D moonGeo = position(MOON, jd);
        final Vector3D earthSsb = embSsb.subtract(moonGeo.scalarMultiply(1.0 / (1.0 + emrat)));
        return sunSsb.subtract(earthSsb).scalarMultiply(1000.0); // km -> m
    }

    @Override
    public Vector3D moonGeocentric(final AbsoluteDate date) {
        return position(MOON, julianDateTT(date)).scalarMultiply(1000.0); // km -> m
    }

    private static double julianDateTT(final AbsoluteDate date) {
        return 2451545.0 + date.julianCenturiesTT() * 36525.0;
    }

    /** Chebyshev position (km, ICRF) of a DE body at a Julian date. */
    private Vector3D position(final int body, final double jd) {
        final int recIdx = (int) Math.floor((jd - startJd) / spanDays);
        final int recBase = (2 + recIdx) * recLenBytes;          // records 3+ are data (0-indexed 2+)
        final double recStart = buf.getDouble(recBase);

        final int off0 = ipt[body][0] - 1;                       // 0-indexed within record
        final int nc = ipt[body][1];
        final int ns = ipt[body][2];
        final double seg = (jd - recStart) / spanDays * ns;
        int isub = (int) Math.floor(seg);
        if (isub < 0) {
            isub = 0;
        } else if (isub >= ns) {
            isub = ns - 1;
        }
        final double tau = 2.0 * (seg - isub) - 1.0;

        final double[] cheby = chebyshev(tau, nc);
        final double[] p = new double[3];
        for (int c = 0; c < 3; c++) {
            final int base = recBase + (off0 + isub * nc * 3 + c * nc) * 8;
            double sum = 0;
            for (int k = 0; k < nc; k++) {
                sum += buf.getDouble(base + k * 8) * cheby[k];
            }
            p[c] = sum;
        }
        return new Vector3D(p[0], p[1], p[2]);
    }

    private static double[] chebyshev(final double tau, final int n) {
        final double[] t = new double[n];
        t[0] = 1.0;
        if (n > 1) {
            t[1] = tau;
        }
        for (int k = 2; k < n; k++) {
            t[k] = 2.0 * tau * t[k - 1] - t[k - 2];
        }
        return t;
    }
}
