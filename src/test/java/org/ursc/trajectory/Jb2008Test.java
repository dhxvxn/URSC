package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.forces.drag.jb2008.Jb2008;

/**
 * Cross-validates the from-scratch URSC {@link Jb2008} engine against reference densities produced
 * by the Orekit 13.1.9 JB2008 realisation (its OREKIT-independent 15-argument entry point), for a
 * spread of altitudes (400-1000 km), geometries, solar indices and storm levels. Agreement to a
 * tight relative tolerance confirms the algorithm and all Bowman coefficients were ported correctly.
 */
class Jb2008Test {

    // {dateMJD, sunRA, sunDecli, satLon, satLat, satAltM, f10, f10b, s10, s10b, xm10, xm10b, y10, y10b, dstdtc, orekitRho}
    private static final double[][] REF = {
        {54000.5000, 0.0000, 0.0000, 0.0000, 0.0000, 400000.0, 150, 150, 150, 150, 150, 150, 150, 150, 0.0, 5.120137e-12},
        {54000.3000, 1.2000, -0.3500, 2.0000, 0.6000, 400000.0, 120, 115, 110, 112, 118, 116, 100, 105, 0.0, 2.284992e-12},
        {60590.5000, 3.0000, -0.1000, -1.5000, -0.7000, 800000.0, 276.5, 209.0, 187.2, 180.9, 216.6, 210.8, 197.4, 191.8, 85.0, 7.355656e-14},
        {60590.9000, 0.5000, 0.2000, 0.3000, 0.9000, 800000.0, 170.9, 203.2, 162.9, 178.2, 183.0, 206.7, 180.0, 190.7, 200.0, 9.876756e-14},
        {55200.2500, -2.0000, 0.4000, 1.0000, -0.3000, 550000.0, 90, 92, 88, 90, 95, 93, 80, 82, 30.0, 2.735529e-14},
        {58849.7500, 2.5000, -0.2000, -2.8000, 0.2000, 1000000.0, 70, 72, 68, 70, 72, 71, 60, 62, 10.0, 1.343264e-15},
    };

    @Test
    void matchesOrekitReference() {
        final Jb2008 jb = new Jb2008();
        for (final double[] c : REF) {
            final double rho = jb.density(c[0], c[1], c[2], c[3], c[4], c[5],
                    c[6], c[7], c[8], c[9], c[10], c[11], c[12], c[13], c[14]);
            final double expected = c[15];
            final double relErr = Math.abs(rho - expected) / expected;
            assertTrue(relErr < 1.0e-4,
                    String.format("JB2008 density mismatch: got %.6e, expected %.6e (rel %.2e)", rho, expected, relErr));
        }
    }

    @Test
    void stormIncreasesDensity() {
        // same geometry/indices, only the DTC storm correction differs -> higher density
        final Jb2008 jb = new Jb2008();
        final double quiet = jb.density(60590.5, 3.0, -0.1, -1.5, -0.7, 800000.0,
                276.5, 209.0, 187.2, 180.9, 216.6, 210.8, 197.4, 191.8, 0.0);
        final double storm = jb.density(60590.5, 3.0, -0.1, -1.5, -0.7, 800000.0,
                276.5, 209.0, 187.2, 180.9, 216.6, 210.8, 197.4, 191.8, 200.0);
        assertTrue(storm > quiet, "storm DTC should raise density: quiet=" + quiet + " storm=" + storm);
    }
}
