package org.ursc.trajectory.io;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/** Opens a product file, transparently decompressing gzip (.gz) or Unix compress (.Z) by content. */
public final class ProductInput {

    private ProductInput() {
    }

    public static InputStream open(final Path path) throws IOException {
        final BufferedInputStream in = new BufferedInputStream(Files.newInputStream(path));
        in.mark(4);
        final byte[] head = in.readNBytes(2);
        in.reset();
        if (head.length == 2 && (head[0] & 0xFF) == 0x1F && (head[1] & 0xFF) == 0x8B) {
            return new GZIPInputStream(in);
        }
        if (UnixCompress.isCompressed(head)) {
            try (InputStream z = in) {
                return new ByteArrayInputStream(UnixCompress.decompress(z));
            }
        }
        return in;
    }

    /** Strip compression suffixes (.gz, .Z) from a file name. */
    public static String baseName(final Path path) {
        String n = path.getFileName().toString();
        if (n.endsWith(".gz")) {
            n = n.substring(0, n.length() - 3);
        } else if (n.endsWith(".Z")) {
            n = n.substring(0, n.length() - 2);
        }
        return n;
    }
}
