package org.ursc.trajectory.orekit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.linear.RealMatrix;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.attitudes.FrameAlignedProvider;
import org.orekit.estimation.leastsquares.BatchLSEstimator;
import org.orekit.estimation.measurements.EstimatedMeasurement;
import org.orekit.estimation.measurements.EstimatedMeasurementBase;
import org.orekit.estimation.measurements.ObservableSatellite;
import org.orekit.estimation.measurements.ObservedMeasurement;
import org.orekit.estimation.measurements.QuadraticClockModel;
import org.orekit.estimation.measurements.gnss.AmbiguityCache;
import org.orekit.estimation.measurements.gnss.AmbiguityDriver;
import org.orekit.estimation.measurements.gnss.OneWayGNSSPhase;
import org.orekit.estimation.measurements.gnss.OneWayGNSSRange;
import org.orekit.estimation.measurements.modifiers.OnBoardAntennaOneWayGNSSPhaseModifier;
import org.orekit.estimation.measurements.modifiers.OnBoardAntennaOneWayGNSSRangeModifier;
import org.orekit.estimation.measurements.modifiers.ShapiroOneWayGNSSPhaseModifier;
import org.orekit.estimation.measurements.modifiers.ShapiroOneWayGNSSRangeModifier;
import org.orekit.orbits.Orbit;
import org.orekit.propagation.Propagator;
import org.orekit.propagation.conversion.NumericalPropagatorBuilder;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.ParameterDriver;
import org.orekit.utils.ParameterDriversList;
import org.ursc.trajectory.gnss.RinexObsReader;

/**
 * Orbit determination from <b>undifferenced ionosphere-free GPS code and carrier phase</b> with
 * Orekit's {@link BatchLSEstimator}, the only orbit-determination method of the program.
 *
 * <h2>Measurement model (Orekit)</h2>
 * Every GPS satellite in view gives, per epoch, an {@link OneWayGNSSRange} (ionosphere-free code
 * P_IF) and an {@link OneWayGNSSPhase} (ionosphere-free phase L_IF, in metres, wavelength 1 m).
 * Orekit models the light time in the inertial frame (which includes the Sagnac effect), the GPS
 * clock (a {@link QuadraticClockModel} from the selected product, relativistic term included)
 * and the receiver clock (the {@link ObservableSatellite} clock-offset driver).
 * Modifiers: {@link ShapiroOneWayGNSSRangeModifier} / {@link ShapiroOneWayGNSSPhaseModifier}
 * (relativistic path delay) and {@link OnBoardAntennaOneWayGNSSRangeModifier} /
 * {@link OnBoardAntennaOneWayGNSSPhaseModifier} (receiver antenna offset from the centre of mass,
 * rotated by the propagated attitude).
 *
 * <h2>Estimated parameters</h2>
 * The initial orbit (and optionally Cd/Cr) through the {@link NumericalPropagatorBuilder}; one
 * receiver clock offset per epoch (a time span of the clock driver per epoch); one float
 * ambiguity per continuous phase arc (time spans of the {@link AmbiguityDriver} of each GPS
 * satellite, from the {@link AmbiguityCache}).
 *
 * <h2>Data editing</h2>
 * Phase arcs are split at loss-of-lock flags, gaps, Melbourne-Wubbena jumps and gross
 * geometry-free jumps on the dense (10 s) data and at product changes; after each Orekit solve,
 * steps in the phase residuals of an arc (slips invisible to both combinations) split its
 * ambiguity span and outliers are removed; a final solve re-weights code and phase with their
 * post-fit scatter so the Orekit covariance is realistic.
 */
public final class OrekitCarrierPhaseOd {

    private static final String RECEIVER = "LEO";

    /** Tunables. */
    public static final class Settings {
        public double rateSeconds = 30.0;
        public double sigmaCode = 1.0;
        public double sigmaPhase = 0.01;
        public double maxGapSeconds = 60.0;
        public double gfSlipMeters = 1.0;
        public double mwSlipCycles = 2.5;
        public double minArcSeconds = 900.0;
        public int minCodePerEpoch = 4;
        public double outlierSigma = 4.0;
        public int maxIterations = 12;
        public int cleaningRounds = 3;
        public boolean shapiro = true;
        /** Antenna phase centre minus centre of mass, body frame (m). */
        public Vector3D antennaBody = Vector3D.ZERO;
        /**
         * GPS satellite antennas (ANTEX): ionosphere-free phase-centre offsets and variations for
         * centre-of-mass (precise) orbits, and the satellites' yaw attitude; null = not modelled.
         */
        public GpsAntennas gpsAntennas;
        /** Apply carrier-phase wind-up (needs {@link #gpsAntennas} for the GPS attitude). */
        public boolean windUp = true;
    }

    /** GPS orbit/clock source for an epoch, given the satellites in view. */
    public interface SourceSelector {
        OrekitGpsSource select(org.ursc.trajectory.time.AbsoluteDate t, Collection<Integer> prns);
    }

    /** Outcome. */
    public static final class Result {
        public Orbit orbit;
        /** Estimated propagation parameters (e.g. Cd, Cr) by driver name. */
        public final Map<String, Double> propagationParameters = new LinkedHashMap<>();
        /** Covariance of [orbit (Cartesian, inertial), propagation parameters], physical units. */
        public double[][] covariance;
        public boolean converged;
        public int iterations;
        public int epochs;
        public int codeObservations;
        public int phaseObservations;
        public int arcs;
        public int slipsPreprocessing;
        public int slipsFromResiduals;
        public int rejected;
        public double codeRms = Double.NaN;
        public double phaseRms = Double.NaN;
        public double sigmaCode;
        public double sigmaPhase;
        /** A-posteriori variance factor of the final (re-weighted) solve; about 1 when weights are right. */
        public double varianceFactor = Double.NaN;
        public final List<org.ursc.trajectory.time.AbsoluteDate> epochDates = new ArrayList<>();
        public final List<Double> epochPhaseRms = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
    }

    /** One usable code/phase pair on the estimator grid. */
    private static final class Obs {
        final int epoch;
        final int prn;
        final double code;
        final double phase;
        Arc arc;
        boolean codeOk = true;
        boolean phaseOk = true;
        OneWayGNSSRange range;
        OneWayGNSSPhase carrier;

        Obs(final int epoch, final int prn, final double code, final double phase) {
            this.epoch = epoch;
            this.prn = prn;
            this.code = code;
            this.phase = phase;
        }
    }

    /** A continuous phase arc of one satellite. */
    private static final class Arc {
        private static int counter;
        final int prn;
        final int id;
        final List<Obs> obs = new ArrayList<>();

        Arc(final int prn) {
            this.prn = prn;
            synchronized (Arc.class) {
                this.id = counter++;
            }
        }

        AbsoluteDate start(final List<AbsoluteDate> dates) {
            return dates.get(obs.get(0).epoch);
        }
    }

    private final NumericalPropagatorBuilder builder;
    private final Settings s;
    private final OrekitSession session;
    private final AttitudeProvider gpsAttitude;
    private final List<AbsoluteDate> dates = new ArrayList<>();
    private final List<OrekitGpsSource> sourceAt = new ArrayList<>();
    private final List<Obs> obs = new ArrayList<>();
    private final List<Arc> arcs = new ArrayList<>();
    private int slipsPre;
    /** Receiver clock of each epoch: one Orekit observable satellite (same propagator) per epoch. */
    private final Map<Integer, ObservableSatellite> receiverAt = new HashMap<>();
    /** Float ambiguity driver of each phase arc. */
    private final Map<Arc, AmbiguityDriver> ambiguityOf = new IdentityHashMap<>();

    /**
     * Ambiguity cache keyed by emitter only (the emitter name carries the arc), so that all
     * measurements of one arc share one driver whatever epoch-receiver they belong to.
     */
    private static final class ArcAmbiguityCache extends AmbiguityCache {
        private final Map<String, AmbiguityDriver> drivers = new HashMap<>();

        @Override
        public AmbiguityDriver getAmbiguity(final String emitter, final String receiver, final double wavelength) {
            return drivers.computeIfAbsent(emitter, e -> new AmbiguityDriver(e, RECEIVER, wavelength));
        }
    }

    /**
     * @param builder  propagator builder seeded with the initial orbit; its selected propagation
     *                 drivers (Cd, Cr) are estimated
     * @param epochs   observation epochs (dense, e.g. 10 s) covering the arc
     * @param start    first epoch used
     * @param cutoff   last epoch used
     * @param selector GPS product per epoch
     */
    public OrekitCarrierPhaseOd(final NumericalPropagatorBuilder builder, final List<RinexObsReader.Epoch> epochs,
                                final org.ursc.trajectory.time.AbsoluteDate start,
                                final org.ursc.trajectory.time.AbsoluteDate cutoff,
                                final SourceSelector selector, final Settings settings, final OrekitSession session) {
        this.builder = builder;
        this.s = settings;
        this.session = session;
        this.gpsAttitude = new FrameAlignedProvider(session.gcrf);
        preprocess(epochs, start, cutoff, selector);
    }

    // ---- preprocessing: arcs and cycle slips on the dense data -------------------------------

    private static final class Track {
        Arc arc;
        org.ursc.trajectory.time.AbsoluteDate lastT;
        double lastGf = Double.NaN;
        double mwSum;
        int mwN;
        OrekitGpsSource source;
        String l1Code;
    }

    private void preprocess(final List<RinexObsReader.Epoch> epochs, final org.ursc.trajectory.time.AbsoluteDate start,
                            final org.ursc.trajectory.time.AbsoluteDate cutoff, final SourceSelector selector) {
        final Map<Integer, Track> tracks = new HashMap<>();
        final List<Arc> all = new ArrayList<>();
        final Map<Arc, double[]> span = new IdentityHashMap<>();
        for (final RinexObsReader.Epoch e : epochs) {
            final org.ursc.trajectory.time.AbsoluteDate t = e.getDate();
            if (t.durationFrom(start) < -1.0e-3 || t.durationFrom(cutoff) > 1.0e-3) {
                continue;
            }
            final List<Integer> prns = new ArrayList<>();
            for (final RinexObsReader.SatRecord r : e.getSatellites()) {
                if (!Double.isNaN(r.ionoFreePseudorange())) {
                    prns.add(r.getPrn());
                }
            }
            final OrekitGpsSource src = prns.isEmpty() ? null : selector.select(t, prns);
            if (src == null) {
                continue;
            }
            final double off = t.durationFrom(start);
            final long k = Math.round(off / s.rateSeconds);
            final boolean onGrid = Math.abs(off - k * s.rateSeconds) < 1.0e-3;
            final AbsoluteDate od = OrekitDates.toOrekit(t);
            int epochIdx = -1;
            if (onGrid) {
                epochIdx = dates.size();
                dates.add(od);
                sourceAt.add(src);
            }
            for (final RinexObsReader.SatRecord r : e.getSatellites()) {
                final int prn = r.getPrn();
                final double code = r.ionoFreePseudorange();
                if (Double.isNaN(code) || !src.covers(prn, od)) {
                    continue;
                }
                final double phase = r.ionoFreePhase();
                final Track tr = tracks.computeIfAbsent(prn, p -> new Track());
                Arc arc = null;
                if (!Double.isNaN(phase)) {
                    final double gf = r.geometryFree();
                    final double mw = r.melbourneWubbenaCycles();
                    final boolean restart = tr.arc == null || tr.lastT == null
                            || t.durationFrom(tr.lastT) > s.maxGapSeconds || tr.source != src
                            || !r.phaseL1Code().equals(tr.l1Code);
                    final boolean slip = !restart && (r.lossOfLock()
                            || !Double.isNaN(tr.lastGf) && Math.abs(gf - tr.lastGf) > s.gfSlipMeters
                            || tr.mwN >= 3 && Math.abs(mw - tr.mwSum / tr.mwN) > s.mwSlipCycles);
                    if (restart || slip) {
                        if (slip) {
                            slipsPre++;
                        }
                        tr.arc = new Arc(prn);
                        all.add(tr.arc);
                        span.put(tr.arc, new double[] {off, off});
                        tr.mwSum = 0.0;
                        tr.mwN = 0;
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
                        o.arc = arc;
                    } else {
                        o.phaseOk = false;
                    }
                    obs.add(o);
                }
            }
        }
        for (final Arc a : all) {
            final double[] sp = span.get(a);
            if (sp[1] - sp[0] >= s.minArcSeconds && a.obs.size() >= 3) {
                arcs.add(a);
            } else {
                for (final Obs o : a.obs) {
                    o.phaseOk = false;
                    o.arc = null;
                }
            }
        }
    }

    // ---- Orekit measurements --------------------------------------------------------------------

    private static String gpsName(final int prn) {
        return String.format(java.util.Locale.US, "G%02d", prn);
    }

    /** Epochs that keep enough code observations; others are dropped (no clock span for them). */
    private boolean[] usableEpochs() {
        final int[] n = new int[dates.size()];
        for (final Obs o : obs) {
            if (o.codeOk) {
                n[o.epoch]++;
            }
        }
        final boolean[] ok = new boolean[dates.size()];
        for (int e = 0; e < ok.length; e++) {
            ok[e] = n[e] >= s.minCodePerEpoch;
        }
        return ok;
    }

    private QuadraticClockModel remoteClock(final OrekitGpsSource src, final int prn, final AbsoluteDate t) {
        final double c0 = src.clock(prn, t);
        final double drift = src.clock(prn, t.shiftedBy(0.5)) - src.clock(prn, t.shiftedBy(-0.5));
        return new QuadraticClockModel(t, c0, Double.isNaN(drift) ? 0.0 : drift, 0.0);
    }

    /**
     * Build the measurement set and the receiver-clock / ambiguity spans for the current edit
     * state. Clock and ambiguity drivers are created afresh so that each span carries data;
     * their values are initialised from the previous solution or from the data.
     */
    private List<ObservedMeasurement<?>> measurements(final Map<AbsoluteDate, Double> clocks,
                                                      final Map<Arc, Double> ambiguities) {
        final ArcAmbiguityCache cache = new ArcAmbiguityCache();
        receiverAt.clear();
        ambiguityOf.clear();
        final boolean[] use = usableEpochs();
        // Orekit 13 attributes the partials of a measurement to the first time span of a spanned
        // driver, so per-epoch clocks and per-arc ambiguities are separate drivers instead of spans
        for (int e = 0; e < dates.size(); e++) {
            if (!use[e]) {
                continue;
            }
            final ObservableSatellite sat = new ObservableSatellite(0, String.format(java.util.Locale.US, "%s-%04d", RECEIVER, e));
            final ParameterDriver clk = sat.getClockOffsetDriver();
            clk.setScale(1.0e-9);
            clk.setSelected(true);
            final Double v = clocks.get(dates.get(e));
            clk.setValue(v == null ? 0.0 : v);
            receiverAt.put(e, sat);
        }
        final ShapiroOneWayGNSSRangeModifier shapiroRange = new ShapiroOneWayGNSSRangeModifier(Constants.EIGEN5C_EARTH_MU);
        final ShapiroOneWayGNSSPhaseModifier shapiroPhase = new ShapiroOneWayGNSSPhaseModifier(Constants.EIGEN5C_EARTH_MU);
        // antenna phase centres: LEO receiver offset (body frame) and, for centre-of-mass (precise)
        // GPS orbits, the satellite's ionosphere-free ANTEX pattern in its yaw-steering attitude
        final org.orekit.gnss.antenna.FrequencyPattern rcvPattern = new org.orekit.gnss.antenna.FrequencyPattern(
                s.antennaBody, org.orekit.gnss.antenna.FrequencyPattern.ZERO_CORRECTION.getPhaseCenterVariationFunction());
        final Map<String, OnBoardAntennaOneWayGNSSRangeModifier> rangeAnt = new HashMap<>();
        final Map<String, OnBoardAntennaOneWayGNSSPhaseModifier> phaseAnt = new HashMap<>();
        final Map<Arc, PhaseWindUpModifier> windUp = new IdentityHashMap<>();
        final java.util.Set<Arc> usedArcs = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (final Arc a : arcs) {
            if (usedPhase(a, use) >= 3) {
                usedArcs.add(a);
            }
        }
        final List<ObservedMeasurement<?>> out = new ArrayList<>();
        for (final Obs o : obs) {
            o.range = null;
            o.carrier = null;
            if (!use[o.epoch]) {
                continue;
            }
            final ObservableSatellite receiver = receiverAt.get(o.epoch);
            final AbsoluteDate t = dates.get(o.epoch);
            final OrekitGpsSource src = sourceAt.get(o.epoch);
            final QuadraticClockModel rc = remoteClock(src, o.prn, t);
            final boolean com = s.gpsAntennas != null && src.centreOfMassOrbits();
            final org.orekit.gnss.antenna.FrequencyPattern txPattern = com ? s.gpsAntennas.ionosphereFreePattern(o.prn, t) : null;
            final AttitudeProvider txAttitude = s.gpsAntennas == null ? null : s.gpsAntennas.attitude(o.prn, t);
            final String key = o.prn + (txPattern != null ? "c" : "a") + System.identityHashCode(txAttitude);
            final boolean anyAntenna = s.antennaBody.getNorm() > 0.0 || txPattern != null;
            final org.orekit.gnss.antenna.FrequencyPattern tx = txPattern != null ? txPattern
                    : org.orekit.gnss.antenna.FrequencyPattern.ZERO_CORRECTION;
            final AttitudeProvider txAtt = txAttitude != null ? txAttitude : gpsAttitude;
            if (o.codeOk) {
                o.range = new OneWayGNSSRange(src.provider(o.prn), rc, t, o.code, s.sigmaCode, 1.0, receiver);
                if (s.shapiro) {
                    o.range.addModifier(shapiroRange);
                }
                if (anyAntenna) {
                    o.range.addModifier(rangeAnt.computeIfAbsent(key,
                            k -> new OnBoardAntennaOneWayGNSSRangeModifier(rcvPattern, tx, txAtt)));
                }
                out.add(o.range);
            }
            if (o.phaseOk && o.arc != null && usedArcs.contains(o.arc)) {
                o.carrier = new OneWayGNSSPhase(src.provider(o.prn), gpsName(o.prn) + "#" + o.arc.id, rc, t, o.phase,
                        1.0, s.sigmaPhase, 1.0, receiver, cache);
                if (s.shapiro) {
                    o.carrier.addModifier(shapiroPhase);
                }
                if (anyAntenna) {
                    o.carrier.addModifier(phaseAnt.computeIfAbsent(key,
                            k -> new OnBoardAntennaOneWayGNSSPhaseModifier(rcvPattern, tx, txAtt)));
                }
                if (s.windUp && txAttitude != null) {
                    o.carrier.addModifier(windUp.computeIfAbsent(o.arc, a -> new PhaseWindUpModifier(txAttitude)));
                }
                out.add(o.carrier);
                ambiguityOf.put(o.arc, o.carrier.getAmbiguityDriver());
            }
        }
        // ambiguity values: previous solve, else the mean phase-minus-code of the arc
        for (final Map.Entry<Arc, AmbiguityDriver> e : ambiguityOf.entrySet()) {
            final AmbiguityDriver d = e.getValue();
            d.setScale(0.1);
            d.setSelected(true);
            Double v = ambiguities.get(e.getKey());
            if (v == null) {
                double sum = 0.0;
                int n = 0;
                for (final Obs o : e.getKey().obs) {
                    if (o.carrier != null && o.range != null) {
                        sum += o.phase - o.code;
                        n++;
                    }
                }
                v = n == 0 ? 0.0 : sum / n;
            }
            d.setValue(v);
        }
        return out;
    }

    private AbsoluteDate firstPhaseDate(final Arc a) {
        for (final Obs o : a.obs) {
            if (o.carrier != null) {
                return dates.get(o.epoch);
            }
        }
        return a.start(dates);
    }

    private AbsoluteDate lastPhaseDate(final Arc a) {
        for (int i = a.obs.size() - 1; i >= 0; i--) {
            if (a.obs.get(i).carrier != null) {
                return dates.get(a.obs.get(i).epoch);
            }
        }
        return dates.get(a.obs.get(a.obs.size() - 1).epoch);
    }

    private int usedPhase(final Arc a, final boolean[] use) {
        int n = 0;
        for (final Obs o : a.obs) {
            if (o.phaseOk && use[o.epoch]) {
                n++;
            }
        }
        return n;
    }

    // ---- estimation -------------------------------------------------------------------------------

    private BatchLSEstimator estimator;

    private void solve(final List<ObservedMeasurement<?>> meas, final Result r) {
        estimator = new BatchLSEstimator(new SparseGaussNewtonOptimizer(), builder);
        for (final ObservedMeasurement<?> m : meas) {
            estimator.addMeasurement(m);
        }
        // converged when the normalised parameter corrections are below 1e-3 (Orekit's usual test)
        // or the weighted cost no longer changes (relative 1e-6): with Cd estimated on long arcs
        // Gauss-Newton creeps towards the minimum and the parameter test alone may never trigger
        estimator.setConvergenceChecker((iteration, previous, current) -> {
            final double maxStep = current.getPoint().subtract(previous.getPoint()).getLInfNorm();
            final double c0 = previous.getCost();
            final double c1 = current.getCost();
            return maxStep < 1.0e-3 || Math.abs(c0 - c1) <= 1.0e-6 * Math.max(c1, 1.0e-30);
        });
        estimator.setMaxIterations(Math.max(s.maxIterations, 25));
        estimator.setMaxEvaluations(4 * Math.max(s.maxIterations, 25));
        final Propagator[] p = estimator.estimate();
        r.iterations += estimator.getIterationsCount();
        r.converged = true;
        r.orbit = p[0].getInitialState().getOrbit();
        builder.resetOrbit(r.orbit);
    }

    private Map<ObservedMeasurement<?>, EstimatedMeasurement<?>> last() {
        return estimator.getLastEstimations();
    }

    private double residual(final ObservedMeasurement<?> m) {
        final EstimatedMeasurement<?> e = last().get(m);
        return e == null ? Double.NaN : m.getObservedValue()[0] - e.getEstimatedValue()[0];
    }

    private boolean active(final ObservedMeasurement<?> m) {
        final EstimatedMeasurement<?> e = last().get(m);
        return e != null && e.getStatus() == EstimatedMeasurementBase.Status.PROCESSED;
    }

    private double rms(final boolean code) {
        double ss = 0.0;
        int n = 0;
        for (final Obs o : obs) {
            final ObservedMeasurement<?> m = code ? o.range : o.carrier;
            if (m != null && active(m)) {
                final double v = residual(m);
                ss += v * v;
                n++;
            }
        }
        return n == 0 ? Double.NaN : Math.sqrt(ss / n);
    }

    /** Split arcs at steps in their phase residuals. */
    private int splitAtResidualSteps() {
        final double thr = Math.max(6.0 * rms(false), 0.04);
        int splits = 0;
        final List<Arc> added = new ArrayList<>();
        for (final Arc a : arcs) {
            final List<Obs> use = new ArrayList<>();
            for (final Obs o : a.obs) {
                if (o.carrier != null && active(o.carrier)) {
                    use.add(o);
                }
            }
            if (use.size() < 10) {
                continue;
            }
            final double[] v = new double[use.size()];
            for (int i = 0; i < v.length; i++) {
                v[i] = residual(use.get(i).carrier);
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
                final int pivot = use.get(bestAt).epoch;
                final Arc tail = new Arc(a.prn);
                final List<Obs> head = new ArrayList<>();
                for (final Obs o : a.obs) {
                    if (o.epoch >= pivot) {
                        tail.obs.add(o);
                        o.arc = tail;
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

    /** Remove observations beyond {@code outlierSigma} times the post-fit RMS. */
    private int rejectOutliers() {
        final double tc = s.outlierSigma * Math.max(rms(true), 0.25 * s.sigmaCode);
        final double tp = s.outlierSigma * Math.max(rms(false), 0.25 * s.sigmaPhase);
        int n = 0;
        for (final Obs o : obs) {
            if (o.range != null && Math.abs(residual(o.range)) > tc) {
                o.codeOk = false;
                n++;
            }
            if (o.carrier != null && Math.abs(residual(o.carrier)) > tp) {
                o.phaseOk = false;
                n++;
            }
        }
        return n;
    }

    /** Current clock and ambiguity values to carry into the next solve. */
    private void snapshot(final Map<AbsoluteDate, Double> clocks, final Map<Arc, Double> ambiguities) {
        clocks.clear();
        for (final Map.Entry<Integer, ObservableSatellite> e : receiverAt.entrySet()) {
            clocks.put(dates.get(e.getKey()), e.getValue().getClockOffsetDriver().getValue());
        }
        ambiguities.clear();
        for (final Map.Entry<Arc, AmbiguityDriver> e : ambiguityOf.entrySet()) {
            ambiguities.put(e.getKey(), e.getValue().getValue());
        }
    }

    /** Run the estimation with data editing. */
    public Result estimate() {
        final Result r = new Result();
        r.slipsPreprocessing = slipsPre;
        if (dates.size() < 10) {
            r.notes.add("too few epochs with GPS data on the estimator grid (" + dates.size() + ")");
            return r;
        }
        final double sigC0 = s.sigmaCode;
        final double sigP0 = s.sigmaPhase;
        final Map<AbsoluteDate, Double> clocks = new HashMap<>();
        final Map<Arc, Double> ambiguities = new IdentityHashMap<>();
        solve(measurements(clocks, ambiguities), r);
        for (int round = 0; round < s.cleaningRounds; round++) {
            final int splits = splitAtResidualSteps();
            final int rej = rejectOutliers();
            r.slipsFromResiduals += splits;
            r.rejected += rej;
            if (splits == 0 && rej == 0) {
                break;
            }
            snapshot(clocks, ambiguities);
            solve(measurements(clocks, ambiguities), r);
        }
        // variance-component step: weights from the post-fit scatter, then the final solve
        final double rc = rms(true);
        final double rp = rms(false);
        if (rc > 0 && rp > 0) {
            s.sigmaCode = rc;
            s.sigmaPhase = rp;
            snapshot(clocks, ambiguities);
            solve(measurements(clocks, ambiguities), r);
        }
        r.sigmaCode = s.sigmaCode;
        r.sigmaPhase = s.sigmaPhase;
        s.sigmaCode = sigC0;
        s.sigmaPhase = sigP0;
        r.codeRms = rms(true);
        r.phaseRms = rms(false);
        final boolean[] use = usableEpochs();
        final double[] ss = new double[dates.size()];
        final int[] cnt = new int[dates.size()];
        for (final Obs o : obs) {
            if (o.range != null && active(o.range)) {
                r.codeObservations++;
            }
            if (o.carrier != null && active(o.carrier)) {
                r.phaseObservations++;
                final double v = residual(o.carrier);
                ss[o.epoch] += v * v;
                cnt[o.epoch]++;
            }
        }
        for (int e = 0; e < dates.size(); e++) {
            if (use[e]) {
                r.epochs++;
                if (cnt[e] > 0) {
                    r.epochDates.add(OrekitDates.fromOrekit(dates.get(e)));
                    r.epochPhaseRms.add(Math.sqrt(ss[e] / cnt[e]));
                }
            }
        }
        for (final Arc a : arcs) {
            if (usedPhase(a, use) >= 3) {
                r.arcs++;
            }
        }
        final int nMeas = r.codeObservations + r.phaseObservations;
        final int nPar = estimator.getOptimum().getJacobian().getColumnDimension();
        final double wrms = estimator.getOptimum().getRMS();
        r.varianceFactor = nMeas > nPar ? wrms * wrms * nMeas / (nMeas - nPar) : Double.NaN;
        final ParameterDriversList prop = estimator.getPropagatorParametersDrivers(true);
        for (final ParameterDriversList.DelegatingDriver d : prop.getDrivers()) {
            r.propagationParameters.put(d.getName(), d.getValue());
        }
        try {
            final RealMatrix cov = estimator.getPhysicalCovariances(1.0e-12);
            final int n = 6 + prop.getNbParams();
            r.covariance = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    r.covariance[i][j] = cov.getEntry(i, j);
                }
            }
        } catch (final RuntimeException e) {
            r.notes.add("covariance unavailable: " + e.getMessage());
        }
        return r;
    }
}
