package org.ursc.trajectory.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ursc.trajectory.io.Oem;
import org.ursc.trajectory.math.Vector3D;

class PropagateFromStateTest {

    @Test
    void propagatesATypedStateThenReseedsFromItsOwnOem(@TempDir final Path dir) throws Exception {
        final RunConfig cfg = PipelineTest.config("");
        cfg.workflow = RunConfig.Workflow.PROPAGATE;
        cfg.forecast.spanDays = 0.25;
        cfg.forecast.outputStepSeconds = 600;
        cfg.forecast.frame = RunConfig.OutputFrame.ITRF;
        cfg.spacecraft.objectName = "TESTSAT";
        cfg.initialState.source = RunConfig.InitialState.Source.TYPED;
        cfg.initialState.frame = RunConfig.OutputFrame.ITRF;
        cfg.initialState.epochUtc = "2024-10-07T21:59:42";
        // first POEORB state of Sentinel-3A on 2024-10-07 (ITRF, m and m/s)
        cfg.initialState.x = 482304.989602;
        cfg.initialState.y = 1077999.632437;
        cfg.initialState.z = -7091304.589548;
        cfg.initialState.vx = 7497.731211;
        cfg.initialState.vy = -93.447784;
        cfg.initialState.vz = 495.577989;

        final RunResult a = PropagateFromState.run(cfg, ProgressListener.NONE);
        assertEquals(RunResult.Status.SUCCESS, a.status, String.join("; ", a.errors));
        assertEquals(37, a.states.size()); // 6 h at 10 min, inclusive
        // the first output state, expressed back in ITRF, is the typed state; the run converts with
        // Orekit's IERS 2010 frames and this check with the program's own IAU 2006/2000B frames,
        // which agree to a few millimetres (the 2000B nutation truncation)
        final RunResult.State s0 = a.states.get(0);
        final FrameUtil.PV itrf = FrameUtil.gcrfToItrf(s0.position, s0.velocity, s0.date);
        assertTrue(itrf.r.distance(new Vector3D(482304.989602, 1077999.632437, -7091304.589548)) < 0.02,
                "ITRF round trip " + itrf.r.distance(new Vector3D(482304.989602, 1077999.632437, -7091304.589548)));
        assertTrue(itrf.v.distance(new Vector3D(7497.731211, -93.447784, 495.577989)) < 2e-5);

        // write it as an ITRF OEM and re-seed from its 3rd line
        final Path oemFile = dir.resolve("a.oem");
        OemExport.toOem(a, cfg).write(oemFile);
        final Oem oem = Oem.read(oemFile);
        assertEquals("TESTSAT", oem.segments.get(0).get("OBJECT_NAME"));
        cfg.initialState.source = RunConfig.InitialState.Source.OEM_FILE;
        cfg.initialState.file = oemFile.toString();
        cfg.initialState.epochUtc = oem.segments.get(0).states.get(2).epoch;
        cfg.forecast.spanDays = 600.0 / 86400.0;
        final RunResult b = PropagateFromState.run(cfg, ProgressListener.NONE);
        assertEquals(RunResult.Status.SUCCESS, b.status, String.join("; ", b.errors));
        // same physics: b's start equals a's 3rd state and b's end equals a's 4th state
        assertTrue(b.states.get(0).position.distance(a.states.get(2).position) < 0.02,
                "re-seed start differs by " + b.states.get(0).position.distance(a.states.get(2).position));
        // (the OEM is written in ITRF with the program's frames and read back with Orekit's: the
        // centimetre-level difference between the two frame implementations shows here)
        assertTrue(b.states.get(1).position.distance(a.states.get(3).position) < 0.1,
                "re-seeded propagation differs by " + b.states.get(1).position.distance(a.states.get(3).position));
    }
}
