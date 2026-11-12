package org.ursc.trajectory.orekit;

import java.util.ArrayList;
import java.util.List;

import org.hipparchus.linear.MatrixUtils;
import org.hipparchus.linear.RealMatrix;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.forces.ForceModel;
import org.orekit.propagation.MatricesHarvester;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.ParameterDriver;
import org.ursc.trajectory.pipeline.ProgressListener;
import org.ursc.trajectory.pipeline.RunConfig;
import org.ursc.trajectory.pipeline.RunResult;

/**
 * Orekit numerical forecast: propagate an estimated state to the output start, then sample it at
 * the output step to the end, with the formal covariance propagated through Orekit's
 * state-transition and parameter Jacobians ({@link MatricesHarvester}):
 * {@code P(t) = [Phi Psi] P0 [Phi Psi]^T}.
 */
public final class OrekitForecast {

    private OrekitForecast() {
    }

    /**
     * @param s0          initial state (GCRF)
     * @param p0          covariance of [position, velocity, estimated parameters] at s0, or null
     * @param params      the force-model drivers matching the trailing rows of {@code p0}
     * @param forcesAt    builds the force models (fresh instances, estimated coefficients) for each leg
     * @param outputStart first output epoch
     * @param end         last output epoch
     */
    public static List<RunResult.State> run(final SpacecraftState s0, final double[][] p0, final List<String> params,
                                            final ForcesFactory forcesAt, final AttitudeProvider attitude,
                                            final AbsoluteDate outputStart, final AbsoluteDate end,
                                            final RunConfig cfg, final ProgressListener pl) {
        final boolean cov = p0 != null;
        // leg 1: estimated epoch -> output start
        SpacecraftState start = s0;
        RealMatrix pStart = cov ? MatrixUtils.createRealMatrix(p0) : null;
        if (Math.abs(outputStart.durationFrom(s0.getDate())) > 1.0e-6) {
            final List<ForceModel> f1 = forcesAt.build();
            final NumericalPropagator leg1 = OrekitDynamics.propagator(s0, cfg, f1, attitude);
            final MatricesHarvester h1 = cov ? harvester(leg1, f1, params) : null;
            start = leg1.propagate(outputStart);
            if (cov) {
                pStart = transport(pStart, h1, start, params);
            }
        }
        // leg 2: output start -> end, sampled
        final List<ForceModel> f2 = forcesAt.build();
        final NumericalPropagator leg2 = OrekitDynamics.propagator(start, cfg, f2, attitude);
        final MatricesHarvester h2 = cov ? harvester(leg2, f2, params) : null;
        final List<RunResult.State> out = new ArrayList<>();
        final RealMatrix pAtStart = pStart;
        final double span = end.durationFrom(start.getDate());
        final int every = Math.max(1, cfg.forecast.covarianceEveryNSteps);
        final int[] k = {0};
        leg2.setStepHandler(cfg.forecast.outputStepSeconds, st -> {
            double[][] c = null;
            if (cov && k[0] % every == 0) {
                final RealMatrix full = transport(pAtStart, h2, st, params);
                c = full.getSubMatrix(0, 5, 0, 5).getData();
            }
            k[0]++;
            out.add(new RunResult.State(OrekitDates.fromOrekit(st.getDate()),
                    OrekitDates.fromOrekit(st.getPosition()), OrekitDates.fromOrekit(st.getPVCoordinates().getVelocity()), c));
            if (span > 0) {
                pl.stage("Forecast propagation", 0.45 + 0.5 * st.getDate().durationFrom(outputStart) / span);
            }
        });
        leg2.propagate(end);
        return out;
    }

    /** Builds fresh force models (with the estimated coefficients) for one propagation leg. */
    public interface ForcesFactory {
        List<ForceModel> build();
    }

    private static MatricesHarvester harvester(final NumericalPropagator p, final List<ForceModel> forces,
                                               final List<String> params) {
        for (final ForceModel fm : forces) {
            for (final ParameterDriver d : fm.getParametersDrivers()) {
                if (params.contains(d.getName())) {
                    d.setSelected(true);
                }
            }
        }
        return p.setupMatricesComputation("stm", null, null);
    }

    /** Full covariance of [state, params] transported from the harvester's reference to {@code st}. */
    private static RealMatrix transport(final RealMatrix p, final MatricesHarvester h, final SpacecraftState st,
                                        final List<String> params) {
        final int np = params.size();
        final RealMatrix phi = h.getStateTransitionMatrix(st);
        final RealMatrix m = MatrixUtils.createRealIdentityMatrix(6 + np);
        m.setSubMatrix(phi.getData(), 0, 0);
        if (np > 0) {
            final RealMatrix psi = h.getParametersJacobian(st);
            final List<String> cols = h.getJacobiansColumnsNames();
            for (int j = 0; j < np; j++) {
                final int c = cols.indexOf(params.get(j));
                if (c >= 0 && psi != null) {
                    for (int i = 0; i < 6; i++) {
                        m.setEntry(i, 6 + j, psi.getEntry(i, c));
                    }
                }
            }
        }
        return m.multiply(p).multiplyTransposed(m);
    }
}
