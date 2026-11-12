package org.ursc.trajectory.app.command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.ursc.trajectory.app.Args;
import org.ursc.trajectory.pipeline.OemExport;
import org.ursc.trajectory.pipeline.ProgressListener;
import org.ursc.trajectory.pipeline.RunConfig;
import org.ursc.trajectory.pipeline.RunResult;
import org.ursc.trajectory.pipeline.Runs;
import org.ursc.trajectory.util.Json;

/**
 * Headless run of a saved run configuration (the JSON the GUI stores with every run, or a preset)
 * on the Orekit engine: OD + forecast or propagation, exactly as the GUI runs it. Writes the OEM
 * and the full result next to each other.
 */
public final class RunCommand implements Command {

    @Override
    public String name() {
        return "run";
    }

    @Override
    public String description() {
        return "run a saved configuration (OD + forecast or propagation) on the Orekit engine: run <config.json> [--out dir]";
    }

    @Override
    public int run(final Args args) throws Exception {
        if (args.positionalCount() < 1) {
            System.err.println("usage: run <config.json> [--out folder]");
            return 2;
        }
        final List<String> warnings = new ArrayList<>();
        final RunConfig cfg = RunConfig.fromJson(Files.readString(Paths.get(args.positional(0))), warnings);
        warnings.forEach(w -> System.out.println("config: " + w));
        final Path out = Files.createDirectories(Paths.get(args.get("out", ".")));
        final RunResult res = Runs.execute(cfg, new ProgressListener() {
            public void stage(final String name, final double fraction) {
                System.out.printf(Locale.US, "%3.0f%%  %s%n", fraction * 100, name);
            }

            public void log(final String message) {
                System.out.println(message);
            }
        });
        System.out.println("status: " + res.status);
        res.errors.forEach(e -> System.out.println("error: " + e));
        res.warnings.forEach(w -> System.out.println("warning: " + w));
        Files.writeString(out.resolve("result.json"), Json.write(Json.toMap(res)));
        if (res.ok() && !res.states.isEmpty()) {
            final Path oem = out.resolve("forecast.oem");
            OemExport.toOem(res, cfg).write(oem);
            System.out.println("wrote " + oem);
        }
        if (res.od != null) {
            System.out.printf(Locale.US, "OD: %s, phase RMS %.3f m, %d phase + %d code observations%n",
                    res.od.method, res.od.phaseRms, res.od.phaseObservations, res.od.codeObservations);
        }
        return res.ok() ? 0 : 1;
    }
}
