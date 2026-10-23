package org.ursc.trajectory.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@link TarGz} against a hand-built single-entry tar.gz archive. */
class TarGzTest {

    private static final int BLOCK = 512;

    /** Build a minimal tar.gz containing one entry with the given name and content. */
    private static Path writeArchive(final Path dir, final String entryName,
                                     final String content) throws IOException {
        final byte[] body = content.getBytes(StandardCharsets.UTF_8);
        final byte[] header = new byte[BLOCK];
        final byte[] name = entryName.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(name, 0, header, 0, name.length);
        final byte[] octal = String.format("%011o", body.length).getBytes(StandardCharsets.UTF_8);
        System.arraycopy(octal, 0, header, 124, octal.length); // size field, left as 11 digits + NUL

        final ByteArrayOutputStream tar = new ByteArrayOutputStream();
        tar.write(header);
        tar.write(body);
        final int pad = (BLOCK - body.length % BLOCK) % BLOCK;
        tar.write(new byte[pad]);
        tar.write(new byte[BLOCK * 2]); // two zero blocks = end of archive

        final Path path = dir.resolve("sample.tgz");
        try (GZIPOutputStream gz = new GZIPOutputStream(Files.newOutputStream(path))) {
            gz.write(tar.toByteArray());
        }
        return path;
    }

    private static String readAll(final InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buf = new byte[1024];
        int r;
        while ((r = in.read(buf)) >= 0) {
            out.write(buf, 0, r);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void readsNamedEntry(@TempDir final Path dir) throws IOException {
        final String content = "RINEX sample line 1\nRINEX sample line 2\n";
        final Path tgz = writeArchive(dir, "S3A_GNSSRD.DBL", content);
        try (InputStream in = TarGz.openEntry(tgz, ".DBL")) {
            assertEquals(content, readAll(in), "entry content round-trips");
        }
    }

    @Test
    void missingEntryThrows(@TempDir final Path dir) throws IOException {
        final Path tgz = writeArchive(dir, "S3A_GNSSRD.DBL", "x");
        assertThrows(IOException.class, () -> TarGz.openEntry(tgz, ".NOPE"));
    }
}
