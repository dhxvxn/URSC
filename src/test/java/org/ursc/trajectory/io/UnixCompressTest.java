package org.ursc.trajectory.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The .Z fixture was produced by the reference Unix compress implementation (ncompress)
 * and cross-checked with the system {@code uncompress}; only the SHA-256 of its plaintext is
 * stored. 260 kB of SP3-like text drives the code width from 9 bits upward.
 */
class UnixCompressTest {

    private static final String SHA256 = "a229aa19b8a2905d1ad8257fcf2f7f274f801c982cb5955198616cbddc40be48";

    private static String sha(final byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    @Test
    void decodesReferenceCompressOutput() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/compress/sp3_sample.txt.Z")) {
            final byte[] out = UnixCompress.decompress(in);
            assertEquals(260000, out.length);
            assertEquals(SHA256, sha(out));
        }
    }

    @Test
    void productInputDetectsCompressionByContent(@TempDir final Path dir) throws Exception {
        final Path renamed = dir.resolve("igs23363.sp3"); // no .Z suffix: detection is by magic number
        try (InputStream in = getClass().getResourceAsStream("/compress/sp3_sample.txt.Z")) {
            Files.write(renamed, in.readAllBytes());
        }
        try (InputStream in = ProductInput.open(renamed)) {
            assertEquals(SHA256, sha(in.readAllBytes()));
        }
        assertEquals("igs23363.sp3", ProductInput.baseName(dir.resolve("igs23363.sp3.Z")));
    }

    @Test
    void rejectsNonCompressedInput() {
        assertThrows(IOException.class,
                () -> UnixCompress.decompress(new ByteArrayInputStream("plain".getBytes())));
    }
}
