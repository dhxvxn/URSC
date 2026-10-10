package org.ursc.trajectory.app;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.ursc.trajectory.app.command.Command;
import org.ursc.trajectory.app.command.CompareCommand;
import org.ursc.trajectory.app.command.DecayCommand;
import org.ursc.trajectory.app.command.FitCommand;
import org.ursc.trajectory.app.command.PropagateCommand;
import org.ursc.trajectory.app.command.TleCommand;
import org.ursc.trajectory.app.command.ValidateCommand;
import org.ursc.trajectory.app.term.Ansi;

/**
 * Terminal entry point: dispatches to a subcommand. Zero external dependencies.
 *
 * <pre>
 *   java -jar leo-trajectory-0.1.0.jar &lt;command&gt; [args]
 *   commands: propagate, decay, compare, tle, validate, fit, help, version
 * </pre>
 */
public final class Cli {

    private static final String VERSION = "0.1.0";

    private static final Map<String, Command> COMMANDS = new LinkedHashMap<>();

    static {
        register(new PropagateCommand());
        register(new DecayCommand());
        register(new CompareCommand());
        register(new TleCommand());
        register(new ValidateCommand());
        register(new FitCommand());
    }

    private Cli() {
    }

    private static void register(final Command c) {
        COMMANDS.put(c.name(), c);
    }

    public static void main(final String[] argv) throws Exception {
        if (argv.length == 0 || argv[0].equals("help") || argv[0].equals("--help")
                || argv[0].equals("-h")) {
            printHelp();
            System.exit(argv.length == 0 ? 2 : 0);
            return;
        }
        if (argv[0].equals("version") || argv[0].equals("--version")) {
            System.out.println("URSC LEO Trajectory Predictor " + VERSION);
            return;
        }

        final Command command = COMMANDS.get(argv[0]);
        if (command == null) {
            System.err.println("unknown command: " + argv[0]);
            printHelp();
            System.exit(2);
            return;
        }

        final Args args = new Args(Arrays.copyOfRange(argv, 1, argv.length));
        try {
            System.exit(command.run(args));
        } catch (final Exception e) {
            System.err.println(Ansi.red("error: " + e.getMessage()));
            System.exit(1);
        }
    }

    private static void printHelp() {
        System.out.println(Ansi.bold("URSC LEO Trajectory Predictor " + VERSION));
        System.out.println("usage: leoprop <command> [options]\n");
        System.out.println("commands:");
        for (final Command c : COMMANDS.values()) {
            System.out.printf("  %-11s %s%n", c.name(), c.description());
        }
        System.out.println("  help        show this help");
        System.out.println("  version     print the version");
        System.out.println("\nexamples:");
        System.out.println("  leoprop propagate sample-scenario.properties --plot alt");
        System.out.println("  leoprop decay sample-scenario.properties --monte-carlo 20 --seed 1");
        System.out.println("  leoprop compare sample-scenario.properties "
                + "--vary atmosphere=exponential,harris-priester,nrlmsise00");
        System.out.println("  leoprop validate orbit.EOF --hours 24 --plot total");
        System.out.println("  leoprop fit orbit.EOF --estimate-cd --hours 6");
    }
}
