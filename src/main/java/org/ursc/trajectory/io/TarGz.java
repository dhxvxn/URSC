package org.ursc.trajectory.io;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * Minimal reader for a gzip-compressed tar archive ({@code .tgz}): opens a stream
 * over the first entry whose name ends with a given suffix, without extracting the
 * archive to disk.
 *
 * <p>The Sentinel-3A auxiliary products (GNSSRD observations, PROQUA attitude) are
 * distributed as {@code .tgz} bundles of a large ASCII {@code .DBL} plus a small
 * {@code .HDR}. This keeps the core dependency-free (the JDK has gzip but no tar)
 * and lets the huge {@code .DBL} be streamed and decimated rather than unpacked.
 * Only the classic/ustar header fields needed to locate and bound an entry (name,
 * size) are parsed.</p>
 */
public final class TarGz {

    private static final int BLOCK = 512;

    private TarGz() {
    }

    /**
     * Open a stream positioned at the start of the first entry whose name ends with
     * {@code nameSuffix}. The returned stream yields exactly that entry's bytes and
     * closes the underlying archive when closed.
     *
     * @throws IOException if the archive cannot be read or no matching entry exists
     */
    public static InputStream openEntry(final Path tgz, final String nameSuffix)
            throws IOException {
        final InputStream gz =
                new GZIPInputStream(new BufferedInputStream(Files.newInputStream(tgz)));
        final byte[] header = new byte[BLOCK];
        try {
            while (readFully(gz, header, BLOCK)) {
                if (isAllZero(header)) {
                    break; // end-of-archive marker
                }
                final String name = readName(header);
                final long size = readOctal(header, 124, 12);
                if (name.endsWith(nameSuffix)) {
                    return new BoundedInputStream(gz, size);
                }
                // skip this entry's content, padded up to a full block
                final long padded = ((size + BLOCK - 1) / BLOCK) * BLOCK;
                skipFully(gz, padded);
            }
        } catch (final IOException e) {
            gz.close();
            throw e;
        }
        gz.close();
        throw new IOException("no entry ending with '" + nameSuffix + "' in " + tgz);
    }

    private static String readName(final byte[] h) {
        // ustar prefix (345, 155) + name (0, 100); combine when a prefix is present
        final String name = cString(h, 0, 100);
        final String prefix = cString(h, 345, 155);
        return prefix.isEmpty() ? name : prefix + "/" + name;
    }

    private static String cString(final byte[] b, final int off, final int len) {
        int n = 0;
        while (n < len && b[off + n] != 0) {
            n++;
        }
        return new String(b, off, n, StandardCharsets.UTF_8).trim();
    }

    private static long readOctal(final byte[] b, final int off, final int len) {
        long value = 0;
        for (int i = 0; i < len; i++) {
            final int c = b[off + i];
            if (c == 0 || c == ' ') {
                if (value != 0) {
                    break;
                }
                continue;
            }
            value = value * 8 + (c - '0');
        }
        return value;
    }

    private static boolean isAllZero(final byte[] b) {
        for (final byte x : b) {
            if (x != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean readFully(final InputStream in, final byte[] buf, final int len)
            throws IOException {
        int off = 0;
        while (off < len) {
            final int r = in.read(buf, off, len - off);
            if (r < 0) {
                return off > 0 ? fail() : false;
            }
            off += r;
        }
        return true;
    }

    private static boolean fail() throws IOException {
        throw new IOException("truncated tar header");
    }

    private static void skipFully(final InputStream in, final long count) throws IOException {
        long remaining = count;
        final byte[] scratch = new byte[8192];
        while (remaining > 0) {
            final int want = (int) Math.min(scratch.length, remaining);
            final int r = in.read(scratch, 0, want);
            if (r < 0) {
                throw new IOException("truncated tar entry");
            }
            remaining -= r;
        }
    }

    /** A stream exposing exactly {@code limit} bytes of the underlying archive stream. */
    private static final class BoundedInputStream extends FilterInputStream {
        private long remaining;

        BoundedInputStream(final InputStream in, final long limit) {
            super(in);
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            final int b = in.read();
            if (b >= 0) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            final int r = in.read(b, off, (int) Math.min(len, remaining));
            if (r > 0) {
                remaining -= r;
            }
            return r;
        }
    }
}
