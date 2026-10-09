package org.ursc.trajectory.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, dependency-free command-line argument parser. Supports positionals,
 * {@code --key value}, {@code --key=value}, and bare {@code --flag} booleans.
 */
public final class Args {

    private final List<String> positionals = new ArrayList<>();
    private final Map<String, String> options = new LinkedHashMap<>();
    private final List<String> flags = new ArrayList<>();

    public Args(final String[] argv) {
        for (int i = 0; i < argv.length; i++) {
            final String a = argv[i];
            if (a.startsWith("--")) {
                final String body = a.substring(2);
                final int eq = body.indexOf('=');
                if (eq >= 0) {
                    options.put(body.substring(0, eq), body.substring(eq + 1));
                } else if (i + 1 < argv.length && !argv[i + 1].startsWith("--")) {
                    options.put(body, argv[++i]);
                } else {
                    flags.add(body);
                }
            } else {
                positionals.add(a);
            }
        }
    }

    public int positionalCount() {
        return positionals.size();
    }

    public String positional(final int index) {
        return index < positionals.size() ? positionals.get(index) : null;
    }

    public boolean has(final String key) {
        return flags.contains(key) || options.containsKey(key);
    }

    public String get(final String key, final String def) {
        return options.getOrDefault(key, def);
    }

    public double getDouble(final String key, final double def) {
        final String v = options.get(key);
        return v == null ? def : Double.parseDouble(v.trim());
    }

    public int getInt(final String key, final int def) {
        final String v = options.get(key);
        return v == null ? def : Integer.parseInt(v.trim());
    }

    public long getLong(final String key, final long def) {
        final String v = options.get(key);
        return v == null ? def : Long.parseLong(v.trim());
    }

    /** Comma-separated list value, or an empty list if absent. */
    public List<String> getList(final String key) {
        final String v = options.get(key);
        if (v == null || v.isBlank()) {
            return new ArrayList<>();
        }
        final List<String> out = new ArrayList<>();
        for (final String part : v.split(",")) {
            out.add(part.trim());
        }
        return out;
    }

    @Override
    public String toString() {
        return "positionals=" + positionals + ", options=" + options + ", flags=" + flags;
    }

    /** Helper: all remaining positionals from an index onward. */
    public List<String> positionalsFrom(final int index) {
        if (index >= positionals.size()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(positionals.subList(index, positionals.size()));
    }

    public List<String> allPositionals() {
        return Arrays.asList(positionals.toArray(new String[0]));
    }
}
