package org.ursc.trajectory.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End to end through real file formats: the simulated demo dataset (RINEX 3 obs and nav,
 * IGS-style SP3/CLK, POEORB-layout reference) run through the file-based pipeline.
 */
class DemoDatasetTest {

    private static RunConfig config(final Path dir, final RunConfig.ProductMode mode) {
        final RunConfig cfg = new RunConfig();
        cfg.cutoffUtc = DemoDataset.CUTOFF_UTC;
        cfg.inputs.gnssrd.add(dir.resolve(DemoDataset.OBS_FILE).toString());
        cfg.inputs.gpsProducts.add(dir.toString()); // the whole folder: loader sorts out what is what
        cfg.products.mode = mode;
        cfg.forecast.spanDays = 1.0;
        cfg.forecast.outputStepSeconds = 300;
        cfg.forecast.covarianceEveryNSteps = 12;
        return cfg;
    }

    @Test
    void forecastModeUsesOnlyCutoffEligibleProductsAndRecoversTheOrbit(@TempDir final Path dir) throws Exception {
        DemoDataset.write(dir, ProgressListener.NONE);
        final RunConfig cfg = config(dir, RunConfig.ProductMode.FORECAST);
        assertTrue(Runs.validate(cfg).isEmpty(), Runs.validate(cfg).toString());
        final RunResult res = Runs.execute(cfg, ProgressListener.NONE);
        assertEquals(RunResult.Status.SUCCESS, res.status, String.join("; ", res.errors));
        // Final (18 days later) and Rapid (next day 17:00) are published after the 06:00 cutoff
        assertEquals(2, res.productRejected.stream().filter(r -> r.contains("after cutoff")).count(), res.productRejected.toString());
        assertEquals(1, res.productUsage.size(), res.productUsage.toString());
        assertTrue(res.productUsage.keySet().iterator().next().startsWith("IGS Ultra-rapid"));
        assertTrue(res.spp.stream().anyMatch(r -> r.predictedOrbit), "arc lies in the ultra-rapid predicted half");
        assertEquals("SIMSAT", res.objectName); // from the RINEX MARKER NAME
        assertTrue(res.od.converged);

        final ReferenceComparison.Result cmp = ReferenceComparison.compare(
                List.of(dir.resolve(DemoDataset.REF_FILE)), res.states);
        assertEquals(res.states.size(), cmp.runIndex.size());
        final double atCutoff = cmp.err3d.get(0);
        final double atOneDay = cmp.err3d.get(cmp.err3d.size() - 1);
        System.out.printf(java.util.Locale.US, "[demo] fixes=%d postfit=%.2f m | error vs reference: cutoff %.2f m, +1 day %.2f m%n",
                res.od.observations, res.od.postFitRms, atCutoff, atOneDay);
        assertTrue(atCutoff < 5.0, "cutoff error " + atCutoff);
        assertTrue(atOneDay < 50.0, "1-day error " + atOneDay);
    }

    @Test
    void rejectsObservationsFromDifferentSpacecraft(@TempDir final Path dir) throws Exception {
        DemoDataset.write(dir, ProgressListener.NONE);
        final RunConfig cfg = config(dir, RunConfig.ProductMode.FORECAST);
        cfg.inputs.gnssrd.add(java.nio.file.Paths.get("data/gnss/observations/2024/"
                + "S3A_OPER_AUX_GNSSRD_POD__20241022T121051_V20241015T235942_20241016T235941.tgz").toAbsolutePath().toString());
        final List<String> errs = Runs.validate(cfg);
        assertTrue(errs.stream().anyMatch(e -> e.contains("different spacecraft (S3A, SIMSAT)")), errs.toString());
        assertEquals(RunResult.Status.FAILED_VALIDATION, Runs.execute(cfg, ProgressListener.NONE).status);
    }

    @Test
    void reconstructionModeUsesTheFinalProduct(@TempDir final Path dir) throws Exception {
        DemoDataset.write(dir, ProgressListener.NONE);
        final RunConfig cfg = config(dir, RunConfig.ProductMode.RECONSTRUCTION);
        cfg.forecast.spanDays = 0.1;
        cfg.forecast.covariance = false;
        final RunResult res = Runs.execute(cfg, ProgressListener.NONE);
        assertEquals(RunResult.Status.SUCCESS, res.status, String.join("; ", res.errors));
        assertTrue(res.label.startsWith("reconstruction"));
        assertTrue(res.productUsage.keySet().stream().anyMatch(k -> k.startsWith("IGS Final")), res.productUsage.toString());
    }
}
