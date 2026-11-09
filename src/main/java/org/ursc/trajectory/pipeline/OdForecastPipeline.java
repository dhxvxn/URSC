package org.ursc.trajectory.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.ursc.trajectory.estimation.BatchLeastSquares;
import org.ursc.trajectory.estimation.PositionObservation;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.gnss.RinexObsReader;
import org.ursc.trajectory.gnss.SppEngine;
import org.ursc.trajectory.gnss.SppSolution;
import org.ursc.trajectory.gnss.products.GnssOrbitClockSource;
import org.ursc.trajectory.gnss.products.GnssProductResolver;
import org.ursc.trajectory.gnss.products.ProductLoader;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.orbits.CartesianOrbit;
import org.ursc.trajectory.orbits.PVCoordinates;
import org.ursc.trajectory.propagation.SampledPropagator;
import org.ursc.trajectory.propagation.analytical.KeplerianPropagator;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.sampling.EphemerisCollector;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * GNSS-driven orbit determination and forecast:
 * GNSSRD + GPS products (IGS Final > Rapid > Ultra-rapid > broadcast) + UTC cutoff
 * -> SPP fixes -> ITRF-to-GCRF -> covariance-weighted batch OD -> numerical forecast
 * with formal covariance. Truth (POEORB) is never read here.
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
            pl.stage("Reading GNSS observations", 0.02);
            final List<Path> obsFiles = InputFiles.expand(cfg.inputs.gnssrd);
            final List<Path> prodFiles = InputFiles.expand(cfg.inputs.gpsProducts);
            hash(obsFiles, res);
            hash(prodFiles, res);
            final ObsLoader.Loaded obs = ObsLoader.load(obsFiles, cfg.od.samplingSeconds);
            res.warnings.addAll(obs.problems);
            pl.stage("Loading GPS orbit/clock products", 0.06);
            final ProductLoader.Loaded prod = ProductLoader.load(prodFiles, Map.of());
            res.warnings.addAll(prod.problems);
            if (prod.sources.isEmpty()) {
                res.errors.add("No usable GPS orbit/clock product among the supplied files "
                        + "(need IGS SP3[+CLK] Final/Rapid/Ultra-rapid or RINEX broadcast navigation)");
                res.status = RunResult.Status.FAILED_VALIDATION;
                return res;
            }
            res.objectName = cfg.spacecraft.objectName.isBlank() ? obs.objectName : cfg.spacecraft.objectName;
            res.objectId = cfg.spacecraft.objectId.isBlank() ? obs.objectId : cfg.spacecraft.objectId;
            try (Environment env = Environment.setUp(cfg, cutoff)) {
                run(cfg, cutoff, obs.epochs, prod.sources, env, pl, res);
            }
        } catch (final Exception e) {
            res.errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
            res.status = RunResult.Status.ERROR;
        } finally {
            res.elapsedSeconds = (System.nanoTime() - t0) / 1e9;
        }
        return res;
    }

    /** Checks the three mandatory inputs; returns the parsed cutoff or null. */
    static AbsoluteDate validate(final RunConfig cfg, final RunResult res) {
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
        if (cfg.od.arcHours <= 0 || cfg.od.samplingSeconds <= 0 || cfg.forecast.spanDays < 0
                || cfg.forecast.outputStepSeconds <= 0) {
            res.errors.add("Arc length, sampling, forecast span and output step must be positive");
        }
        return res.errors.isEmpty() ? cutoff : null;
    }

    /** Core run on in-memory observations and sources (used directly by tests). */
    static void run(final RunConfig cfg, final AbsoluteDate cutoff, final List<RinexObsReader.Epoch> epochs,
                    final List<GnssOrbitClockSource> sources, final Environment env,
                    final ProgressListener pl, final RunResult res) {
        res.cutoffUtc = TimeUtil.formatUtc(cutoff);
        if (res.workflow.isEmpty()) {
            res.workflow = RunConfig.Workflow.OD_FORECAST.name();
        }
        res.defaultsUsed.addAll(env.defaultsUsed);
        res.warnings.addAll(env.warnings);
        final boolean forecastMode = cfg.products.mode == RunConfig.ProductMode.FORECAST;
        res.label = forecastMode ? "forecast (cutoff-safe)" : "reconstruction (retrospective, not cutoff-safe)";

        // ---- product resolution
        final GnssOrbitClockSource.ProductClass forced = cfg.products.tier == RunConfig.ProductTier.AUTO ? null
                : GnssOrbitClockSource.ProductClass.valueOf(cfg.products.tier.name());
        final GnssProductResolver resolver = new GnssProductResolver(sources,
                forecastMode ? GnssProductResolver.Mode.FORECAST : GnssProductResolver.Mode.RECONSTRUCTION,
                cutoff, GnssProductResolver.Eligibility.valueOf(cfg.products.eligibility.name()), forced,
                cfg.od.minSatellites);
        res.productRejected.addAll(resolver.rejected());
        if (resolver.eligible().isEmpty()) {
            res.errors.add("No GPS product is eligible" + (forecastMode ? " for this cutoff" : ""));
            res.status = RunResult.Status.FAILED_VALIDATION;
            return;
        }

        // ---- SPP over the arc
        final AbsoluteDate arcStart = cutoff.shiftedBy(-cfg.od.arcHours * 3600.0);
        final List<RinexObsReader.Epoch> arc = new ArrayList<>();
        for (final RinexObsReader.Epoch e : epochs) {
            if (!e.getDate().isBefore(arcStart) && !e.getDate().isAfter(cutoff)) {
                arc.add(e);
            }
        }
        if (arc.isEmpty()) {
            res.errors.add("No GNSS observation epochs between " + TimeUtil.formatUtc(arcStart) + " and the cutoff "
                    + res.cutoffUtc + " UTC; check the GNSSRD coverage");
            res.status = RunResult.Status.FAILED_SPP;
            return;
        }
        final SppEngine engine = new SppEngine();
        final List<PositionObservation> fixes = new ArrayList<>();
        Vector3D guess = Vector3D.ZERO;
        for (int k = 0; k < arc.size(); k++) {
            if (pl.cancelled()) {
                res.status = RunResult.Status.CANCELLED;
                return;
            }
            pl.stage("Single point positioning", 0.08 + 0.17 * k / arc.size());
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
            final SppSolution sol = engine.solveEpoch(e, sel.source, guess);
            if (sol == null) {
                row.reason = "fewer than 4 usable satellites";
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
            final Vector3D pG = FramesFactory.getTransform(FramesFactory.getITRF(), FramesFactory.getGCRF(),
                    e.getDate()).transformPosition(sol.getPosition());
            final double[][] cov = cfg.od.covarianceWeighting
                    ? FrameUtil.covItrfToGcrf(sol.covariance(cfg.od.pseudorangeSigma), e.getDate()) : null;
            if (cfg.od.covarianceWeighting && cov == null) {
                row.reason = "singular geometry";
                continue;
            }
            row.accepted = true;
            fixes.add(new PositionObservation(e.getDate(), pG, cov));
        }
        res.productUsage.putAll(resolver.usage());
        res.productFallbacks.putAll(resolver.fallbacks());
        pl.log("SPP: " + fixes.size() + " accepted fixes of " + arc.size() + " epochs");
        if (fixes.size() < 6) {
            res.errors.add("Only " + fixes.size() + " usable SPP fixes in the arc (need at least 6)");
            res.status = RunResult.Status.FAILED_SPP;
            return;
        }

        // ---- batch OD
        pl.stage("Orbit determination", 0.27);
        final AbsoluteDate epoch0 = fixes.get(0).getDate();
        final boolean estCd = cfg.od.estimateCd && cfg.forces.drag.enabled;
        final boolean estCr = cfg.od.estimateCr && cfg.forces.srp.enabled;
        final int p = 6 + (estCd ? 1 : 0) + (estCr ? 1 : 0);
        final double[] x0 = new double[p];
        final double[] scale = new double[p];
        final String[] names = new String[p];
        final Vector3D v0 = initialVelocity(fixes);
        final Vector3D r0 = fixes.get(0).getPosition();
        final double[] rv = {r0.getX(), r0.getY(), r0.getZ(), v0.getX(), v0.getY(), v0.getZ()};
        final String[] rvNames = {"x", "y", "z", "vx", "vy", "vz"};
        for (int i = 0; i < 6; i++) {
            x0[i] = rv[i];
            scale[i] = i < 3 ? 1.0 : 1.0e-3;
            names[i] = rvNames[i];
        }
        int k = 6;
        final int cdIdx = estCd ? k++ : -1;
        final int crIdx = estCr ? k++ : -1;
        if (estCd) {
            x0[cdIdx] = cfg.spacecraft.cd;
            scale[cdIdx] = 1.0e-2;
            names[cdIdx] = "Cd";
        }
        if (estCr) {
            x0[crIdx] = cfg.spacecraft.cr;
            scale[crIdx] = 1.0e-2;
            names[crIdx] = "Cr";
        }
        final double mass = cfg.spacecraft.massKg;
        final BatchLeastSquares.PropagatorFactory factory = q -> ForceModelBuilder.build(
                state(epoch0, q, mass), cdIdx >= 0 ? q[cdIdx] : cfg.spacecraft.cd,
                crIdx >= 0 ? q[crIdx] : cfg.spacecraft.cr, cfg, env, false);
        final BatchLeastSquares bls = new BatchLeastSquares(factory, fixes, scale);
        final BatchLeastSquares.Result fit = cfg.forecast.covariance
                ? bls.estimateWithCovariance(x0, cfg.od.maxIterations) : bls.estimate(x0, cfg.od.maxIterations);
        final RunResult.Od od = new RunResult.Od();
        od.converged = fit.converged;
        od.iterations = fit.iterations;
        od.observations = fixes.size();
        od.preFitRms = fit.preFitRms;
        od.postFitRms = fit.postFitRms;
        od.varianceFactor = fit.varianceFactor;
        od.weighted = fit.weighted;
        od.epochUtc = TimeUtil.formatUtc(epoch0);
        od.parameterNames = names;
        od.parameters = fit.params;
        od.covariance = fit.covariance;
        if (fit.residuals != null) {
            for (int i = 0; i < fixes.size(); i++) {
                od.residualUtc.add(TimeUtil.formatUtc(fixes.get(i).getDate()));
                od.residualNorm.add(fit.residuals[i].getNorm());
            }
        }
        res.od = od;
        pl.log(String.format(java.util.Locale.US, "OD: converged=%s iterations=%d post-fit RMS %.2f m",
                fit.converged, fit.iterations, fit.postFitRms));
        if (!fit.converged) {
            res.errors.add("Batch OD did not converge in " + cfg.od.maxIterations
                    + " iterations; no forecast issued (never replaced by a truth state)");
            res.status = RunResult.Status.FAILED_OD;
            return;
        }

        // ---- forecast from the cutoff, with formal covariance
        pl.stage("Forecast propagation", 0.45);
        final AbsoluteDate end = cutoff.shiftedBy(cfg.forecast.spanDays * 86400.0);
        final double[] cdcr = {cdIdx >= 0 ? fit.params[cdIdx] : cfg.spacecraft.cd,
                               crIdx >= 0 ? fit.params[crIdx] : cfg.spacecraft.cr};
        final List<SpacecraftState> nominal = trajectory(fit.params, cdcr, epoch0, cutoff, end, cfg, env, false);
        double[][][] covs = null;
        if (cfg.forecast.covariance && fit.covariance != null) {
            covs = propagateCovariance(fit.params, scale, fit.covariance, cdIdx, crIdx, epoch0, cutoff, end,
                    cfg, env, pl);
            if (covs == null) {
                if (pl.cancelled()) {
                    res.status = RunResult.Status.CANCELLED;
                    return;
                }
                res.warnings.add("Covariance propagation failed; OEM written without covariance");
            }
        } else if (cfg.forecast.covariance) {
            res.warnings.add("OD covariance unavailable (singular normal matrix); OEM written without covariance");
        }
        for (int i = 0; i < nominal.size(); i++) {
            final SpacecraftState s = nominal.get(i);
            res.states.add(new RunResult.State(s.getDate(), s.getPosition(), s.getVelocity(),
                    covs == null ? null : covs[i]));
        }
        pl.stage("Done", 1.0);
        res.status = RunResult.Status.SUCCESS;
    }

    static SpacecraftState state(final AbsoluteDate epoch, final double[] q, final double mass) {
        return new SpacecraftState(new CartesianOrbit(new PVCoordinates(new Vector3D(q[0], q[1], q[2]),
                new Vector3D(q[3], q[4], q[5])), FramesFactory.getGCRF(), epoch, Constants.EARTH_MU), mass);
    }

    /**
     * Initial velocity at the first fix: Gibbs initial orbit determination (exact for two-body
     * motion) on three fixes separated by at least ~10 minutes, then Keplerian propagation
     * back to the first fix. Falls back to a 3-point derivative for very short arcs.
     */
    static Vector3D initialVelocity(final List<PositionObservation> f) {
        final AbsoluteDate t0 = f.get(0).getDate();
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
        if (i1 > 0 && i2 > 0) {
            final Vector3D r1 = f.get(0).getPosition();
            final Vector3D r2 = f.get(i1).getPosition();
            final Vector3D r3 = f.get(i2).getPosition();
            final Vector3D v2 = gibbs(r1, r2, r3, Constants.EARTH_MU);
            if (v2 != null) {
                final SpacecraftState s2 = new SpacecraftState(new CartesianOrbit(new PVCoordinates(r2, v2),
                        FramesFactory.getGCRF(), f.get(i1).getDate(), Constants.EARTH_MU), 1.0);
                return new KeplerianPropagator(s2).propagate(t0).getVelocity();
            }
        }
        final PositionObservation a = f.get(0);
        final PositionObservation b = f.get(1);
        final PositionObservation c = f.get(2);
        final double t1 = b.getDate().durationFrom(a.getDate());
        final double t2 = c.getDate().durationFrom(a.getDate());
        final Vector3D d1 = b.getPosition().subtract(a.getPosition());
        final Vector3D d2 = c.getPosition().subtract(a.getPosition());
        return d1.scalarMultiply(t2 / (t1 * (t2 - t1))).subtract(d2.scalarMultiply(t1 / (t2 * (t2 - t1))));
    }

    /** Gibbs method (Vallado, Algorithm 54): velocity at {@code r2}, or null if degenerate. */
    static Vector3D gibbs(final Vector3D r1, final Vector3D r2, final Vector3D r3, final double mu) {
        final double m1 = r1.getNorm();
        final double m2 = r2.getNorm();
        final double m3 = r3.getNorm();
        final Vector3D z12 = r1.crossProduct(r2);
        final Vector3D z23 = r2.crossProduct(r3);
        final Vector3D z31 = r3.crossProduct(r1);
        final Vector3D n = z23.scalarMultiply(m1).add(z31.scalarMultiply(m2)).add(z12.scalarMultiply(m3));
        final Vector3D d = z12.add(z23).add(z31);
        final Vector3D s = r1.scalarMultiply(m2 - m3).add(r2.scalarMultiply(m3 - m1)).add(r3.scalarMultiply(m1 - m2));
        final double nd = n.getNorm() * d.getNorm();
        if (!(nd > 0)) {
            return null;
        }
        final double lg = Math.sqrt(mu / nd);
        return d.crossProduct(r2).scalarMultiply(lg / m2).add(s.scalarMultiply(lg));
    }

    /** Propagate params from epoch0 to the cutoff, then sample [cutoff, end] at the output step. */
    static List<SpacecraftState> trajectory(final double[] params, final double[] cdcr, final AbsoluteDate epoch0,
                                            final AbsoluteDate cutoff, final AbsoluteDate end, final RunConfig cfg,
                                            final Environment env, final boolean simplified) {
        final double mass = cfg.spacecraft.massKg;
        final SpacecraftState atCutoff = ForceModelBuilder.build(state(epoch0, params, mass), cdcr[0], cdcr[1],
                cfg, env, simplified).propagate(cutoff);
        final SampledPropagator prop = ForceModelBuilder.build(atCutoff, cdcr[0], cdcr[1], cfg, env, simplified);
        final EphemerisCollector col = new EphemerisCollector();
        prop.setStepHandler(cfg.forecast.outputStepSeconds, col);
        prop.propagate(end);
        return new ArrayList<>(col.getStates());
    }

    /**
     * Formal covariance P(t) = Phi(t) C Phi(t)^T, with Phi (6 x p) from finite-difference
     * propagation of each parameter (reduced force model, same perturbation sizes as the OD).
     */
    private static double[][][] propagateCovariance(final double[] params, final double[] scale,
            final double[][] cov, final int cdIdx, final int crIdx, final AbsoluteDate epoch0,
            final AbsoluteDate cutoff, final AbsoluteDate end, final RunConfig cfg, final Environment env,
            final ProgressListener pl) {
        final int p = params.length;
        final double[] cdcr = {cdIdx >= 0 ? params[cdIdx] : cfg.spacecraft.cd,
                               crIdx >= 0 ? params[crIdx] : cfg.spacecraft.cr};
        final List<SpacecraftState> ref = trajectory(params, cdcr, epoch0, cutoff, end, cfg, env, true);
        final int n = ref.size();
        final double[][][] phi = new double[n][6][p];
        for (int j = 0; j < p; j++) {
            if (pl.cancelled()) {
                return null;
            }
            pl.stage("Covariance propagation (" + (j + 1) + "/" + p + ")", 0.6 + 0.38 * j / p);
            final double[] q = params.clone();
            q[j] += scale[j];
            final double[] cc = {cdIdx >= 0 ? q[cdIdx] : cfg.spacecraft.cd, crIdx >= 0 ? q[crIdx] : cfg.spacecraft.cr};
            final List<SpacecraftState> pert = trajectory(q, cc, epoch0, cutoff, end, cfg, env, true);
            if (pert.size() != n) {
                return null;
            }
            for (int i = 0; i < n; i++) {
                final Vector3D dr = pert.get(i).getPosition().subtract(ref.get(i).getPosition());
                final Vector3D dv = pert.get(i).getVelocity().subtract(ref.get(i).getVelocity());
                final double[] d = {dr.getX(), dr.getY(), dr.getZ(), dv.getX(), dv.getY(), dv.getZ()};
                for (int a = 0; a < 6; a++) {
                    phi[i][a][j] = d[a] / scale[j];
                }
            }
        }
        final double[][][] out = new double[n][][];
        for (int i = 0; i < n; i++) {
            out[i] = FrameUtil.mul(FrameUtil.mul(phi[i], cov), FrameUtil.transpose(phi[i]));
        }
        return out;
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
