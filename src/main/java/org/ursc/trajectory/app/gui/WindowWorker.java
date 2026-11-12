package org.ursc.trajectory.app.gui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.ursc.trajectory.pipeline.ProgressListener;
import org.ursc.trajectory.pipeline.RunConfig;

/**
 * One day of a window run in its own JVM: runs the configuration and saves it in the run store
 * exactly as an interactive run is saved. The GUI starts several of these in parallel, because
 * Orekit's data context is process-wide.
 *
 * <p>Arguments: workspace folder, run id, configuration JSON file.</p>
 */
public final class WindowWorker {

    private WindowWorker() {
    }

    public static void main(final String[] args) throws Exception {
        final Path workspace = Paths.get(args[0]);
        final String id = args[1];
        final List<String> warnings = new ArrayList<>();
        final RunConfig cfg = RunConfig.fromJson(Files.readString(Paths.get(args[2])), warnings);
        final RunStore runs = new RunStore(workspace);
        GuiServer.runAndSave(runs, id, cfg, warnings, ProgressListener.NONE);
        System.out.println("window worker " + id + " done");
        System.exit(0);
    }
}
