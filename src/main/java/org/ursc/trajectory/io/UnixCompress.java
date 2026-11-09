package org.ursc.trajectory.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Decoder for the Unix {@code compress} (.Z, LZW) format, which legacy IGS products
 * still use and the JDK cannot read. Supports block mode and 9-16 bit codes.
 */
public final class UnixCompress {

    private UnixCompress() {
    }

    /** @return true if the bytes start with the .Z magic number 0x1F 0x9D. */
    public static boolean isCompressed(final byte[] head) {
        return head.length >= 2 && (head[0] & 0xFF) == 0x1F && (head[1] & 0xFF) == 0x9D;
    }

    /** Fully decompress a .Z stream. */
    public static byte[] decompress(final InputStream in) throws IOException {
        final byte[] data = in.readAllBytes();
        if (!isCompressed(data) || data.length < 3) {
            throw new IOException("not a .Z (Unix compress) stream");
        }
        final int flags = data[2] & 0xFF;
        final int maxBits = flags & 0x1F;
        final boolean blockMode = (flags & 0x80) != 0;
        if (maxBits < 9 || maxBits > 16) {
            throw new IOException("unsupported .Z code width " + maxBits);
        }
        final int maxCodes = 1 << maxBits;
        final int[] prefix = new int[maxCodes];
        final byte[] suffix = new byte[maxCodes];
        for (int i = 0; i < 256; i++) {
            prefix[i] = -1;
            suffix[i] = (byte) i;
        }
        final byte[] stack = new byte[maxCodes + 1];
        final ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 4);

        int bits = 9;
        int next = blockMode ? 257 : 256;
        int oldCode = -1;
        byte firstChar = 0;
        long bitPos = 3L * 8;
        final long totalBits = (long) data.length * 8;
        // codes are read in groups of 8 codes; on width change the remainder of the group is skipped
        long groupStart = bitPos;

        while (true) {
            if (bitPos + bits > totalBits) {
                break;
            }
            int code = 0;
            for (int i = 0; i < bits; i++) {
                final long p = bitPos + i;
                if (((data[(int) (p >>> 3)] >>> (p & 7)) & 1) != 0) {
                    code |= 1 << i;
                }
            }
            bitPos += bits;

            if (blockMode && code == 256) {
                // clear table: skip to the end of the current 8-code group
                final long groupBits = (long) bits * 8;
                final long used = (bitPos - groupStart) % groupBits;
                if (used != 0) {
                    bitPos += groupBits - used;
                }
                bits = 9;
                next = 257;
                oldCode = -1;
                groupStart = bitPos;
                continue;
            }
            if (oldCode == -1) {
                if (code > 255) {
                    throw new IOException("corrupt .Z stream (first code " + code + ")");
                }
                firstChar = (byte) code;
                out.write(code);
                oldCode = code;
            } else {
                int cur = code;
                int sp = 0;
                if (code >= next) {
                    if (code > next) {
                        throw new IOException("corrupt .Z stream (code " + code + " > " + next + ")");
                    }
                    stack[sp++] = firstChar;
                    cur = oldCode;
                }
                while (cur >= 256) {
                    stack[sp++] = suffix[cur];
                    cur = prefix[cur];
                }
                firstChar = suffix[cur];
                stack[sp++] = firstChar;
                while (sp > 0) {
                    out.write(stack[--sp]);
                }
                if (next < maxCodes) {
                    prefix[next] = oldCode;
                    suffix[next] = firstChar;
                    next++;
                }
                oldCode = code;
            }
            if (next >= (1 << bits) && bits < maxBits) {
                // width grows: skip the rest of the current 8-code group
                final long groupBits = (long) bits * 8;
                final long used = (bitPos - groupStart) % groupBits;
                if (used != 0) {
                    bitPos += groupBits - used;
                }
                bits++;
                groupStart = bitPos;
            }
        }
        return out.toByteArray();
    }
}
