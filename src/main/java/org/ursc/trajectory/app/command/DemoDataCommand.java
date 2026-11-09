package org.ursc.trajectory.app.command;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.ursc.trajectory.app.Args;
import org.ursc.trajectory.pipeline.DemoDataset;
import org.ursc.trajectory.pipeline.ProgressListener;

/** Writes the simulated demo dataset. */
public final class DemoDataCommand implements Command {

    @Override
    public String name() {
        return "demo-data";
    }

    @Override
    public String description() {
        return "write a SIMULATED dataset (GNSS obs, IGS-style products, nav, reference orbit) to try the GUI";
    }

    @Override
    public int run(final Args args) throws Exception {
        if (args.positionalCount() < 1) {
            System.err.println("usage: demo-data <folder>");
            return 2;
        }
        final Path dir = Paths.get(args.positional(0));
        final ProgressListener pl = new ProgressListener() {
            public void stage(final String name, final double fraction) {
                System.out.printf("%3.0f%%  %s%n", fraction * 100, name);
            }

            public void log(final String message) {
                System.out.println(message);
            }
        };
        for (final Path p : DemoDataset.write(dir, pl)) {
            System.out.println("wrote " + p);
        }
        System.out.println("Suggested cutoff: " + DemoDataset.CUTOFF_UTC + " UTC");
        return 0;
    }
}
