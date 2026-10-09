package org.ursc.trajectory.forces.gravity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.ursc.trajectory.forces.ForceModel;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.frames.Transform;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Ocean tides: the periodic redistribution of ocean mass perturbs the
 * geopotential. Implements the IERS-2010 formulation (eq. 6.15) driven by a
 * bundled truncation of the FES2004 model &mdash; the 8 dominant constituents
 * (Q1, O1, P1, K1, N2, M2, S2, K2) to degree and order 4:
 * <pre>
 *   dC̄_nm = Σ_f [ (C+ + C−) cosθ_f + (S+ + S−) sinθ_f ]
 *   dS̄_nm = Σ_f [ (S+ − S−) cosθ_f − (C+ − C−) sinθ_f ]
 * </pre>
 * where θ_f is the Doodson argument of constituent f. Smaller than the solid
 * tide (~1e-9 m/s^2 at LEO); included for completeness. A fuller model can be
 * supplied by replacing the bundled coefficient file.
 */
public final class OceanTides implements ForceModel {

    private static final String RESOURCE = "/gravity/fes2004_8const.dat";
    private static final double UNIT = 1.0e-11; // file coefficients are in 1e-11

    /** One tidal constituent: its Doodson multipliers and per-(n,m) coefficients. */
    private static final class Constituent {
        final int[] k = new int[6];               // Doodson multipliers
        final List<int[]> nm = new ArrayList<>();  // (n, m) pairs
        final List<double[]> cs = new ArrayList<>(); // {C+, S+, C-, S-} per nm
    }

    private final double mu;
    private final double referenceRadius;
    private final int maxDegree;
    private final List<Constituent> constituents;

    public OceanTides(final double mu, final double referenceRadius) {
        this.mu = mu;
        this.referenceRadius = referenceRadius;
        this.constituents = load();
        int maxN = 0;
        for (final Constituent c : constituents) {
            for (final int[] nm : c.nm) {
                maxN = Math.max(maxN, nm[0]);
            }
        }
        this.maxDegree = maxN;
    }

    /** Default: bundled FES2004 subset with EGM96 GM and radius. */
    public OceanTides() {
        this(GravityFieldFactory.EGM96_MU, GravityFieldFactory.EGM96_RADIUS);
    }

    @Override
    public Vector3D acceleration(final SpacecraftState state) {
        final AbsoluteDate date = state.getDate();
        final double[] beta = FundamentalArguments.doodson(date);

        final double[][] dC = new double[maxDegree + 1][maxDegree + 1];
        final double[][] dS = new double[maxDegree + 1][maxDegree + 1];

        for (final Constituent c : constituents) {
            double theta = 0.0;
            for (int i = 0; i < 6; i++) {
                theta += c.k[i] * beta[i];
            }
            final double cosT = Math.cos(theta);
            final double sinT = Math.sin(theta);
            for (int idx = 0; idx < c.nm.size(); idx++) {
                final int n = c.nm.get(idx)[0];
                final int m = c.nm.get(idx)[1];
                if (n < 2) {
                    continue;
                }
                final double[] v = c.cs.get(idx);
                final double cPlus = v[0];
                final double sPlus = v[1];
                final double cMinus = v[2];
                final double sMinus = v[3];
                dC[n][m] += UNIT * ((cPlus + cMinus) * cosT + (sPlus + sMinus) * sinT);
                dS[n][m] += UNIT * ((sPlus - sMinus) * cosT - (cPlus - cMinus) * sinT);
            }
        }

        // de-normalise and evaluate (central term C00 = 0)
        for (int n = 2; n <= maxDegree; n++) {
            for (int m = 0; m <= n; m++) {
                final double f = GravityFieldFactory.denormalizationFactor(n, m);
                dC[n][m] *= f;
                dS[n][m] *= f;
            }
        }
        final GravityField deltaField =
                new GravityField(mu, referenceRadius, maxDegree, maxDegree, dC, dS);
        final Transform toBody =
                FramesFactory.getTransform(state.getFrame(), FramesFactory.getITRF(), date);
        final Vector3D rSat = toBody.transformPosition(state.getPosition());
        final Vector3D accBody = GravitationalGradient.acceleration(deltaField, maxDegree, maxDegree, rSat);
        return toBody.getRotation().applyInverseTo(accBody);
    }

    private static List<Constituent> load() {
        final List<Constituent> list = new ArrayList<>();
        Constituent current = null;
        String currentDoodson = null;
        try (InputStream in = OceanTides.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("bundled ocean-tide resource not found: " + RESOURCE);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String s = line.trim();
                    if (s.isEmpty() || s.startsWith("#")) {
                        continue;
                    }
                    final String[] tok = s.split("\\s+");
                    // Doodson Darwin l m DelC+ DelS+ DelC- DelS-
                    final String doodson = tok[0];
                    final int n = Integer.parseInt(tok[2]);
                    final int m = Integer.parseInt(tok[3]);
                    final double cp = Double.parseDouble(tok[4]);
                    final double sp = Double.parseDouble(tok[5]);
                    final double cm = Double.parseDouble(tok[6]);
                    final double sm = Double.parseDouble(tok[7]);
                    if (!doodson.equals(currentDoodson)) {
                        current = new Constituent();
                        decodeDoodson(doodson, current.k);
                        list.add(current);
                        currentDoodson = doodson;
                    }
                    current.nm.add(new int[] {n, m});
                    current.cs.add(new double[] {cp, sp, cm, sm});
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("cannot read ocean-tide coefficients", e);
        }
        return list;
    }

    /**
     * Decode a Doodson number like {@code 255.555} into the six multipliers: the
     * first digit is the tau coefficient; the remaining five are each offset by 5.
     */
    private static void decodeDoodson(final String doodson, final int[] k) {
        final String digits = doodson.replace(".", "");
        k[0] = digits.charAt(0) - '0';
        for (int i = 1; i < 6; i++) {
            k[i] = (digits.charAt(i) - '0') - 5;
        }
    }

    @Override
    public String getName() {
        return "OceanTides(FES2004-8)";
    }
}
