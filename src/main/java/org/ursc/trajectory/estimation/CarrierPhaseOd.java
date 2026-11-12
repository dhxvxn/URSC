package org.ursc.trajectory.estimation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.ursc.trajectory.attitude.AttitudeProvider;
import org.ursc.trajectory.attitude.BodyFrame;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.frames.Transform;
import org.ursc.trajectory.gnss.GpsConstants;
import org.ursc.trajectory.gnss.RinexObsReader;
import org.ursc.trajectory.gnss.products.GnssOrbitClockSource;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SampledPropagator;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.sampling.EphemerisCollector;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Batch orbit determination from <b>undifferenced ionosphere-free GPS code and carrier phase</b>,
 * the measurement-level counterpart of {@link BatchLeastSquares} (which fits single-point
 * position fixes).
 *
 * <h2>Observation model</h2>
 * For a GPS satellite {@code s} seen at reception epoch {@code t} (GPS time):
 * <pre>
 *   P_IF = rho + c dt_r(t) - c dt_s + d_shapiro + e_P
 *   L_IF = rho + c dt_r(t) - c dt_s + d_shapiro + B_arc + e_L
 * </pre>
 * where {@code rho} is the geometric range from the LEO <i>antenna</i> (centre of mass plus the
 * body-frame antenna offset rotated by the attitude) to the GPS satellite at transmit time
 * (light-time iteration, Sagnac rotation), {@code dt_s} the GPS clock (including the relativistic
 * term, from broadcast or precise products), {@code c dt_r} one receiver-clock parameter per epoch,
 * and {@code B_arc} one float ambiguity per continuous phase arc. First-order ionosphere is removed
 * by the dual-frequency combination; there is no troposphere at LEO altitude.
 *
 * <h2>Estimation</h2>
 * Global parameters (initial position/velocity and optionally Cd/Cr, exactly as for
 * {@link BatchLeastSquares}) are solved jointly with all clocks and ambiguities by Gauss-Newton on
 * the full normal equations. Orbit partials come from finite-difference propagation, so the
 * estimator works with any force model. The orbit-sensitivity matrix is computed once at the
 * starting point (the SPP-based solution) and refreshed only after a large correction.
 *
 * <h2>Data cleaning</h2>
 * Arcs are split at data gaps, loss-of-lock flags, geometry-free and Melbourne-Wubbena jumps
 * (evaluated on the full-rate data), and changes of GPS product; after each solve, steps in the
 * phase residuals (slips invisible to both combinations, e.g. equal L1/L2 slips) split arcs, and
 * outliers beyond {@code outlierSigma} a-posteriori sigmas are rejected. A final variance-component
 * step re-weights code and phase by their post-fit scatter so the formal covariance is realistic.
 *
 * <p>Not modelled (documented limitations): GPS satellite antenna phase-centre offsets and
 * variations (needs an ANTEX file; mostly absorbed by ambiguities and clocks), LEO antenna
 * phase-centre variations, and carrier-phase wind-up (its common part is absorbed by the clocks).</p>
 */
public final class CarrierPhaseOd {

    private static final double C = GpsConstants.SPEED_OF_LIGHT;
    private static final double WE = GpsConstants.EARTH_ROTATION_RATE;

    /** Tunable settings; defaults suit a dual-frequency LEO receiver at 1-10 s. */
    public static final class Settings {
        /** Estimator sampling (s); observations off this grid are used only for slip detection. */
        public double rateSeconds = 30.0;
        /** A-priori sigma of the ionosphere-free code (m). */
        public double sigmaCode = 1.0;
        /** A-priori sigma of the ionosphere-free phase (m). */
        public double sigmaPhase = 0.01;
        /** A data gap longer than this starts a new phase arc (s). */
        public double maxGapSeconds = 60.0;
        /**
         * Geometry-free jump between consecutive samples that flags a slip (m). At LEO speed the
         * ionosphere alone changes the geometry-free combination by 10-30 cm in 10 s at low
         * elevation, so this is only a guard against gross jumps; wide-lane slips are caught by the
         * Melbourne-Wubbena test and equal L1/L2 slips by the residual-step test.
         */
        public double gfSlipMeters = 1.0;
        /** Melbourne-Wubbena departure from the arc mean that flags a slip (wide-lane cycles). */
        public double mwSlipCycles = 2.5;
        /** Phase arcs shorter than this are not used (s). */
        public double minArcSeconds = 900.0;
        /** Epochs with fewer ionosphere-free code observations are dropped. */
        public int minCodePerEpoch = 3;
        /** Outlier rejection threshold in a-posteriori sigmas. */
        public double outlierSigma = 4.0;
        /** Maximum Gauss-Newton iterations per solve. */
        public int maxIterations = 10;
        /** Maximum data-cleaning rounds (slip splits / outlier removal). */
        public int cleaningRounds = 6;
        /** Apply the general-relativistic (Shapiro) path delay. */
        public boolean shapiro = true;
        /** GPS antenna offset from the centre of mass, body frame (m). */
        public Vector3D antennaBody = Vector3D.ZERO;
        /** Body axes for the antenna offset; ignored when the offset is zero. */
        public AttitudeProvider attitude;
    }

    /** Selects the GPS orbit/clock source for an epoch, given the satellites in view. */
    public interface SourceSelector {
        GnssOrbitClockSource select(AbsoluteDate t, Collection<Integer> prns);
    }

    /** Outcome of a carrier-phase orbit determination. */
    public static final class Result {
        public double[] params;
        public boolean converged;
        public int iterations;
        public int epochs;
        public int codeObservations;
        public int phaseObservations;
        public int arcs;
        public int slipsPreprocessing;
        public int slipsFromResiduals;
        public int rejectedCode;
        public int rejectedPhase;
        public double codeRms = Double.NaN;
        public double phaseRms = Double.NaN;
        public double sigmaCode;
        public double sigmaPhase;
        public double varianceFactor = Double.NaN;
        /** Formal covariance of the global parameters (parameter units squared), or null. */
        public double[][] covariance;
        /** Post-fit phase residual RMS per estimator epoch (m), with the epochs. */
        public final List<AbsoluteDate> epochDates = new ArrayList<>();
        public final List<Double> epochPhaseRms = new ArrayList<>();
        /** GPS product description -> number of estimator epochs it served. */
        public final Map<String, Integer> productUsage = new LinkedHashMap<>();
        public final List<String> notes = new ArrayList<>();
    }

    // ---- data ---------------------------------------------------------------------------------

    /** One usable observation on the estimator grid. */
    private static final class Obs {
        final int epoch;      // estimator-epoch index
        final int prn;
        final double code;    // P_IF (m) or NaN
        final double phase;   // L_IF (m) or NaN
        int arc;              // global arc id (index into arcs) or -1
        boolean codeOk = true;
        boolean phaseOk = true;
        // geometry at the current reference trajectory
        double model;         // rho + shapiro - c dts (m)
        Vector3D u;           // unit line of sight antenna -> GPS satellite (ITRF)

        Obs(final int epoch, final int prn, final double code, final double phase) {
            this.epoch = epoch;
            this.prn = prn;
            this.code = code;
            this.phase = phase;
        }
    }

    /** A continuous phase arc of one satellite. */
    private static final class Arc {
        final int prn;
        final List<Obs> obs = new ArrayList<>();

        Arc(final int prn) {
            this.prn = prn;
        }
    }

    private final BatchLeastSquares.PropagatorFactory factory;
    private final double[] scale;
    private final AbsoluteDate epoch0;
    private final Settings s;
    private final List<AbsoluteDate> dates = new ArrayList<>();       // estimator epochs
    private final List<Integer> gridIndex = new ArrayList<>();         // sample index on the rate grid
    private final List<GnssOrbitClockSource> sourceAt = new ArrayList<>();
    private final List<Transform> toItrf = new ArrayList<>();
    private final List<Obs> obs = new ArrayList<>();
    private final List<Arc> arcs = new ArrayList<>();
    private int slipsPre;

    /**
     * Prepare the measurements: select the GPS source per epoch, split phase arcs at cycle slips on
     * the full-rate data, and keep the epochs on the estimator grid.
     *
     * @param factory  builds a propagator seeded at {@code epoch0} from a global parameter vector
     * @param scale    finite-difference step and column scaling per global parameter
     * @param epochs   observation epochs in time order (any rate up to the estimator rate)
     * @param epoch0   epoch of the estimated state (start of the arc)
     * @param cutoff   last usable observation epoch
     * @param selector GPS orbit/clock source per epoch
     * @param settings tunables
     */
    public CarrierPhaseOd(final BatchLeastSquares.PropagatorFactory factory, final double[] scale,
                          final List<RinexObsReader.Epoch> epochs, final AbsoluteDate epoch0,
                          final AbsoluteDate cutoff, final SourceSelector selector, final Settings settings) {
        this.factory = factory;
        this.scale = scale.clone();
        this.epoch0 = epoch0;
        this.s = settings;
        preprocess(epochs, cutoff, selector);
    }

    // ---- preprocessing ------------------------------------------------------------------------

    /** Per-satellite running state for slip detection on the full-rate series. */
    private static final class Track {
        Arc arc;
        AbsoluteDate lastT;
        double lastGf = Double.NaN;
        double mwSum;
        int mwN;
        GnssOrbitClockSource source;
        String l1Code;
        AbsoluteDate arcStart;
    }

    private void preprocess(final List<RinexObsReader.Epoch> epochs, final AbsoluteDate cutoff,
                            final SourceSelector selector) {
        final Map<Integer, Track> tracks = new HashMap<>();
        final Map<Arc, double[]> span = new HashMap<>(); // arc -> {startOffset, endOffset} (s)
        final List<Arc> allArcs = new ArrayList<>();
        for (final RinexObsReader.Epoch e : epochs) {
            final AbsoluteDate t = e.getDate();
            if (t.isBefore(epoch0) || t.isAfter(cutoff)) {
                continue;
            }
            final List<Integer> prns = new ArrayList<>();
            for (final RinexObsReader.SatRecord r : e.getSatellites()) {
                if (!Double.isNaN(r.ionoFreePseudorange())) {
                    prns.add(r.getPrn());
                }
            }
            final GnssOrbitClockSource src = prns.isEmpty() ? null : selector.select(t, prns);
            if (src == null) {
                continue;
            }
            final double off = t.durationFrom(epoch0);
            final long k = Math.round(off / s.rateSeconds);
            final boolean onGrid = Math.abs(off - k * s.rateSeconds) < 1.0e-3;
            int epochIdx = -1;
            if (onGrid) {
                epochIdx = dates.size();
                dates.add(t);
                gridIndex.add((int) k);
                sourceAt.add(src);
            }
            for (final RinexObsReader.SatRecord r : e.getSatellites()) {
                final int prn = r.getPrn();
                final double code = r.ionoFreePseudorange();
                if (Double.isNaN(code) || !src.covers(prn, t)) {
                    continue;
                }
                final double phase = r.ionoFreePhase();
                final Track tr = tracks.computeIfAbsent(prn, p -> new Track());
                Arc arc = null;
                if (!Double.isNaN(phase)) {
                    final double gf = r.geometryFree();
                    final double mw = r.melbourneWubbenaCycles();
                    final boolean restart = tr.arc == null
                            || tr.lastT == null || t.durationFrom(tr.lastT) > s.maxGapSeconds
                            || tr.source != src
                            || !r.phaseL1Code().equals(tr.l1Code);
                    final boolean slip = !restart && (r.lossOfLock()
                            || (!Double.isNaN(tr.lastGf) && Math.abs(gf - tr.lastGf) > s.gfSlipMeters)
                            || (tr.mwN >= 3 && Math.abs(mw - tr.mwSum / tr.mwN) > s.mwSlipCycles));
                    if (restart || slip) {
                        if (slip) {
                            slipsPre++;
                        }
                        tr.arc = new Arc(prn);
                        allArcs.add(tr.arc);
                        tr.mwSum = 0.0;
                        tr.mwN = 0;
                        tr.arcStart = t;
                        span.put(tr.arc, new double[] {off, off});
                    }
                    tr.lastGf = gf;
                    tr.mwSum += mw;
                    tr.mwN++;
                    tr.source = src;
                    tr.l1Code = r.phaseL1Code();
                    tr.lastT = t;
                    span.get(tr.arc)[1] = off;
                    arc = tr.arc;
                } else {
                    tr.arc = null;
                    tr.lastGf = Double.NaN;
                }
                if (onGrid) {
                    final Obs o = new Obs(epochIdx, prn, code, phase);
                    if (arc != null) {
                        arc.obs.add(o);
                    } else {
                        o.phaseOk = false;
                    }
                    obs.add(o);
                }
            }
        }
        // keep arcs that are long enough; others contribute code only
        for (final Arc a : allArcs) {
            final double[] sp = span.get(a);
            final boolean keep = sp[1] - sp[0] >= s.minArcSeconds && a.obs.size() >= 3;
            if (keep) {
                arcs.add(a);
            } else {
                for (final Obs o : a.obs) {
                    o.phaseOk = false;
                }
            }
        }
        for (final AbsoluteDate d : dates) {
            toItrf.add(FramesFactory.getTransform(FramesFactory.getGCRF(), FramesFactory.getITRF(), d));
        }
    }

    // ---- trajectory and geometry --------------------------------------------------------------

    /** Antenna positions (ITRF) at every estimator epoch for a global parameter vector. */
    private Vector3D[] antennaItrf(final double[] params) {
        final SampledPropagator prop = factory.build(params);
        final EphemerisCollector col = new EphemerisCollector();
        prop.setStepHandler(s.rateSeconds, col);
        final int last = gridIndex.get(gridIndex.size() - 1);
        prop.propagate(epoch0.shiftedBy(last * s.rateSeconds));
        final List<SpacecraftState> st = col.getStates();
        if (st.size() <= last) {
            throw new IllegalStateException("propagator produced " + st.size() + " samples, expected " + (last + 1));
        }
        final boolean offset = s.antennaBody.getNormSq() > 0.0 && s.attitude != null;
        final Vector3D[] out = new Vector3D[dates.size()];
        for (int i = 0; i < dates.size(); i++) {
            final SpacecraftState x = st.get(gridIndex.get(i));
            Vector3D r = x.getPosition();
            if (offset) {
                final BodyFrame b = s.attitude.attitude(x);
                r = r.add(s.antennaBody.getX(), b.x()).add(s.antennaBody.getY(), b.y())
                        .add(s.antennaBody.getZ(), b.z());
            }
            out[i] = toItrf.get(i).transformPosition(r);
        }
        return out;
    }

    /** Rotate a transmit-epoch ECEF position into the reception-epoch frame (Sagnac). */
    private static Vector3D rotateForTransit(final Vector3D p, final double tau) {
        final double th = WE * tau;
        final double cs = Math.cos(th);
        final double sn = Math.sin(th);
        return new Vector3D(cs * p.getX() + sn * p.getY(), -sn * p.getX() + cs * p.getY(), p.getZ());
    }

    /** Refresh every observation's model value and line of sight at the given antenna positions. */
    private void geometry(final Vector3D[] ant) {
        for (final Obs o : obs) {
            final AbsoluteDate t = dates.get(o.epoch);
            final GnssOrbitClockSource src = sourceAt.get(o.epoch);
            final Vector3D ra = ant[o.epoch];
            double tau = (Double.isNaN(o.code) ? 2.2e7 : o.code) / C;
            Vector3D rs = null;
            for (int it = 0; it < 3; it++) {
                rs = rotateForTransit(src.positionEcef(o.prn, t.shiftedBy(-tau)), tau);
                tau = rs.distance(ra) / C;
            }
            final double rho = rs.distance(ra);
            o.u = rs.subtract(ra).scalarMultiply(1.0 / rho);
            double model = rho - C * src.clockBias(o.prn, t.shiftedBy(-tau));
            if (s.shapiro) {
                final double a = rs.getNorm() + ra.getNorm();
                model += 2.0 * Constants.EARTH_MU / (C * C) * Math.log((a + rho) / (a - rho));
            }
            o.model = model;
        }
    }

    // ---- estimation ---------------------------------------------------------------------------

    /** Indices and values of one observation row in the full parameter vector. */
    private int nGlobal;
    private int[] clockIdx;      // per estimator epoch: parameter index or -1 (epoch unused)
    private int[] ambIdx;        // per arc: parameter index or -1 (arc unused)
    private int nUnknowns;

    /** Assign clock and ambiguity indices from the currently usable observations. */
    private void index() {
        clockIdx = new int[dates.size()];
        final int[] codeCount = new int[dates.size()];
        for (final Obs o : obs) {
            if (o.codeOk && !Double.isNaN(o.code)) {
                codeCount[o.epoch]++;
            }
        }
        int k = nGlobal;
        for (int e = 0; e < dates.size(); e++) {
            clockIdx[e] = codeCount[e] >= s.minCodePerEpoch ? k++ : -1;
        }
        ambIdx = new int[arcs.size()];
        for (int a = 0; a < arcs.size(); a++) {
            int n = 0;
            for (final Obs o : arcs.get(a).obs) {
                if (o.phaseOk && clockIdx[o.epoch] >= 0) {
                    n++;
                }
            }
            ambIdx[a] = n >= 3 ? k++ : -1;
            for (final Obs o : arcs.get(a).obs) {
                o.arc = a;
            }
        }
        nUnknowns = k;
    }

    private boolean useCode(final Obs o) {
        return o.codeOk && !Double.isNaN(o.code) && clockIdx[o.epoch] >= 0;
    }

    private boolean usePhase(final Obs o) {
        return o.phaseOk && !Double.isNaN(o.phase) && o.arc >= 0 && ambIdx[o.arc] >= 0
                && clockIdx[o.epoch] >= 0;
    }

    private double[] state;        // full unknown vector: globals, clocks, ambiguities
    private Vector3D[][] sens;      // [epoch][global j] antenna-position change per scaled step (ITRF)
    private Vector3D[] sensAt;      // antenna positions where sens was evaluated
    private double sigC;
    private double sigP;

    /** Finite-difference sensitivity of the antenna positions to every global parameter. */
    private void sensitivity(final double[] globals, final Vector3D[] nominal) {
        sens = new Vector3D[dates.size()][nGlobal];
        for (int j = 0; j < nGlobal; j++) {
            final double[] pert = globals.clone();
            pert[j] += scale[j];
            final Vector3D[] pj = antennaItrf(pert);
            for (int e = 0; e < dates.size(); e++) {
                sens[e][j] = pj[e].subtract(nominal[e]);
            }
        }
        sensAt = nominal;
    }

    private double[] globals() {
        final double[] g = new double[nGlobal];
        System.arraycopy(state, 0, g, 0, nGlobal);
        return g;
    }

    private double codeResidual(final Obs o) {
        return o.code - (o.model + state[clockIdx[o.epoch]]);
    }

    private double phaseResidual(final Obs o) {
        return o.phase - (o.model + state[clockIdx[o.epoch]] + state[ambIdx[o.arc]]);
    }

    /** Weighted sum of squared residuals. */
    private double cost() {
        double c = 0.0;
        for (final Obs o : obs) {
            if (useCode(o)) {
                final double v = codeResidual(o) / sigC;
                c += v * v;
            }
            if (usePhase(o)) {
                final double v = phaseResidual(o) / sigP;
                c += v * v;
            }
        }
        return c;
    }

    /** Initialise clocks from the code and ambiguities from the mean phase-minus-model. */
    private void initialiseLinear() {
        final double[] sum = new double[dates.size()];
        final int[] n = new int[dates.size()];
        for (final Obs o : obs) {
            if (useCode(o)) {
                sum[o.epoch] += o.code - o.model;
                n[o.epoch]++;
            }
        }
        for (int e = 0; e < dates.size(); e++) {
            if (clockIdx[e] >= 0) {
                state[clockIdx[e]] = sum[e] / n[e];
            }
        }
        for (int a = 0; a < arcs.size(); a++) {
            if (ambIdx[a] < 0) {
                continue;
            }
            double sa = 0.0;
            int na = 0;
            for (final Obs o : arcs.get(a).obs) {
                if (usePhase(o)) {
                    sa += o.phase - o.model - state[clockIdx[o.epoch]];
                    na++;
                }
            }
            state[ambIdx[a]] = sa / na;
        }
    }

    /**
     * One full solve: Gauss-Newton on globals + clocks + ambiguities until the global correction
     * is negligible. Returns the final normal matrix (for the covariance).
     */
    private double[][] solve(final Result r) {
        double[][] normal = null;
        Vector3D[] ant = antennaItrf(globals());
        geometry(ant);
        if (sens == null) {
            sensitivity(globals(), ant);
        }
        double cost = cost();
        for (int it = 0; it < s.maxIterations; it++) {
            r.iterations++;
            final int n = nUnknowns;
            final double[][] nm = new double[n][n];
            final double[] g = new double[n];
            final int[] idx = new int[nGlobal + 2];
            final double[] val = new double[nGlobal + 2];
            for (final Obs o : obs) {
                final boolean c = useCode(o);
                final boolean ph = usePhase(o);
                if (!c && !ph) {
                    continue;
                }
                // orbit part: d(model)/d(global_j) = -u . d(antenna)/d(global_j)
                final Vector3D[] sj = sens[o.epoch];
                for (int j = 0; j < nGlobal; j++) {
                    idx[j] = j;
                    val[j] = -o.u.dotProduct(sj[j]);
                }
                idx[nGlobal] = clockIdx[o.epoch];
                val[nGlobal] = 1.0;
                if (c) {
                    accumulate(nm, g, idx, val, nGlobal + 1, codeResidual(o), 1.0 / (sigC * sigC));
                }
                if (ph) {
                    idx[nGlobal + 1] = ambIdx[o.arc];
                    val[nGlobal + 1] = 1.0;
                    accumulate(nm, g, idx, val, nGlobal + 2, phaseResidual(o), 1.0 / (sigP * sigP));
                }
            }
            for (int a = 0; a < n; a++) {
                nm[a][a] += 1.0e-12 * nm[a][a] + 1.0e-20;
            }
            final double[][] chol = cholesky(nm);
            if (chol == null) {
                r.notes.add("normal matrix not positive definite; stopping");
                break;
            }
            normal = chol;
            final double[] dx = cholSolve(chol, g);
            // line search on the full step (the problem is linear in clocks/ambiguities)
            double step = 1.0;
            boolean improved = false;
            final double[] saved = state.clone();
            for (int bt = 0; bt < 5; bt++) {
                for (int k = 0; k < n; k++) {
                    state[k] = saved[k] + step * dx[k] * (k < nGlobal ? scale[k] : 1.0);
                }
                ant = antennaItrf(globals());
                geometry(ant);
                final double trial = cost();
                if (trial <= cost * (1.0 + 1.0e-12)) {
                    improved = true;
                    final double rel = (cost - trial) / Math.max(cost, 1.0e-30);
                    cost = trial;
                    double dPos = 0.0;
                    double dVel = 0.0;
                    for (int k = 0; k < Math.min(6, nGlobal); k++) {
                        final double d = step * dx[k] * scale[k];
                        if (k < 3) {
                            dPos = Math.max(dPos, Math.abs(d));
                        } else {
                            dVel = Math.max(dVel, Math.abs(d));
                        }
                    }
                    // refresh the orbit sensitivity only after a large correction
                    if (dPos > 100.0) {
                        sensitivity(globals(), ant);
                    }
                    if (dPos < 1.0e-3 && dVel < 1.0e-6 || rel < 1.0e-9) {
                        r.converged = true;
                    }
                    break;
                }
                step *= 0.5;
            }
            if (!improved) {
                System.arraycopy(saved, 0, state, 0, n);
                ant = antennaItrf(globals());
                geometry(ant);
                r.converged = true; // no further descent possible: at the minimum within tolerance
                break;
            }
            if (r.converged) {
                break;
            }
        }
        return normal;
    }

    private static void accumulate(final double[][] nm, final double[] g, final int[] idx, final double[] val,
                                   final int q, final double resid, final double w) {
        for (int a = 0; a < q; a++) {
            final int ia = idx[a];
            final double wa = w * val[a];
            g[ia] += wa * resid;
            for (int b = 0; b < q; b++) {
                nm[ia][idx[b]] += wa * val[b];
            }
        }
    }

    /** Split arcs at steps in the phase residuals (slips invisible to GF and MW). Returns the count. */
    private int splitAtResidualSteps() {
        final double sp = rms(false);
        final double thr = Math.max(6.0 * sp, 0.04);
        int splits = 0;
        final List<Arc> added = new ArrayList<>();
        for (final Arc a : arcs) {
            final List<Obs> use = new ArrayList<>();
            for (final Obs o : a.obs) {
                if (usePhase(o)) {
                    use.add(o);
                }
            }
            if (use.size() < 10) {
                continue;
            }
            final double[] v = new double[use.size()];
            for (int i = 0; i < v.length; i++) {
                v[i] = phaseResidual(use.get(i));
            }
            int bestAt = -1;
            double best = 0.0;
            final int w = 5;
            for (int i = w; i <= v.length - w; i++) {
                double before = 0.0;
                double after = 0.0;
                for (int k = 0; k < w; k++) {
                    before += v[i - 1 - k];
                    after += v[i + k];
                }
                final double step = Math.abs(after - before) / w;
                if (step > best) {
                    best = step;
                    bestAt = i;
                }
            }
            if (bestAt > 0 && best > thr) {
                final Obs pivot = use.get(bestAt);
                final Arc tail = new Arc(a.prn);
                final List<Obs> head = new ArrayList<>();
                for (final Obs o : a.obs) {
                    if (o.epoch >= pivot.epoch) {
                        tail.obs.add(o);
                    } else {
                        head.add(o);
                    }
                }
                a.obs.clear();
                a.obs.addAll(head);
                added.add(tail);
                splits++;
            }
        }
        arcs.addAll(added);
        return splits;
    }

    /** Reject observations beyond {@code outlierSigma} a-posteriori sigmas. Returns {code, phase} counts. */
    private int[] rejectOutliers() {
        final double tc = s.outlierSigma * Math.max(rms(true), 0.25 * s.sigmaCode);
        final double tp = s.outlierSigma * Math.max(rms(false), 0.25 * s.sigmaPhase);
        int rc = 0;
        int rp = 0;
        for (final Obs o : obs) {
            if (useCode(o) && Math.abs(codeResidual(o)) > tc) {
                o.codeOk = false;
                rc++;
            }
            if (usePhase(o) && Math.abs(phaseResidual(o)) > tp) {
                o.phaseOk = false;
                rp++;
            }
        }
        return new int[] {rc, rp};
    }

    /** Post-fit RMS of the code ({@code true}) or phase residuals. */
    private double rms(final boolean code) {
        double ss = 0.0;
        int n = 0;
        for (final Obs o : obs) {
            if (code ? useCode(o) : usePhase(o)) {
                final double v = code ? codeResidual(o) : phaseResidual(o);
                ss += v * v;
                n++;
            }
        }
        return n == 0 ? Double.NaN : Math.sqrt(ss / n);
    }

    /** Carry the clocks/ambiguities across a re-indexing (new arcs start from their mean). */
    private void reindex(final double[] globals) {
        final Map<Integer, Double> clocks = new TreeMap<>();
        if (clockIdx != null && state != null) {
            for (int e = 0; e < dates.size(); e++) {
                if (clockIdx[e] >= 0) {
                    clocks.put(e, state[clockIdx[e]]);
                }
            }
        }
        index();
        state = new double[nUnknowns];
        System.arraycopy(globals, 0, state, 0, nGlobal);
        initialiseLinear();
        for (final Map.Entry<Integer, Double> c : clocks.entrySet()) {
            if (clockIdx[c.getKey()] >= 0) {
                state[clockIdx[c.getKey()]] = c.getValue();
            }
        }
    }

    /**
     * Estimate the global parameters (and all clocks and ambiguities) from carrier phase and code.
     *
     * @param guess starting global parameters, e.g. the SPP-based batch solution
     * @return the solution and its diagnostics
     */
    public Result estimate(final double[] guess) {
        final Result r = new Result();
        nGlobal = guess.length;
        r.slipsPreprocessing = slipsPre;
        if (dates.size() < 10) {
            r.notes.add("too few estimator epochs (" + dates.size() + ")");
            r.params = guess.clone();
            return r;
        }
        sigC = s.sigmaCode;
        sigP = s.sigmaPhase;
        state = null;
        reindex(guess);
        double[][] chol = solve(r);
        for (int round = 0; round < s.cleaningRounds; round++) {
            final int splits = splitAtResidualSteps();
            r.slipsFromResiduals += splits;
            final int[] rej = rejectOutliers();
            r.rejectedCode += rej[0];
            r.rejectedPhase += rej[1];
            if (splits == 0 && rej[0] == 0 && rej[1] == 0) {
                break;
            }
            reindex(globals());
            r.converged = false;
            chol = solve(r);
        }
        // variance-component step: weight each observation type by its post-fit scatter
        final double rc = rms(true);
        final double rp = rms(false);
        if (rc > 0 && rp > 0) {
            sigC = rc;
            sigP = rp;
            r.converged = false;
            chol = solve(r);
        }
        r.params = globals();
        r.codeRms = rms(true);
        r.phaseRms = rms(false);
        r.sigmaCode = sigC;
        r.sigmaPhase = sigP;
        int nc = 0;
        int np = 0;
        for (final Obs o : obs) {
            if (useCode(o)) {
                nc++;
            }
            if (usePhase(o)) {
                np++;
            }
        }
        r.codeObservations = nc;
        r.phaseObservations = np;
        int ne = 0;
        for (int e = 0; e < dates.size(); e++) {
            if (clockIdx[e] >= 0) {
                ne++;
            }
        }
        r.epochs = ne;
        int na = 0;
        for (int a = 0; a < arcs.size(); a++) {
            if (ambIdx[a] >= 0) {
                na++;
            }
        }
        r.arcs = na;
        final int dof = Math.max(1, nc + np - nUnknowns);
        r.varianceFactor = cost() / dof;
        if (chol != null) {
            r.covariance = new double[nGlobal][nGlobal];
            for (int j = 0; j < nGlobal; j++) {
                final double[] ej = new double[nUnknowns];
                ej[j] = 1.0;
                final double[] col = cholSolve(chol, ej);
                for (int i = 0; i < nGlobal; i++) {
                    r.covariance[i][j] = col[i] * scale[i] * scale[j];
                }
            }
        }
        // per-epoch phase residual RMS for display, and product usage
        final double[] ss = new double[dates.size()];
        final int[] cnt = new int[dates.size()];
        for (final Obs o : obs) {
            if (usePhase(o)) {
                final double v = phaseResidual(o);
                ss[o.epoch] += v * v;
                cnt[o.epoch]++;
            }
        }
        for (int e = 0; e < dates.size(); e++) {
            if (clockIdx[e] >= 0) {
                r.productUsage.merge(sourceAt.get(e).description(), 1, Integer::sum);
                if (cnt[e] > 0) {
                    r.epochDates.add(dates.get(e));
                    r.epochPhaseRms.add(Math.sqrt(ss[e] / cnt[e]));
                }
            }
        }
        return r;
    }

    // ---- dense Cholesky -----------------------------------------------------------------------

    /** Lower Cholesky factor of a symmetric positive-definite matrix, or null if not SPD. */
    static double[][] cholesky(final double[][] a) {
        final int n = a.length;
        final double[][] l = new double[n][];
        for (int i = 0; i < n; i++) {
            l[i] = new double[i + 1];
            for (int j = 0; j <= i; j++) {
                double sum = a[i][j];
                final double[] li = l[i];
                final double[] lj = l[j];
                for (int k = 0; k < j; k++) {
                    sum -= li[k] * lj[k];
                }
                if (i == j) {
                    if (!(sum > 0.0)) {
                        return null;
                    }
                    li[i] = Math.sqrt(sum);
                } else {
                    li[j] = sum / lj[j];
                }
            }
        }
        return l;
    }

    /** Solve (L L^T) x = b. */
    static double[] cholSolve(final double[][] l, final double[] b) {
        final int n = b.length;
        final double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            double s = b[i];
            for (int k = 0; k < i; k++) {
                s -= l[i][k] * y[k];
            }
            y[i] = s / l[i][i];
        }
        final double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double s = y[i];
            for (int k = i + 1; k < n; k++) {
                s -= l[k][i] * x[k];
            }
            x[i] = s / l[i][i];
        }
        return x;
    }
}
