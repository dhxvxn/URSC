package org.ursc.trajectory.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.linear.CholeskyDecomposer;
import org.hipparchus.optim.nonlinear.vector.leastsquares.GaussNewtonOptimizer;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.estimation.iod.IodGibbs;
import org.orekit.estimation.leastsquares.BatchLSEstimator;
import org.orekit.estimation.measurements.EstimatedMeasurement;
import org.orekit.estimation.measurements.ObservableSatellite;
import org.orekit.estimation.measurements.ObservedMeasurement;
import org.orekit.estimation.measurements.Position;
import org.orekit.forces.ForceModel;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.conversion.NumericalPropagatorBuilder;
import org.orekit.utils.ParameterDriver;
import org.ursc.trajectory.gnss.RinexObsReader;
import org.ursc.trajectory.gnss.SppEngine;
import org.ursc.trajectory.gnss.SppSolution;
import org.ursc.trajectory.gnss.products.GnssOrbitClockSource;
import org.ursc.trajectory.gnss.products.GnssProductResolver;
import org.ursc.trajectory.gnss.products.ProductLoader;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.orekit.OrekitCarrierPhaseOd;
import org.ursc.trajectory.orekit.OrekitDates;
import org.ursc.trajectory.orekit.OrekitDynamics;
import org.ursc.trajectory.orekit.OrekitForecast;
import org.ursc.trajectory.orekit.OrekitGpsProducts;
import org.ursc.trajectory.orekit.OrekitGpsSource;
import org.ursc.trajectory.orekit.OrekitSession;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * GNSS orbit determination and forecast on the Orekit engine:
 * GNSSRD + GPS products (IGS Final > Rapid > Ultra-rapid > broadcast) + UTC cutoff
 * -> code positions (SPP) for the initial orbit (Orekit Gibbs + Orekit batch fit)
 * -> Orekit batch least-squares on undifferenced ionosphere-free code + carrier phase
 * -> Orekit numerical forecast with formal covariance. Truth (POEORB) is never read here.
 */
public final class OdForecastPipeline {

    private OdForecastPipeline() {
    }

    /** Run from the files named in {@code cfg}. Never throws; problems end up in the result. */
    public static RunResult run(final RunConfig cfg, final ProgressListener pl) {
        final long t0 = System.nanoTime();
        final RunResult res = new RunResult();
        res.workflow = cfg.workflow.name();
        res.runName = cfg.runName;
        final AbsoluteDate cutoff;
        try {
            cutoff = validate(cfg, res);
            if (cutoff == null) {
                res.status = RunResult.Status.FAILED_VALIDATION;
                return res;
            }
            final List<Path> obsFiles = InputFiles.expand(cfg.inputs.gnssrd);
            final List<Path> prodFiles = InputFiles.expand(cfg.inputs.gpsProducts);
            hash(obsFiles, res);
            hash(prodFiles, res);
            final List<String> craft = ObsLoader.spacecraft(obsFiles);
            if (craft.size() > 1) {
                res.errors.add("The GNSS observation files belong to different spacecraft (" + String.join(", ", craft)
                        + "). Select the files of one spacecraft.");
                res.status = RunResult.Status.FAILED_VALIDATION;
                return res;
            }
            final AbsoluteDate arcStart = cutoff.shiftedBy(-cfg.od.arcHours * 3600.0);
            try (Environment env = Environment.setUp(cfg, cutoff);
                 OrekitSession session = openSession(cfg, env, arcStart.shiftedBy(-86400.0),
                         cutoff.shiftedBy(cfg.forecast.spanDays * 86400.0 + 86400.0))) {
                pl.stage("Reading GNSS observations (Orekit RINEX parser)", 0.02);
                final ObsLoader.Loaded obs = ObsLoader.loadWithOrekit(obsFiles, Math.min(cfg.od.samplingSeconds, 10.0),
                        arcStart.shiftedBy(-60.0), cutoff, session);
                res.warnings.addAll(obs.problems);
                pl.stage("Loading GPS orbit/clock products (Orekit SP3 / clock / navigation parsers)", 0.06);
                final ProductLoader.Loaded prod = OrekitGpsProducts.load(prodFiles, Map.of(), session);
                res.warnings.addAll(prod.problems);
                if (prod.sources.isEmpty()) {
                    res.errors.add("No usable GPS orbit/clock product among the supplied files "
                            + "(need IGS SP3[+CLK] Final/Rapid/Ultra-rapid or RINEX broadcast navigation)");
                    res.status = RunResult.Status.FAILED_VALIDATION;
                    return res;
                }
                res.objectName = cfg.spacecraft.objectName.isBlank() ? obs.objectName : cfg.spacecraft.objectName;
                res.objectId = cfg.spacecraft.objectId.isBlank() ? obs.objectId : cfg.spacecraft.objectId;
                run(cfg, cutoff, obs.epochs, prod.sources, env, session, pl, res, obs.antennaDelta);
            }
        } catch (final Exception e) {
            res.errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
            res.status = RunResult.Status.ERROR;
        } finally {
            res.elapsedSeconds = (System.nanoTime() - t0) / 1e9;
        }
        return res;
    }

    /** Orekit data context of a run (EOP held at the cutoff by {@link Environment}). */
    static OrekitSession openSession(final RunConfig cfg, final Environment env, final AbsoluteDate from,
                                     final AbsoluteDate to) throws IOException {
        final Path gravity = cfg.inputs.gravityFile == null || cfg.inputs.gravityFile.isBlank()
                ? null : Paths.get(cfg.inputs.gravityFile);
        return OrekitSession.open(env.eop, from, to, gravity, Environment.de440(cfg));
    }

    /** Checks the three mandatory inputs; returns the parsed cutoff or null. */
    public static AbsoluteDate validate(final RunConfig cfg, final RunResult res) {
        if (cfg.inputs.gnssrd.isEmpty()) {
            res.errors.add("Mandatory input missing: GNSSRD observation file(s) or folder(s)");
        }
        if (cfg.inputs.gpsProducts.isEmpty()) {
            res.errors.add("Mandatory input missing: a GPS orbit/clock product "
                    + "(IGS Final, Rapid, Ultra-rapid or broadcast navigation)");
        }
        AbsoluteDate cutoff = null;
        if (cfg.cutoffUtc == null || cfg.cutoffUtc.isBlank()) {
            res.errors.add("Mandatory input missing: UTC cutoff");
        } else {
            try {
                cutoff = TimeUtil.parseUtc(cfg.cutoffUtc);
                res.cutoffUtc = TimeUtil.formatUtc(cutoff);
            } catch (final IllegalArgumentException e) {
                res.errors.add(e.getMessage());
            }
        }
        if (cfg.od.arcHours <= 0 || cfg.od.samplingSeconds <= 0 || cfg.od.phaseRateSeconds <= 0
                || cfg.forecast.spanDays < 0 || cfg.forecast.outputStepSeconds <= 0) {
            res.errors.add("Arc length, sampling, forecast span and output step must be positive");
        }
        if (Environment.de440(cfg) == null) {
            res.errors.add("The JPL DE440 ephemeris (lnxp1990.440) was not found; select it as the DE440 file "
                    + "(it is in data/ephemeris/ of the project)");
        }
        return res.errors.isEmpty() ? cutoff : null;
    }

    /**
     * Core run on in-memory observations and Orekit-backed GPS sources (used directly by tests).
     *
     * @param antennaDelta GPS antenna position from the observation header, body frame (m), or null
     */
    static void run(final RunConfig cfg, final AbsoluteDate cutoff, final List<RinexObsReader.Epoch> epochs,
                    final List<GnssOrbitClockSource> sources, final Environment env, final OrekitSession session,
                    final ProgressListener pl, final RunResult res, final double[] antennaDelta) {
        res.cutoffUtc = TimeUtil.formatUtc(cutoff);
        if (res.workflow.isEmpty()) {
            res.workflow = RunConfig.Workflow.OD_FORECAST.name();
        }
        res.defaultsUsed.addAll(env.defaultsUsed);
        res.warnings.addAll(env.warnings);
        final boolean forecastMode = cfg.products.mode == RunConfig.ProductMode.FORECAST;
        res.label = forecastMode ? "forecast (cutoff-safe)" : "reconstruction (retrospective, not cutoff-safe)";

        // ---- product resolution
        final GnssProductResolver resolver = resolver(cfg, cutoff, sources);
        res.productRejected.addAll(resolver.rejected());
        if (resolver.eligible().isEmpty()) {
            res.errors.add("No GPS product is eligible" + (forecastMode ? " for this cutoff" : ""));
            res.status = RunResult.Status.FAILED_VALIDATION;
            return;
        }

        // ---- code positions over the arc: only to seed the orbit
        final AbsoluteDate arcStart = cutoff.shiftedBy(-cfg.od.arcHours * 3600.0);
        final List<RinexObsReader.Epoch> arc = new ArrayList<>();
        AbsoluteDate lastKept = null;
        for (final RinexObsReader.Epoch e : epochs) {
            if (!e.getDate().isBefore(arcStart) && !e.getDate().isAfter(cutoff)
                    && (lastKept == null || e.getDate().durationFrom(lastKept) >= cfg.od.samplingSeconds - 1.0e-6)) {
                arc.add(e);
                lastKept = e.getDate();
            }
        }
        if (arc.isEmpty()) {
            res.errors.add("No GNSS observation epochs between " + TimeUtil.formatUtc(arcStart) + " and the cutoff "
                    + res.cutoffUtc + " UTC; check the GNSSRD coverage");
            res.status = RunResult.Status.FAILED_SPP;
            return;
        }
        final List<Position> fixes = new ArrayList<>();
        final ObservableSatellite seedSat = new ObservableSatellite(0);
        if (!positions(cfg, arc, resolver, session, seedSat, fixes, pl, res)) {
            return;
        }
        res.productUsage.putAll(resolver.usage());
        res.productFallbacks.putAll(resolver.fallbacks());
        pl.log("SPP: " + fixes.size() + " accepted fixes of " + arc.size() + " epochs (initial orbit only)");
        if (fixes.size() < 6) {
            res.errors.add("Only " + fixes.size() + " usable SPP fixes in the arc (need at least 6 to seed the orbit)");
            res.status = RunResult.Status.FAILED_SPP;
            return;
        }

        // ---- initial orbit: Orekit Gibbs on three fixes, then an Orekit batch fit to the fixes
        pl.stage("Initial orbit (Orekit Gibbs + batch fit to code positions)", 0.22);
        final double mass = cfg.spacecraft.massKg;
        final AttitudeProvider attitude = OrekitDynamics.attitude(cfg, env, session);
        final List<String> fw = new ArrayList<>();
        final Orbit gibbs = gibbs(fixes, session);
        final NumericalPropagatorBuilder seedBuilder = OrekitDynamics.builder(gibbs, mass, cfg,
                OrekitDynamics.forces(cfg, env, session, cfg.spacecraft.cd, cfg.spacecraft.cr, fw), attitude);
        final BatchLSEstimator seedFit = new BatchLSEstimator(
                new GaussNewtonOptimizer(new CholeskyDecomposer(1.0e-15, 1.0e-15), true), seedBuilder);
        for (final Position f : fixes) {
            seedFit.addMeasurement(f);
        }
        seedFit.setParametersConvergenceThreshold(1.0e-3);
        seedFit.setMaxIterations(cfg.od.maxIterations);
        seedFit.setMaxEvaluations(4 * cfg.od.maxIterations);
        final Orbit seedOrbit;
        try {
            seedOrbit = seedFit.estimate()[0].getInitialState().getOrbit();
        } catch (final RuntimeException e) {
            res.errors.add("Initial orbit fit to the code positions did not converge (" + e.getMessage()
                    + "); no forecast issued");
            res.status = RunResult.Status.FAILED_OD;
            return;
        }
        final RunResult.Od od = new RunResult.Od();
        final double seedRms = seedResiduals(seedFit, fixes, od);
        od.sppPostFitRms = seedRms;
        od.weighted = cfg.od.covarianceWeighting;
        od.varianceFactor = seedVarianceFactor(seedFit, fixes);
        od.epochUtc = TimeUtil.formatUtc(OrekitDates.fromOrekit(seedOrbit.getDate()));
        res.flags.addAll(ManeuverCheck.assess(od, cfg.od.pseudorangeSigma));
        for (final String f : res.flags) {
            pl.log("WARNING: " + f);
        }
        pl.log(String.format(java.util.Locale.US, "Initial orbit: fit to %d code positions, RMS %.2f m",
                fixes.size(), seedRms));

        // ---- orbit determination: Orekit batch least squares on code + carrier phase
        pl.stage("Orbit determination: Orekit batch least squares on code + carrier phase", 0.27);
        boolean estCd = cfg.od.estimateCd && cfg.forces.drag.enabled;
        boolean estCr = cfg.od.estimateCr && cfg.forces.srp.enabled;
        final List<ForceModel> odForces = OrekitDynamics.forces(cfg, env, session, cfg.spacecraft.cd, cfg.spacecraft.cr, fw);
        final NumericalPropagatorBuilder builder = OrekitDynamics.builder(seedOrbit, mass, cfg, odForces, attitude);
        for (final ParameterDriver d : OrekitDynamics.coefficientDrivers(odForces, estCd, estCr)) {
            d.setSelected(true);
        }
        final OrekitCarrierPhaseOd.Settings cs = new OrekitCarrierPhaseOd.Settings();
        cs.rateSeconds = cfg.od.phaseRateSeconds;
        cs.sigmaCode = cfg.od.codeSigma;
        cs.sigmaPhase = cfg.od.phaseSigma;
        cs.maxIterations = cfg.od.maxIterations;
        if (cfg.od.antennaOffset) {
            if (antennaDelta != null && antennaDelta.length == 3) {
                cs.antennaBody = new org.hipparchus.geometry.euclidean.threed.Vector3D(
                        antennaDelta[0] - cfg.spacecraft.comBodyX, antennaDelta[1] - cfg.spacecraft.comBodyY,
                        antennaDelta[2] - cfg.spacecraft.comBodyZ);
                od.antennaOffsetApplied = cs.antennaBody.getNorm() > 0.0;
            } else {
                res.warnings.add("The GNSSRD header has no ANTENNA: DELTA; the antenna offset from the centre of "
                        + "mass is not applied");
            }
        }
        cs.windUp = cfg.od.phaseWindUp;
        if (cfg.od.gpsAntennaPhaseCentre || cfg.od.phaseWindUp) {
            cs.gpsAntennas = org.ursc.trajectory.orekit.GpsAntennas.load(Environment.antex(cfg), session);
            if (cs.gpsAntennas == null) {
                res.warnings.add("No ANTEX antenna file found (data/antenna/igs20_gps.atx); GPS antenna phase "
                        + "centres and phase wind-up are not modelled");
            }
        }
        if (!cfg.od.gpsAntennaPhaseCentre && cs.gpsAntennas != null) {
            // keep the GPS attitude for wind-up, drop the phase-centre offsets
            cs.gpsAntennas = cs.gpsAntennas.withoutPhaseCentres();
        }
        final GnssProductResolver cpResolver = resolver(cfg, cutoff, sources);
        OrekitCarrierPhaseOd.Result cp;
        try {
            cp = new OrekitCarrierPhaseOd(builder, epochs, OrekitDates.fromOrekit(seedOrbit.getDate()), cutoff,
                    (t, prns) -> {
                        final GnssProductResolver.Selection sel = cpResolver.select(t, prns);
                        return sel == null || !(sel.source instanceof OrekitGpsSource) ? null : (OrekitGpsSource) sel.source;
                    }, cs, session).estimate();
        } catch (final RuntimeException e) {
            if (!(estCd || estCr)) {
                res.errors.add("Carrier-phase orbit determination failed (" + e.getClass().getSimpleName() + ": "
                        + e.getMessage() + "); no forecast issued");
                res.status = RunResult.Status.FAILED_OD;
                res.od = od;
                return;
            }
            // with the drag/radiation coefficient free the fit did not converge (e.g. a storm day):
            // fit again with the coefficients fixed rather than issuing no forecast
            res.warnings.add("Carrier-phase orbit determination did not converge with the "
                    + (estCd ? "drag" : "radiation") + " coefficient estimated (" + e.getMessage()
                    + "); repeated with the coefficients fixed at their configured values");
            pl.log("OD: refitting with Cd/Cr fixed");
            estCd = false;
            estCr = false;
            final NumericalPropagatorBuilder fixed = OrekitDynamics.builder(seedOrbit, mass, cfg,
                    OrekitDynamics.forces(cfg, env, session, cfg.spacecraft.cd, cfg.spacecraft.cr, fw), attitude);
            final GnssProductResolver retryResolver = resolver(cfg, cutoff, sources);
            try {
                cp = new OrekitCarrierPhaseOd(fixed, epochs, OrekitDates.fromOrekit(seedOrbit.getDate()), cutoff,
                        (t, prns) -> {
                            final GnssProductResolver.Selection sel = retryResolver.select(t, prns);
                            return sel == null || !(sel.source instanceof OrekitGpsSource) ? null : (OrekitGpsSource) sel.source;
                        }, cs, session).estimate();
            } catch (final RuntimeException e2) {
                res.errors.add("Carrier-phase orbit determination failed (" + e2.getClass().getSimpleName() + ": "
                        + e2.getMessage() + "); no forecast issued");
                res.status = RunResult.Status.FAILED_OD;
                res.od = od;
                return;
            }
        }
        fillOd(od, cp, seedOrbit, estCd, estCr, cfg);
        res.od = od;
        for (final String w : new java.util.LinkedHashSet<>(fw)) {
            if (!res.warnings.contains(w)) {
                res.warnings.add(w);
            }
        }
        res.warnings.addAll(cp.notes);
        pl.log(String.format(java.util.Locale.US,
                "OD (code + carrier phase): phase RMS %.3f m, code RMS %.2f m, %d phase + %d code obs, %d arcs, "
                        + "%d slips, %d rejected, change from initial orbit %.2f m",
                cp.phaseRms, cp.codeRms, cp.phaseObservations, cp.codeObservations, cp.arcs,
                cp.slipsPreprocessing + cp.slipsFromResiduals, cp.rejected, od.changeFromSpp));
        if (cp.orbit == null || cp.phaseObservations < 100 || !(cp.phaseRms < 1.0)) {
            od.converged = false;
            res.errors.add(String.format(java.util.Locale.US,
                    "Carrier-phase orbit determination is not usable (%d phase observations, phase RMS %.3f m%s); "
                            + "no forecast issued (never replaced by a truth state)",
                    cp.phaseObservations, cp.phaseRms, cp.notes.isEmpty() ? "" : ", " + String.join("; ", cp.notes)));
            res.status = RunResult.Status.FAILED_OD;
            return;
        }

        // ---- forecast from the cutoff, with formal covariance (Orekit STM)
        pl.stage("Forecast propagation (Orekit)", 0.45);
        final double cr = coefficient(cp, "reflection", cfg.spacecraft.cr, estCr);
        final double cdUsed = coefficient(cp, "drag", cfg.spacecraft.cd, estCd);
        final List<String> params = new ArrayList<>(cp.propagationParameters.keySet());
        final double[][] p0 = cfg.forecast.covariance ? cp.covariance : null;
        if (cfg.forecast.covariance && p0 == null) {
            res.warnings.add("OD covariance unavailable; OEM written without covariance");
        }
        final SpacecraftState s0 = new SpacecraftState(cp.orbit).withMass(mass);
        final org.orekit.time.AbsoluteDate oCut = OrekitDates.toOrekit(cutoff);
        final org.orekit.time.AbsoluteDate oEnd = oCut.shiftedBy(cfg.forecast.spanDays * 86400.0);
        final List<RunResult.State> states = OrekitForecast.run(s0, p0, params,
                () -> OrekitDynamics.forces(cfg, env, session, cdUsed, cr, new ArrayList<>()), attitude, oCut, oEnd, cfg, pl);
        OrekitForecast.toOutputFrame(states, cfg.forecast.frame, session);
        res.states.addAll(states);
        pl.stage("Done", 1.0);
        res.status = RunResult.Status.SUCCESS;
    }

    private static double coefficient(final OrekitCarrierPhaseOd.Result cp, final String key, final double fallback,
                                      final boolean estimated) {
        if (!estimated) {
            return fallback;
        }
        for (final Map.Entry<String, Double> e : cp.propagationParameters.entrySet()) {
            if (e.getKey().toLowerCase(java.util.Locale.US).contains(key)) {
                return e.getValue();
            }
        }
        return fallback;
    }

    private static GnssProductResolver resolver(final RunConfig cfg, final AbsoluteDate cutoff,
                                                final List<GnssOrbitClockSource> sources) {
        final boolean forecastMode = cfg.products.mode == RunConfig.ProductMode.FORECAST;
        final GnssOrbitClockSource.ProductClass forced = cfg.products.tier == RunConfig.ProductTier.AUTO ? null
                : GnssOrbitClockSource.ProductClass.valueOf(cfg.products.tier.name());
        return new GnssProductResolver(sources,
                forecastMode ? GnssProductResolver.Mode.FORECAST : GnssProductResolver.Mode.RECONSTRUCTION,
                cutoff, GnssProductResolver.Eligibility.valueOf(cfg.products.eligibility.name()), forced,
                cfg.od.minSatellites);
    }

    /** Single-point positions (code only) on the SPP sampling grid, as Orekit Position measurements. */
    private static boolean positions(final RunConfig cfg, final List<RinexObsReader.Epoch> arc,
                                     final GnssProductResolver resolver, final OrekitSession session,
                                     final ObservableSatellite sat, final List<Position> fixes,
                                     final ProgressListener pl, final RunResult res) {
        final SppEngine engine = new SppEngine();
        Vector3D guess = Vector3D.ZERO;
        for (int k = 0; k < arc.size(); k++) {
            if (pl.cancelled()) {
                res.status = RunResult.Status.CANCELLED;
                return false;
            }
            pl.stage("Code positions for the initial orbit", 0.08 + 0.12 * k / arc.size());
            final RinexObsReader.Epoch e = arc.get(k);
            final RunResult.SppRow row = new RunResult.SppRow();
            row.utc = TimeUtil.formatUtc(e.getDate());
            res.spp.add(row);
            final List<Integer> prns = new ArrayList<>();
            for (final RinexObsReader.SatRecord s : e.getSatellites()) {
                if (!Double.isNaN(s.ionoFreePseudorange())) {
                    prns.add(s.getPrn());
                }
            }
            row.satellites = prns.size();
            final GnssProductResolver.Selection sel = resolver.select(e.getDate(), prns);
            if (sel == null) {
                row.reason = "no eligible GPS product covers enough satellites";
                continue;
            }
            row.product = sel.source.productClass().label;
            row.predictedOrbit = sel.source.isPredicted(e.getDate());
            final SppSolution sol;
            try {
                sol = engine.solveEpoch(e, sel.source, guess);
            } catch (final RuntimeException ex) {
                row.reason = "positioning failed: " + ex.getMessage();
                guess = Vector3D.ZERO;
                continue;
            }
            if (sol == null) {
                row.reason = "fewer than 4 usable satellites";
                continue;
            }
            final double radius = sol.getPosition().getNorm();
            if (!(radius > 6.2e6 && radius < 5.0e7)) {
                row.reason = "implausible position (" + Math.round(radius / 1000) + " km from Earth's centre); "
                        + "check that the observations and GPS products match";
                guess = Vector3D.ZERO;
                continue;
            }
            row.pdop = sol.getPdop();
            row.rms = sol.getPostFitRms();
            row.x = sol.getPosition().getX();
            row.y = sol.getPosition().getY();
            row.z = sol.getPosition().getZ();
            row.clock = sol.getClockBias();
            if (!sol.isConverged()) {
                row.reason = "SPP did not converge";
                continue;
            }
            guess = sol.getPosition();
            if (!(sol.getPdop() <= cfg.od.maxPdop)) {
                row.reason = String.format(java.util.Locale.US, "PDOP %.1f above limit %.1f", sol.getPdop(), cfg.od.maxPdop);
                continue;
            }
            if (sol.getPostFitRms() > cfg.od.maxSppRms) {
                row.reason = String.format(java.util.Locale.US, "residual RMS %.1f m above limit %.1f m",
                        sol.getPostFitRms(), cfg.od.maxSppRms);
                continue;
            }
            // ITRF -> GCRF with Orekit
            final org.orekit.time.AbsoluteDate t = OrekitDates.toOrekit(e.getDate());
            final org.orekit.frames.StaticTransform tr = session.itrf.getStaticTransformTo(session.gcrf, t);
            final org.hipparchus.geometry.euclidean.threed.Vector3D pG =
                    tr.transformPosition(OrekitDates.toOrekit(sol.getPosition()));
            final Position fix;
            if (cfg.od.covarianceWeighting) {
                final double[][] c = sol.covariance(cfg.od.pseudorangeSigma);
                if (c == null) {
                    row.reason = "singular geometry";
                    continue;
                }
                fix = new Position(t, pG, rotateCovariance(c, tr.getRotation()), 1.0, sat);
            } else {
                fix = new Position(t, pG, cfg.od.pseudorangeSigma, 1.0, sat);
            }
            row.accepted = true;
            fixes.add(fix);
        }
        return true;
    }

    private static double[][] rotateCovariance(final double[][] c, final Rotation r) {
        final double[][] m = r.getMatrix();   // maps ITRF components to GCRF components
        final double[][] out = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double v = 0.0;
                for (int a = 0; a < 3; a++) {
                    for (int b = 0; b < 3; b++) {
                        v += m[i][a] * c[a][b] * m[j][b];
                    }
                }
                out[i][j] = v;
            }
        }
        return out;
    }

    /** Orekit Gibbs initial orbit determination on three fixes about 10 minutes apart. */
    static Orbit gibbs(final List<Position> f, final OrekitSession s) {
        final org.orekit.time.AbsoluteDate t0 = f.get(0).getDate();
        int i1 = -1;
        int i2 = -1;
        for (int i = 1; i < f.size(); i++) {
            if (i1 < 0 && f.get(i).getDate().durationFrom(t0) >= 600.0) {
                i1 = i;
            } else if (i1 >= 0 && f.get(i).getDate().durationFrom(f.get(i1).getDate()) >= 600.0) {
                i2 = i;
                break;
            }
        }
        if (i1 < 0 || i2 < 0) {
            i1 = 1;
            i2 = 2;
        }
        final Orbit mid = new IodGibbs(org.orekit.utils.Constants.EIGEN5C_EARTH_MU).estimate(s.gcrf,
                f.get(0), f.get(i1), f.get(i2));
        // move to the first fix (Keplerian) so the estimated epoch is the start of the arc
        return new CartesianOrbit(mid.shiftedBy(t0.durationFrom(mid.getDate())));
    }

    /** Fill the seed-fit residual series of {@code od}; returns the 3-D RMS (m). */
    private static double seedResiduals(final BatchLSEstimator fit, final List<Position> fixes, final RunResult.Od od) {
        final Map<ObservedMeasurement<?>, EstimatedMeasurement<?>> est = fit.getLastEstimations();
        double ss = 0.0;
        for (final Position p : fixes) {
            final double[] o = p.getObservedValue();
            final double[] e = est.get(p).getEstimatedValue();
            final double n = Math.sqrt(sq(o[0] - e[0]) + sq(o[1] - e[1]) + sq(o[2] - e[2]));
            od.residualUtc.add(TimeUtil.formatUtc(OrekitDates.fromOrekit(p.getDate())));
            od.residualNorm.add(n);
            ss += n * n;
        }
        return Math.sqrt(ss / fixes.size());
    }

    private static double seedVarianceFactor(final BatchLSEstimator fit, final List<Position> fixes) {
        final double rms = fit.getOptimum().getRMS();
        return rms * rms;
    }

    private static void fillOd(final RunResult.Od od, final OrekitCarrierPhaseOd.Result cp, final Orbit seed,
                               final boolean estCd, final boolean estCr, final RunConfig cfg) {
        od.method = "CARRIER_PHASE";
        od.converged = cp.converged && cp.orbit != null;
        od.iterations = cp.iterations;
        od.observations = cp.codeObservations + cp.phaseObservations;
        od.preFitRms = od.sppPostFitRms;
        od.postFitRms = cp.phaseRms;
        od.codeRms = cp.codeRms;
        od.phaseRms = cp.phaseRms;
        od.codeObservations = cp.codeObservations;
        od.phaseObservations = cp.phaseObservations;
        od.phaseArcs = cp.arcs;
        od.cycleSlips = cp.slipsPreprocessing + cp.slipsFromResiduals;
        od.rejectedObservations = cp.rejected;
        od.varianceFactor = cp.varianceFactor;
        od.weighted = true;
        if (cp.orbit == null) {
            return;
        }
        od.epochUtc = TimeUtil.formatUtc(OrekitDates.fromOrekit(cp.orbit.getDate()));
        final org.orekit.utils.PVCoordinates pv = cp.orbit.getPVCoordinates();
        od.changeFromSpp = pv.getPosition().distance(seed.getPVCoordinates().getPosition());
        final List<String> names = new ArrayList<>(List.of("x", "y", "z", "vx", "vy", "vz"));
        final List<Double> values = new ArrayList<>(List.of(pv.getPosition().getX(), pv.getPosition().getY(),
                pv.getPosition().getZ(), pv.getVelocity().getX(), pv.getVelocity().getY(), pv.getVelocity().getZ()));
        for (final Map.Entry<String, Double> e : cp.propagationParameters.entrySet()) {
            final String n = e.getKey().toLowerCase(java.util.Locale.US);
            names.add(n.contains("drag") ? "Cd" : n.contains("reflection") || n.contains("radiation") ? "Cr" : e.getKey());
            values.add(e.getValue());
        }
        od.parameterNames = names.toArray(new String[0]);
        od.parameters = values.stream().mapToDouble(Double::doubleValue).toArray();
        od.covariance = cp.covariance;
        for (int i = 0; i < cp.epochDates.size(); i++) {
            od.phaseResidualUtc.add(TimeUtil.formatUtc(cp.epochDates.get(i)));
            od.phaseResidualRms.add(cp.epochPhaseRms.get(i));
        }
    }

    private static double sq(final double x) {
        return x * x;
    }

    static void hash(final List<Path> files, final RunResult res) {
        for (final Path f : files) {
            res.inputHashes.put(f.toString(), sha256(f));
        }
    }

    public static String sha256(final Path f) {
        try (InputStream in = Files.newInputStream(f)) {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            final byte[] buf = new byte[1 << 16];
            int r;
            while ((r = in.read(buf)) > 0) {
                md.update(buf, 0, r);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (final IOException | NoSuchAlgorithmException e) {
            return "unreadable";
        }
    }
}
