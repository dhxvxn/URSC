package org.ursc.trajectory.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.ursc.trajectory.attitude.AttitudeProvider;
import org.ursc.trajectory.attitude.CompositeAttitude;
import org.ursc.trajectory.attitude.NominalYawSteering;
import org.ursc.trajectory.attitude.QuaternionFileAttitude;
import org.ursc.trajectory.bodies.CelestialBodyFactory;
import org.ursc.trajectory.bodies.EphemerisSource;
import org.ursc.trajectory.forces.drag.ConstantSpaceWeather;
import org.ursc.trajectory.forces.drag.CssiSpaceWeatherProvider;
import org.ursc.trajectory.forces.drag.CssiTxtSpaceWeatherProvider;
import org.ursc.trajectory.forces.drag.ForecastSpaceWeatherProvider;
import org.ursc.trajectory.forces.drag.Jb2008Inputs;
import org.ursc.trajectory.forces.drag.SolfsmyDtcProvider;
import org.ursc.trajectory.forces.drag.SpaceWeatherProvider;
import org.ursc.trajectory.forces.gravity.GravityField;
import org.ursc.trajectory.forces.gravity.GravityFieldFactory;
import org.ursc.trajectory.frames.ConstantEop;
import org.ursc.trajectory.frames.EopProvider;
import org.ursc.trajectory.frames.Finals2000AEopProvider;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.frames.IersEopProvider;
import org.ursc.trajectory.io.TarGz;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Global run environment built from a {@link RunConfig}: frames + EOP, space weather,
 * Sun/Moon ephemeris, gravity field and attitude. Every optional input that is absent is
 * replaced by a generic default, recorded in {@link #defaultsUsed}. Data beyond the cutoff
 * is withheld unless the config explicitly asks for a retrospective run.
 *
 * <p>Frames and the Sun/Moon ephemeris are process-wide settings in this library, so runs
 * must execute one at a time; {@link #close()} restores the previous ephemeris.</p>
 */
public final class Environment implements AutoCloseable {

    public final List<String> defaultsUsed = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
    public SpaceWeatherProvider weather;
    public Jb2008Inputs jb2008;
    public GravityField gravity;
    public int gravityDegree;
    public int gravityOrder;
    public AttitudeProvider attitude;
    /** Earth orientation of the run (held at the cutoff in forecast mode); feeds the Orekit frames. */
    public EopProvider eop = ConstantEop.ZERO;
    private final EphemerisSource previousEphemeris;
    private Path tempDir;

    private Environment() {
        previousEphemeris = CelestialBodyFactory.getEphemeris();
    }

    /**
     * @param cfg    run configuration
     * @param cutoff data after this epoch is withheld (forecast discipline)
     */
    public static Environment setUp(final RunConfig cfg, final AbsoluteDate cutoff) throws IOException {
        final Environment env = new Environment();
        try {
            env.frames(cfg, cutoff);
            env.weather(cfg, cutoff);
            env.ephemeris(cfg);
            env.gravity(cfg);
            env.attitude(cfg, cutoff);
        } catch (final IOException | RuntimeException e) {
            env.close();
            throw e;
        }
        return env;
    }

    private void frames(final RunConfig cfg, final AbsoluteDate cutoff) throws IOException {
        FramesFactory.setModel(FramesFactory.Model.IAU_2006);
        final String f = cfg.inputs.eopFile;
        EopProvider eop;
        if (f == null || f.isBlank()) {
            eop = ConstantEop.ZERO;
            defaultsUsed.add("Earth orientation: none supplied, UT1-UTC = 0 and zero polar motion "
                    + "(adds metres to tens of metres of frame error; supply finals2000A.all for precise work)");
        } else {
            final Path p = Paths.get(f);
            final boolean finals;
            try (InputStream in = Files.newInputStream(p)) {
                finals = Finals2000AEopProvider.looksLikeFinals2000A(in);
            }
            eop = finals ? new Finals2000AEopProvider(p, ConstantEop.ZERO) : new IersEopProvider(p, ConstantEop.ZERO);
            if (cfg.eop.afterCutoff == RunConfig.EopAfterCutoff.PERSIST && cutoff != null) {
                eop = new CutoffEop(eop, cutoff);
            } else if (cutoff != null) {
                warnings.add("EOP read beyond the cutoff (retrospective run)");
            }
        }
        FramesFactory.setEopProvider(eop);
        this.eop = eop;
    }

    /**
     * The JPL DE440 file of a run: the configured one, else {@code data/ephemeris/lnxp1990.440}
     * in the working folder or next to the program; null if none exists.
     */
    public static Path de440(final RunConfig cfg) {
        if (cfg.inputs.de440File != null && !cfg.inputs.de440File.isBlank()) {
            final Path p = Paths.get(cfg.inputs.de440File);
            return Files.isRegularFile(p) ? p : null;
        }
        final List<Path> candidates = new ArrayList<>();
        candidates.add(Paths.get("data", "ephemeris", "lnxp1990.440"));
        try {
            final Path jar = Paths.get(Environment.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path d = jar.getParent();
            for (int i = 0; i < 3 && d != null; i++, d = d.getParent()) {
                candidates.add(d.resolve("data").resolve("ephemeris").resolve("lnxp1990.440"));
            }
        } catch (final Exception ignored) {
            // no code-source location
        }
        for (final Path p : candidates) {
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    private void weather(final RunConfig cfg, final AbsoluteDate cutoff) throws IOException {
        final RunConfig.Weather w = cfg.weather;
        final ConstantSpaceWeather constant = new ConstantSpaceWeather(w.f107, w.f107a, w.ap);
        final String f = cfg.inputs.spaceWeatherFile;
        if (f == null || f.isBlank()) {
            weather = constant;
            if (cfg.forces.drag.enabled && cfg.forces.dragModel != RunConfig.DragModel.JB2008) {
                defaultsUsed.add(String.format(java.util.Locale.US,
                        "Space weather: none supplied, constant F10.7=%.1f, F10.7a=%.1f, Ap=%.1f", w.f107, w.f107a, w.ap));
            }
        } else {
            final Path p = Paths.get(f);
            String first = "";
            try (var r = Files.newBufferedReader(p)) {
                final String line = r.readLine();
                first = line == null ? "" : line;
            }
            final SpaceWeatherProvider observed = first.toUpperCase(java.util.Locale.US).contains("CSSISPACEWEATHER")
                    ? new CssiTxtSpaceWeatherProvider(p, constant) : new CssiSpaceWeatherProvider(p, constant);
            if (w.afterCutoff == RunConfig.WeatherAfterCutoff.PERSIST && cutoff != null) {
                weather = new ForecastSpaceWeatherProvider(observed, cutoff, w.forecastAp);
            } else {
                weather = observed;
                if (cutoff != null) {
                    warnings.add("Space weather read beyond the cutoff (retrospective run)");
                }
            }
        }
        if (cfg.forces.drag.enabled && cfg.forces.dragModel == RunConfig.DragModel.JB2008) {
            jb2008 = SolfsmyDtcProvider.bundled();
            defaultsUsed.add("JB2008 drivers: bundled SOLFSMY/DTCFILE snapshot");
            if (w.afterCutoff == RunConfig.WeatherAfterCutoff.PERSIST && cutoff != null) {
                jb2008 = new CutoffJb2008Inputs(jb2008, cutoff);
            }
        }
    }

    private void ephemeris(final RunConfig cfg) {
        // the Orekit engine reads the Sun and Moon from DE440 (see OrekitSession); the program's
        // own analytic series is only used by helper views (ground tracks, eclipses)
        CelestialBodyFactory.setEphemeris(null);
    }

    private void gravity(final RunConfig cfg) throws IOException {
        final RunConfig.Gravity g = cfg.forces.gravity;
        final String f = cfg.inputs.gravityFile;
        if (f != null && !f.isBlank()) {
            gravity = GravityFieldFactory.loadIcgem(Paths.get(f), g.degree, g.order);
        } else {
            final int deg = Math.min(g.degree, GravityFieldFactory.EGM96_MAX_DEGREE);
            if (g.degree > deg) {
                warnings.add("Gravity degree " + g.degree + " requested but bundled EGM96 stops at "
                        + deg + "; supply an ICGEM file for higher degree");
            }
            gravity = GravityFieldFactory.getEgm96(Math.max(2, deg));
            if (g.enabled) {
                defaultsUsed.add("Gravity field: bundled EGM96");
            }
        }
        gravityDegree = Math.min(g.degree, gravity.getMaxDegree());
        gravityOrder = Math.min(Math.min(g.order, g.degree), gravity.getMaxOrder());
    }

    private void attitude(final RunConfig cfg, final AbsoluteDate cutoff) throws IOException {
        if (cfg.spacecraft.shape != RunConfig.ShapeModel.BOX_WING) {
            return;
        }
        final AttitudeProvider nominal = new NominalYawSteering();
        if (cfg.spacecraft.attitude != RunConfig.AttitudeSource.MEASURED_PROQUA || cfg.inputs.proqua.isEmpty()) {
            attitude = nominal;
            if (cfg.spacecraft.attitude == RunConfig.AttitudeSource.MEASURED_PROQUA) {
                warnings.add("Measured attitude selected but no PROQUA files supplied; using nominal yaw steering");
            }
            return;
        }
        final List<QuaternionFileAttitude> measured = new ArrayList<>();
        for (final Path p : InputFiles.expand(cfg.inputs.proqua)) {
            final String n = p.getFileName().toString();
            try {
                Path dbl = p;
                if (n.endsWith(".tgz") || n.endsWith(".tar.gz")) {
                    if (tempDir == null) {
                        tempDir = Files.createTempDirectory("ursc-proqua");
                    }
                    dbl = tempDir.resolve(n.replaceAll("\\.(tgz|tar\\.gz)$", ".DBL"));
                    try (InputStream in = TarGz.openEntry(p, ".DBL")) {
                        Files.copy(in, dbl);
                    }
                }
                measured.add(new QuaternionFileAttitude(dbl, true));
            } catch (final IOException | RuntimeException e) {
                warnings.add(n + ": unreadable PROQUA attitude file (" + e.getMessage() + ")");
            }
        }
        final boolean forecastSafe = cfg.products.mode == RunConfig.ProductMode.FORECAST;
        attitude = new CompositeAttitude(measured, nominal, forecastSafe ? cutoff : null);
    }

    @Override
    public void close() {
        CelestialBodyFactory.setEphemeris(previousEphemeris);
        if (tempDir != null) {
            try (var s = Files.walk(tempDir)) {
                s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (final IOException ignored) {
                // best effort
            }
        }
    }
}
