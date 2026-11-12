package org.ursc.trajectory.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

/** Everything a run produced: status, provenance, diagnostics and the output trajectory. */
public final class RunResult {

    public enum Status { SUCCESS, FAILED_VALIDATION, FAILED_SPP, FAILED_OD, CANCELLED, ERROR }

    public Status status = Status.ERROR;
    public String workflow = "";
    public String runName = "";
    /** "forecast (cutoff-safe)" or "reconstruction (retrospective)". */
    public String label = "";
    public String objectName = "";
    public String objectId = "";
    public String cutoffUtc = "";
    public final List<String> errors = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
    public final List<String> defaultsUsed = new ArrayList<>();
    public final List<String> log = new ArrayList<>();
    /** Quality warnings that deserve attention (e.g. a likely manoeuvre in the fitting arc). */
    public final List<String> flags = new ArrayList<>();

    // provenance
    /** Input file -> SHA-256. */
    public final Map<String, String> inputHashes = new LinkedHashMap<>();
    public final Map<String, Integer> productUsage = new LinkedHashMap<>();
    public final List<String> productRejected = new ArrayList<>();
    public final Map<String, Integer> productFallbacks = new LinkedHashMap<>();

    // SPP diagnostics
    public final List<SppRow> spp = new ArrayList<>();

    // OD
    public Od od;

    // output trajectory (GCRF) with optional 6x6 GCRF covariance
    public final List<State> states = new ArrayList<>();
    public String referenceFrameNote = "GCRF internally";

    public double elapsedSeconds;

    public static final class SppRow {
        public String utc;
        public String product;
        public int satellites;
        public double pdop = Double.NaN;
        public double rms = Double.NaN;
        public boolean accepted;
        public String reason = "";
        /** ITRF position (m) and receiver clock (m), NaN if unsolved. */
        public double x = Double.NaN;
        public double y = Double.NaN;
        public double z = Double.NaN;
        public double clock = Double.NaN;
        public boolean predictedOrbit;
    }

    public static final class Od {
        public boolean converged;
        public int iterations;
        public int observations;
        public double preFitRms;
        public double postFitRms;
        public double varianceFactor = Double.NaN;
        public boolean weighted;
        public String epochUtc;
        public String[] parameterNames;
        public double[] parameters;
        public double[][] covariance;
        /** Post-fit residual norms (m) per observation, with their UTC times. */
        public final List<String> residualUtc = new ArrayList<>();
        public final List<Double> residualNorm = new ArrayList<>();
        /** Measurements of the final fit: SPP_POSITIONS or CARRIER_PHASE. */
        public String method = "SPP_POSITIONS";
        /** Post-fit RMS of the SPP position fit (m); equals postFitRms for SPP-based runs. */
        public double sppPostFitRms = Double.NaN;
        /** Carrier-phase fit diagnostics (NaN / 0 when not used). */
        public double codeRms = Double.NaN;
        public double phaseRms = Double.NaN;
        public int codeObservations;
        public int phaseObservations;
        public int phaseArcs;
        public int cycleSlips;
        public int rejectedObservations;
        public boolean antennaOffsetApplied;
        /** Position change of the carrier-phase solution from the SPP-based one at the epoch (m). */
        public double changeFromSpp = Double.NaN;
        /** Post-fit carrier-phase residual RMS (m) per epoch, with UTC times. */
        public final List<String> phaseResidualUtc = new ArrayList<>();
        public final List<Double> phaseResidualRms = new ArrayList<>();
    }

    public static final class State {
        public final AbsoluteDate date;
        public final Vector3D position;
        public final Vector3D velocity;
        /** 6x6 GCRF covariance (m, m/s) or null. */
        public final double[][] covariance;

        public State(final AbsoluteDate date, final Vector3D position, final Vector3D velocity,
                     final double[][] covariance) {
            this.date = date;
            this.position = position;
            this.velocity = velocity;
            this.covariance = covariance;
        }
    }

    public boolean ok() {
        return status == Status.SUCCESS;
    }
}
