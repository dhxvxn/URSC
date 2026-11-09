package org.ursc.trajectory.pipeline;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Entry point for front ends: validate a configuration and execute the chosen workflow. */
public final class Runs {

    private Runs() {
    }

    /** Execute the configured workflow. Never throws. */
    public static RunResult execute(final RunConfig cfg, final ProgressListener pl) {
        return cfg.workflow == RunConfig.Workflow.PROPAGATE
                ? PropagateFromState.run(cfg, pl) : OdForecastPipeline.run(cfg, pl);
    }

    /** Problems that would stop a run (empty list = ready to run). */
    public static List<String> validate(final RunConfig cfg) {
        final List<String> errors = new ArrayList<>();
        if (cfg.workflow == RunConfig.Workflow.OD_FORECAST) {
            final RunResult tmp = new RunResult();
            OdForecastPipeline.validate(cfg, tmp);
            errors.addAll(tmp.errors);
            checkExists(cfg.inputs.gnssrd, "GNSSRD", errors);
            try {
                final List<String> craft = ObsLoader.spacecraft(InputFiles.expand(cfg.inputs.gnssrd));
                if (craft.size() > 1) {
                    errors.add("The GNSS observation files belong to different spacecraft (" + String.join(", ", craft)
                            + "); select the files of one spacecraft");
                }
            } catch (final java.io.IOException e) {
                // reported by checkExists
            }
            checkExists(cfg.inputs.gpsProducts, "GPS product", errors);
        } else {
            final RunConfig.InitialState is = cfg.initialState;
            if (is.source == RunConfig.InitialState.Source.TYPED) {
                if (is.epochUtc == null || is.epochUtc.isBlank()) {
                    errors.add("A typed initial state needs its UTC epoch");
                } else {
                    try {
                        TimeUtil.parseUtc(is.epochUtc);
                    } catch (final IllegalArgumentException e) {
                        errors.add(e.getMessage());
                    }
                }
                final double r = Math.sqrt(is.x * is.x + is.y * is.y + is.z * is.z);
                if (r < 6.4e6) {
                    errors.add("Initial position radius " + Math.round(r / 1000) + " km is below the Earth's surface");
                }
            } else if (is.file == null || is.file.isBlank() || !Files.isRegularFile(Paths.get(is.file))) {
                errors.add("Select an existing OEM or POEORB file for the initial state");
            }
            if (cfg.forecast.spanDays <= 0 || cfg.forecast.outputStepSeconds <= 0) {
                errors.add("Propagation span and output step must be positive");
            }
        }
        for (final String f : new String[] {cfg.inputs.eopFile, cfg.inputs.spaceWeatherFile, cfg.inputs.de440File,
                cfg.inputs.gravityFile}) {
            if (f != null && !f.isBlank() && !Files.isRegularFile(Paths.get(f))) {
                errors.add("File not found: " + f);
            }
        }
        return errors;
    }

    private static void checkExists(final List<String> entries, final String what, final List<String> errors) {
        for (final String e : entries) {
            if (e != null && !e.isBlank() && !Files.exists(Paths.get(e))) {
                errors.add(what + " input not found: " + e);
            }
        }
    }
}
