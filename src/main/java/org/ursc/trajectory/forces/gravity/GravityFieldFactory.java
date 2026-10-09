package org.ursc.trajectory.forces.gravity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import org.ursc.trajectory.math.Constants;

/**
 * Builds {@link GravityField} instances, either from the embedded default zonal
 * model or by reading a coefficient file.
 */
public final class GravityFieldFactory {

    // Un-normalised zonal coefficients C(n,0) = -J(n) (EGM96/JGM-3 low degree).
    private static final double J2 = 1.08262668e-3;
    private static final double J3 = -2.53265648e-6;
    private static final double J4 = -1.61962159e-6;
    private static final double J5 = -2.27296083e-7;
    private static final double J6 = 5.40681239e-7;

    /** EGM96 gravitational constant GM (m^3/s^2), as published with the model. */
    public static final double EGM96_MU = 3.986004415e14;
    /** EGM96 reference radius (m). */
    public static final double EGM96_RADIUS = 6378136.3;
    /** Maximum degree/order of the bundled EGM96 (satellite-only) field. */
    public static final int EGM96_MAX_DEGREE = 70;

    private static final String EGM96_RESOURCE = "/gravity/egm96s_to70.gfc.gz";

    private GravityFieldFactory() {
    }

    /**
     * Load the bundled EGM96 field (satellite-only solution, complete to degree and
     * order 70) truncated to the requested degree, with order equal to that degree
     * so any order &le; degree may be selected by the force model.
     *
     * @param maxDegree degree to retain (1..70)
     * @return the de-normalised gravity field
     */
    public static GravityField getEgm96(final int maxDegree) {
        if (maxDegree < 1 || maxDegree > EGM96_MAX_DEGREE) {
            throw new IllegalArgumentException(
                    "EGM96 degree must be between 1 and " + EGM96_MAX_DEGREE + ", got " + maxDegree);
        }
        try (InputStream raw = GravityFieldFactory.class.getResourceAsStream(EGM96_RESOURCE)) {
            if (raw == null) {
                throw new IllegalStateException("bundled EGM96 resource not found: " + EGM96_RESOURCE);
            }
            try (InputStream in = new GZIPInputStream(raw)) {
                return loadNormalized(in, EGM96_MU, EGM96_RADIUS, maxDegree, maxDegree);
            }
        } catch (final IOException e) {
            throw new IllegalStateException("cannot read bundled EGM96 field", e);
        }
    }

    /**
     * The embedded default field: central term plus zonal harmonics J2..J6, which
     * drive the dominant long-period LEO perturbations (nodal regression and
     * apsidal rotation). Order is 0 (no tesseral terms).
     */
    public static GravityField getDefaultZonalField() {
        final int n = 6;
        final double[][] c = new double[n + 1][n + 1];
        final double[][] s = new double[n + 1][n + 1];
        c[0][0] = 1.0;
        c[2][0] = -J2;
        c[3][0] = -J3;
        c[4][0] = -J4;
        c[5][0] = -J5;
        c[6][0] = -J6;
        return new GravityField(Constants.EARTH_MU, Constants.EARTH_EQUATORIAL_RADIUS, n, 0, c, s);
    }

    /** Load a normalised coefficient file from a path. */
    public static GravityField loadNormalized(final Path path, final double mu,
                                              final double referenceRadius,
                                              final int maxDegree, final int maxOrder)
            throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return loadNormalized(in, mu, referenceRadius, maxDegree, maxOrder);
        }
    }

    /**
     * Load fully-normalised spherical-harmonic coefficients from a stream and
     * de-normalise them. The reader accepts simple whitespace-separated records of
     * the form {@code n m Cbar Sbar} (extra columns, blank lines and lines whose
     * first token is not an integer are ignored, so ICGEM {@code gfc} lines are
     * handled when the leading {@code gfc} token is stripped by the caller or when
     * the key column is tolerated).
     *
     * @param in              the input stream
     * @param mu              reference GM of the field (m^3/s^2)
     * @param referenceRadius reference radius of the field (m)
     * @param maxDegree       maximum degree to retain
     * @param maxOrder        maximum order to retain
     */
    public static GravityField loadNormalized(final InputStream in, final double mu,
                                              final double referenceRadius,
                                              final int maxDegree, final int maxOrder)
            throws IOException {
        final double[][] c = new double[maxDegree + 1][maxDegree + 1];
        final double[][] s = new double[maxDegree + 1][maxDegree + 1];
        c[0][0] = 1.0;

        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] tokens = trimmed.split("\\s+");
                // tolerate an ICGEM "gfc" leading key
                int base = 0;
                if (tokens.length > 0 && !isInteger(tokens[0])) {
                    base = 1;
                }
                if (tokens.length - base < 4) {
                    continue;
                }
                if (!isInteger(tokens[base]) || !isInteger(tokens[base + 1])) {
                    continue;
                }
                final int n = Integer.parseInt(tokens[base]);
                final int m = Integer.parseInt(tokens[base + 1]);
                if (n > maxDegree || m > maxOrder || m > n) {
                    continue;
                }
                final double cBar = parseDouble(tokens[base + 2]);
                final double sBar = parseDouble(tokens[base + 3]);
                final double k = denormalizationFactor(n, m);
                c[n][m] = cBar * k;
                s[n][m] = sBar * k;
            }
        }
        if (c[0][0] == 0.0) {
            c[0][0] = 1.0;
        }
        return new GravityField(mu, referenceRadius, maxDegree, maxOrder, c, s);
    }

    /**
     * Factor converting a fully-normalised coefficient into its un-normalised
     * value: {@code sqrt((2 - delta0m)(2n+1)(n-m)!/(n+m)!)}.
     */
    public static double denormalizationFactor(final int n, final int m) {
        final double delta = (m == 0) ? 1.0 : 2.0;
        double ratio = 1.0; // (n-m)!/(n+m)! = 1 / prod_{k=n-m+1}^{n+m} k
        for (int k = n - m + 1; k <= n + m; k++) {
            ratio /= k;
        }
        return Math.sqrt(delta * (2.0 * n + 1.0) * ratio);
    }

    private static boolean isInteger(final String token) {
        if (token.isEmpty()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            final char ch = token.charAt(i);
            if (i == 0 && (ch == '+' || ch == '-')) {
                continue;
            }
            if (!Character.isDigit(ch)) {
                return false;
            }
        }
        return true;
    }

    private static double parseDouble(final String token) {
        // accept Fortran-style exponents (D/d) found in some field files
        return Double.parseDouble(token.replace('D', 'E').replace('d', 'e'));
    }
}
