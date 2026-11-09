package org.ursc.trajectory.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Expands configured entries (files or folders, recursively) into a de-duplicated file list. */
public final class InputFiles {

    private InputFiles() {
    }

    public static List<Path> expand(final List<String> entries) throws IOException {
        final Set<Path> out = new LinkedHashSet<>();
        for (final String e : entries) {
            if (e == null || e.isBlank()) {
                continue;
            }
            final Path p = Paths.get(e.trim());
            if (Files.isDirectory(p)) {
                try (Stream<Path> s = Files.walk(p)) {
                    s.filter(Files::isRegularFile).filter(f -> !f.getFileName().toString().startsWith("."))
                            .sorted().forEach(f -> out.add(f.toAbsolutePath().normalize()));
                }
            } else if (Files.isRegularFile(p)) {
                out.add(p.toAbsolutePath().normalize());
            } else {
                throw new IOException("input not found: " + e);
            }
        }
        return new ArrayList<>(out);
    }
}
