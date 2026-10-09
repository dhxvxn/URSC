package org.ursc.trajectory.forces.gravity;

import org.ursc.trajectory.bodies.CelestialBody;
import org.ursc.trajectory.bodies.CelestialBodyFactory;
import org.ursc.trajectory.forces.ForceModel;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.frames.Transform;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Solid Earth tides: the deformation of the Earth by the Sun and Moon perturbs
 * the geopotential. Implements the IERS-2010 frequency-independent (Step 1) model
 * (Conventions eqs 6.6-6.7): the tide-generating bodies induce corrections
 * &Delta;C̄<sub>nm</sub>, &Delta;S̄<sub>nm</sub> to the normalised geopotential
 * coefficients for degree 2 and 3, plus the degree-4 corrections from the
 * degree-2 tide (the k⁺ terms).
 *
 * <p>The resulting small field (with C(0,0)=0) is evaluated with the shared
 * {@link GravitationalGradient}. The small frequency-dependent (Step 2) and
 * anelastic imaginary terms are omitted — negligible for LEO trajectory work.</p>
 */
public final class SolidTides implements ForceModel {

    // IERS-2010 Table 6.3 nominal Love numbers (real part)
    private static final double[] K2 = {0.29525, 0.29470, 0.29801};
    private static final double[] K3 = {0.093, 0.093, 0.093, 0.094};
    private static final double[] K2_PLUS = {-0.00087, -0.00079, -0.00057};

    private final double mu;
    private final double referenceRadius;
    private final CelestialBody[] bodies;

    public SolidTides(final double mu, final double referenceRadius,
                      final CelestialBody... bodies) {
        this.mu = mu;
        this.referenceRadius = referenceRadius;
        this.bodies = bodies.clone();
    }

    /** Default: Sun and Moon, EGM96 GM and radius. */
    public SolidTides() {
        this(GravityFieldFactory.EGM96_MU, GravityFieldFactory.EGM96_RADIUS,
                CelestialBodyFactory.getMoon(), CelestialBodyFactory.getSun());
    }

    @Override
    public Vector3D acceleration(final SpacecraftState state) {
        final Transform toBody =
                FramesFactory.getTransform(state.getFrame(), FramesFactory.getITRF(), state.getDate());

        final double[][] dC = new double[5][5];
        final double[][] dS = new double[5][5];

        for (final CelestialBody body : bodies) {
            final Vector3D rb = toBody.transformPosition(
                    body.getPosition(state.getDate(), state.getFrame()));
            final double r = rb.getNorm();
            final double sinPhi = rb.getZ() / r;
            final double lambda = Math.atan2(rb.getY(), rb.getX());
            final double gmRatio = body.getMu() / mu;

            accumulate(dC, dS, 2, K2, gmRatio, r, sinPhi, lambda);
            accumulate(dC, dS, 3, K3, gmRatio, r, sinPhi, lambda);

            // degree-4 corrections from the degree-2 tide (IERS eq 6.7)
            final double reOverR3 = Math.pow(referenceRadius / r, 3.0);
            for (int m = 0; m <= 2; m++) {
                final double pBar = GravityFieldFactory.denormalizationFactor(2, m)
                        * legendre(2, m, sinPhi);
                final double common = gmRatio * reOverR3 * pBar;
                final double coeff = K2_PLUS[m] / 5.0;
                dC[4][m] += coeff * common * Math.cos(m * lambda);
                dS[4][m] += coeff * common * Math.sin(m * lambda);
            }
        }

        // de-normalise the deltas and evaluate the gradient (central term C00 = 0)
        for (int n = 2; n <= 4; n++) {
            for (int m = 0; m <= n; m++) {
                final double f = GravityFieldFactory.denormalizationFactor(n, m);
                dC[n][m] *= f;
                dS[n][m] *= f;
            }
        }
        final GravityField deltaField = new GravityField(mu, referenceRadius, 4, 4, dC, dS);
        final Vector3D rSat = toBody.transformPosition(state.getPosition());
        final Vector3D accBody = GravitationalGradient.acceleration(deltaField, 4, 4, rSat);
        return toBody.getRotation().applyInverseTo(accBody);
    }

    private void accumulate(final double[][] dC, final double[][] dS, final int n,
                            final double[] k, final double gmRatio, final double r,
                            final double sinPhi, final double lambda) {
        final double reOverRpow = Math.pow(referenceRadius / r, n + 1);
        for (int m = 0; m <= n; m++) {
            final double pBar = GravityFieldFactory.denormalizationFactor(n, m) * legendre(n, m, sinPhi);
            final double common = gmRatio * reOverRpow * pBar;
            final double coeff = k[m] / (2.0 * n + 1.0);
            dC[n][m] += coeff * common * Math.cos(m * lambda);
            dS[n][m] += coeff * common * Math.sin(m * lambda);
        }
    }

    /** Conventional (geodesy, no Condon-Shortley phase) associated Legendre P_nm(x), n &le; 3. */
    private static double legendre(final int n, final int m, final double x) {
        final double c = Math.sqrt(Math.max(0.0, 1.0 - x * x)); // cos(phi)
        switch (n) {
            case 2:
                switch (m) {
                    case 0: return 0.5 * (3.0 * x * x - 1.0);
                    case 1: return 3.0 * x * c;
                    case 2: return 3.0 * c * c;
                    default: return 0.0;
                }
            case 3:
                switch (m) {
                    case 0: return 0.5 * (5.0 * x * x * x - 3.0 * x);
                    case 1: return 1.5 * (5.0 * x * x - 1.0) * c;
                    case 2: return 15.0 * x * c * c;
                    case 3: return 15.0 * c * c * c;
                    default: return 0.0;
                }
            default:
                return 0.0;
        }
    }

    @Override
    public String getName() {
        return "SolidTides";
    }
}
