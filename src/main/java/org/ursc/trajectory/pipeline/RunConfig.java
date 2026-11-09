package org.ursc.trajectory.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.ursc.trajectory.util.Json;

/**
 * Every user-adjustable setting of a run, with generic defaults. Only three things are
 * mandatory for the OD + forecast workflow: GNSSRD observation files, at least one GPS
 * orbit/clock product (IGS Final, Rapid, Ultra-rapid or broadcast navigation) and the UTC
 * cutoff. Everything else falls back to the defaults below, and the run record lists which
 * defaults were used. Plain public fields so the GUI and JSON presets bind directly.
 */
public final class RunConfig {

    public enum Workflow { OD_FORECAST, PROPAGATE }

    public enum ProductMode {
        /** Only products available by the cutoff (cutoff-safe forecast). */
        FORECAST,
        /** Best product present, regardless of issue time (retrospective; labelled as such). */
        RECONSTRUCTION
    }

    public enum Eligibility { NOMINAL_LATENCY, REQUIRE_VERIFIED }

    public enum ProductTier { AUTO, FINAL, RAPID, ULTRA_RAPID, BROADCAST }

    public enum OutputFrame { EME2000, GCRF, ITRF }

    public enum DragModel { NRLMSISE00, JB2008, HARRIS_PRIESTER, EXPONENTIAL }

    public enum ThirdBodyEphemeris { ANALYTIC, DE440 }

    public enum ShapeModel { CANNONBALL, BOX_WING }

    public enum AttitudeSource { NOMINAL_YAW_STEERING, MEASURED_PROQUA }

    public enum WeatherAfterCutoff {
        /** F10.7 / F10.7a held at the cutoff value, Ap set to the scenario value. */
        PERSIST,
        /** Retrospective: read the file beyond the cutoff (not cutoff-safe). */
        USE_FILE
    }

    public enum EopAfterCutoff { PERSIST, USE_FILE }

    public String runName = "";
    public Workflow workflow = Workflow.OD_FORECAST;
    /** Mandatory (OD_FORECAST): UTC cutoff, ISO-8601 e.g. 2024-10-16T06:00:00. */
    public String cutoffUtc = "";

    public Inputs inputs = new Inputs();
    public Products products = new Products();
    public Od od = new Od();
    public Forecast forecast = new Forecast();
    public Spacecraft spacecraft = new Spacecraft();
    public Forces forces = new Forces();
    public Weather weather = new Weather();
    public Eop eop = new Eop();
    public Integrator integrator = new Integrator();
    public InitialState initialState = new InitialState();

    /** Input files and folders (each entry may be a file or a folder, scanned recursively). */
    public static final class Inputs {
        /** Mandatory: GNSSRD observation archives (.tgz), .DBL or RINEX observation files. */
        public List<String> gnssrd = new ArrayList<>();
        /** Mandatory: GPS orbit/clock products: IGS SP3 + CLK (Final/Rapid/Ultra) and/or broadcast RINEX nav. */
        public List<String> gpsProducts = new ArrayList<>();
        /** Optional: POEORB precise orbits, display/comparison only (never used to compute a run). */
        public List<String> poeorb = new ArrayList<>();
        /** Optional: AUX_PROQUA measured attitude (used only with the box-wing model). */
        public List<String> proqua = new ArrayList<>();
        /** Optional: IERS finals2000A / EOP C04 file. */
        public String eopFile = "";
        /** Optional: CSSI space-weather file (SpaceWeather-All-v1.2.txt or SW-All.csv). */
        public String spaceWeatherFile = "";
        /** Optional: JPL DE440 binary ephemeris (lnxp1990.440). */
        public String de440File = "";
        /** Optional: ICGEM .gfc gravity field (else bundled EGM96). */
        public String gravityFile = "";
    }

    public static final class Products {
        public ProductMode mode = ProductMode.FORECAST;
        public Eligibility eligibility = Eligibility.NOMINAL_LATENCY;
        /** AUTO = priority order; otherwise force one tier for comparison runs. */
        public ProductTier tier = ProductTier.AUTO;
    }

    public static final class Od {
        /** Length of the measurement arc ending at the cutoff (h). */
        public double arcHours = 6.0;
        /** Observation sampling used for SPP / OD (s). */
        public double samplingSeconds = 300.0;
        /** Pseudorange one-sigma for SPP covariance and OD weighting (m). */
        public double pseudorangeSigma = 5.0;
        /** Covariance-weighted OD (else unit weights). */
        public boolean covarianceWeighting = true;
        public int maxIterations = 12;
        public int minSatellites = 4;
        /** Reject SPP fixes with PDOP above this. */
        public double maxPdop = 6.0;
        /** Reject SPP fixes with post-fit pseudorange RMS above this (m). */
        public double maxSppRms = 30.0;
        public boolean estimateCd = false;
        public boolean estimateCr = false;
    }

    public static final class Forecast {
        public double spanDays = 7.0;
        /** OEM output step (s). */
        public double outputStepSeconds = 60.0;
        public OutputFrame frame = OutputFrame.EME2000;
        /** Propagate the formal OD covariance into the OEM (costs one extra propagation per estimated parameter). */
        public boolean covariance = true;
        /** Covariance written every N output steps. */
        public int covarianceEveryNSteps = 60;
    }

    public static final class Spacecraft {
        /** OEM OBJECT_NAME; blank = taken from the GNSSRD file. */
        public String objectName = "";
        /** OEM OBJECT_ID (e.g. COSPAR 2016-011A); blank = unknown. */
        public String objectId = "";
        public double massKg = 1000.0;
        public ShapeModel shape = ShapeModel.CANNONBALL;
        public double dragAreaM2 = 5.0;
        public double srpAreaM2 = 5.0;
        public double cd = 2.2;
        public double cr = 1.3;
        public AttitudeSource attitude = AttitudeSource.NOMINAL_YAW_STEERING;
        // box-wing plate areas (m^2) and solar-array cant (deg)
        public double faceAreaX = 2.0;
        public double faceAreaY = 2.0;
        public double faceAreaZ = 2.0;
        public double wingAreaM2 = 5.0;
        public double arrayCantDeg = 0.0;
    }

    public static final class Forces {
        public Gravity gravity = new Gravity();
        public Toggle drag = new Toggle(true);
        public DragModel dragModel = DragModel.NRLMSISE00;
        public Toggle srp = new Toggle(true);
        public Toggle sun = new Toggle(true);
        public Toggle moon = new Toggle(true);
        public ThirdBodyEphemeris thirdBodyEphemeris = ThirdBodyEphemeris.ANALYTIC;
        public Toggle solidTides = new Toggle(true);
        public Toggle oceanTides = new Toggle(true);
        public Toggle relativity = new Toggle(true);
        public boolean lenseThirring = true;
        public Toggle albedo = new Toggle(true);
        public Empirical empirical = new Empirical();
    }

    public static final class Toggle {
        public boolean enabled;

        public Toggle() {
            this(true);
        }

        public Toggle(final boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static final class Gravity {
        /** When disabled only the central (two-body) term is used. */
        public boolean enabled = true;
        public int degree = 70;
        public int order = 70;
    }

    public static final class Empirical {
        public boolean enabled = false;
        /** Constant RTN accelerations (m/s^2). */
        public double radial = 0.0;
        public double transverse = 0.0;
        public double normal = 0.0;
    }

    public static final class Weather {
        /** Used when no space-weather file is supplied. */
        public double f107 = 150.0;
        public double f107a = 150.0;
        public double ap = 15.0;
        public WeatherAfterCutoff afterCutoff = WeatherAfterCutoff.PERSIST;
        /** Ap assumed after the cutoff (storms are not forecastable). */
        public double forecastAp = 15.0;
    }

    public static final class Eop {
        public EopAfterCutoff afterCutoff = EopAfterCutoff.PERSIST;
    }

    public static final class Integrator {
        public double absTol = 1e-10;
        public double relTol = 1e-12;
        public double maxStepSeconds = 30.0;
        public double minStepSeconds = 1e-3;
    }

    /** PROPAGATE workflow: where the initial state comes from. */
    public static final class InitialState {
        public enum Source { TYPED, OEM_FILE, POEORB_FILE }

        public Source source = Source.TYPED;
        public String file = "";
        /** Epoch (UTC ISO-8601) to pick from a file, or of the typed state. */
        public String epochUtc = "";
        public OutputFrame frame = OutputFrame.ITRF;
        /** Typed state (m, m/s). */
        public double x;
        public double y;
        public double z;
        public double vx;
        public double vy;
        public double vz;
    }

    // ---------------------------------------------------------------- persistence

    public String toJson() {
        return Json.write(this);
    }

    /** Parse a config (preset) JSON; unknown keys are returned in {@code warnings}. */
    public static RunConfig fromJson(final String json, final List<String> warnings) {
        final RunConfig c = new RunConfig();
        final List<String> unknown = Json.bind(Json.parseObject(json), c);
        if (warnings != null) {
            for (final String u : unknown) {
                warnings.add("unknown setting ignored: " + u);
            }
        }
        return c;
    }

    public static RunConfig load(final Path file, final List<String> warnings) throws IOException {
        return fromJson(Files.readString(file), warnings);
    }

    public void save(final Path file) throws IOException {
        Files.writeString(file, toJson());
    }
}
