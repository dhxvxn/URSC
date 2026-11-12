package org.ursc.trajectory.app;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.ursc.trajectory.app.command.Command;
import org.ursc.trajectory.app.command.RunCommand;
import org.ursc.trajectory.app.command.CompareCommand;
import org.ursc.trajectory.app.command.DecayCommand;
import org.ursc.trajectory.app.command.DemoDataCommand;
import org.ursc.trajectory.app.command.BacktestCommand;
import org.ursc.trajectory.app.command.FitCommand;
import org.ursc.trajectory.app.command.ForecastCommand;
import org.ursc.trajectory.app.command.GuiCommand;
import org.ursc.trajectory.app.command.PredictCommand;
import org.ursc.trajectory.app.command.PropagateCommand;
import org.ursc.trajectory.app.command.SppCommand;
import org.ursc.trajectory.app.command.TleCommand;
import org.ursc.trajectory.app.command.ValidateCommand;
import org.ursc.trajectory.app.term.Ansi;

/**
 * Terminal entry point: dispatches to a subcommand. {@code gui} and {@code run} use the Orekit
 * engine (the program's orbit determination and forecast); the older research commands marked
 * "legacy" in the help still use the program's own in-house physics library.
 *
 * <pre>
 *   java -jar leo-trajectory-0.1.0.jar &lt;command&gt; [args]
 * </pre>
 */
public final class Cli {

    private static final String VERSION = "0.1.0";

    private static final Map<String, Command> COMMANDS = new LinkedHashMap<>();
    /** Commands that run the Orekit engine (the others are legacy research tools). */
    private static final java.util.Set<String> ENGINE = java.util.Set.of("gui", "run", "demo-data");

    static {
        register(new GuiCommand());
        register(new RunCommand());
        register(new DemoDataCommand());
        register(new PropagateCommand());
        register(new DecayCommand());
        register(new CompareCommand());
        register(new TleCommand());
        register(new ValidateCommand());
        register(new FitCommand());
        register(new BacktestCommand());
        register(new ForecastCommand());
        register(new PredictCommand());
        register(new SppCommand());
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
            System.out.println("GNSS Based Orbit Determination and Trajectory Prediction " + VERSION);
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
        System.out.println(Ansi.bold("GNSS Based Orbit Determination and Trajectory Prediction " + VERSION));
        System.out.println("usage: leoprop <command> [options]\n");
        System.out.println("commands:");
        System.out.println(" (Orekit engine)");
        for (final Command c : COMMANDS.values()) {
            if (ENGINE.contains(c.name())) {
                System.out.printf("  %-11s %s%n", c.name(), c.description());
            }
        }
        System.out.println(" (legacy research tools, in-house physics library)");
        for (final Command c : COMMANDS.values()) {
            if (!ENGINE.contains(c.name())) {
                System.out.printf("  %-11s %s%n", c.name(), c.description());
            }
        }
        System.out.println("  help        show this help");
        System.out.println("  version     print the version");
        System.out.println("\nexamples:");
        System.out.println("  leoprop gui");
        System.out.println("  leoprop run ~/URSC-workspace/runs/<id>/config.json --out results");
        System.out.println("  leoprop propagate sample-scenario.properties --plot alt");
        System.out.println("  leoprop decay sample-scenario.properties --monte-carlo 20 --seed 1");
        System.out.println("  leoprop compare sample-scenario.properties "
                + "--vary atmosphere=exponential,harris-priester,nrlmsise00");
        System.out.println("  leoprop validate orbit.EOF --hours 24 --plot total");
        System.out.println("  leoprop fit orbit.EOF --estimate-cd --hours 6");
        System.out.println("  leoprop backtest day1.EOF day2.EOF ... day7.EOF --sw-file SW-All.csv");
        System.out.println("  leoprop forecast seed.EOF --days 7 --sw-file SpaceWeather-All-v1.2.txt");
        System.out.println("  leoprop predict   (interactive: type the UTC + 6 state elements)");
        System.out.println("  leoprop predict --utc 2024-10-07T21:59:42 --pos 4.8e5,-1.6e6,... "
                + "--vel ...,...,... --days 8 --eop-file finals2000A.all --truth d1.eof,d8.eof");
    }
}
