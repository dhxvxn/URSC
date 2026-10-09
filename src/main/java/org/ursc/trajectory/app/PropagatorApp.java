package org.ursc.trajectory.app;

/**
 * Backwards-compatible entry point. Delegates to the {@code propagate} subcommand
 * of {@link Cli}; prefer {@code Cli} (the {@code leoprop} command) directly.
 *
 * <pre>
 *   java -cp target/classes org.ursc.trajectory.app.PropagatorApp scenario.properties
 * </pre>
 */
public final class PropagatorApp {

    private PropagatorApp() {
    }

    public static void main(final String[] args) throws Exception {
        final String[] forwarded = new String[args.length + 1];
        forwarded[0] = "propagate";
        System.arraycopy(args, 0, forwarded, 1, args.length);
        Cli.main(forwarded);
    }
}
