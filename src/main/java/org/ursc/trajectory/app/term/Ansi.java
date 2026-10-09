package org.ursc.trajectory.app.term;

/**
 * Minimal ANSI colour helper. Colour is auto-disabled when output is not a
 * terminal (piped/redirected) or when the {@code NO_COLOR} environment variable
 * is set, so machine-readable piping stays clean.
 */
public final class Ansi {

    private static final boolean ENABLED =
            System.getenv("NO_COLOR") == null && System.console() != null;

    private static final String RESET = "\u001B[0m";

    private Ansi() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    private static String wrap(final String code, final String s) {
        return ENABLED ? code + s + RESET : s;
    }

    public static String bold(final String s) {
        return wrap("\u001B[1m", s);
    }

    public static String dim(final String s) {
        return wrap("\u001B[2m", s);
    }

    public static String red(final String s) {
        return wrap("\u001B[31m", s);
    }

    public static String green(final String s) {
        return wrap("\u001B[32m", s);
    }

    public static String yellow(final String s) {
        return wrap("\u001B[33m", s);
    }

    public static String cyan(final String s) {
        return wrap("\u001B[36m", s);
    }
}
