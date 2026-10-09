package org.ursc.trajectory.app.command;

import org.ursc.trajectory.app.Args;

/** A CLI subcommand. */
public interface Command {

    /** @return the subcommand name used on the command line. */
    String name();

    /** @return a one-line description for the help listing. */
    String description();

    /**
     * Execute the subcommand.
     *
     * @param args parsed arguments (positionals do not include the subcommand name)
     * @return process exit code (0 = success)
     */
    int run(Args args) throws Exception;
}
