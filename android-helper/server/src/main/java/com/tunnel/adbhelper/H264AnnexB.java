package com.tunnel.adbhelper;

import com.tunnel.adb.protocol.AdbWire;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Bounded Annex-B inspection. AVCC and fragmented output are deliberately rejected in P0. */
final class H264AnnexB {
    private byte[] sps;
    private byte[] pps;

    void readConfiguration(byte[] bytes) throws IOException {
        if (bytes.length > AdbWire.MAX_CONFIG) {
            throw new IOException("CODEC_CONFIG_TOO_LARGE");
        }
        visit(bytes, (type, start, end) -> {
            if (type == 7) {
                sps = Arrays.copyOfRange(bytes, start, end);
            } else if (type == 8) {
                pps = Arrays.copyOfRange(bytes, start, end);
            }
        });
    }

    byte[] configuration() throws IOException {
        if (sps == null || pps == null) {
            return null;
        }
        if (sps.length + pps.length + 8 > AdbWire.MAX_CONFIG) {
            throw new IOException("CODEC_CONFIG_TOO_LARGE");
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream(sps.length + pps.length + 8);
        result.write(new byte[]{0, 0, 0, 1});
        result.write(sps);
        result.write(new byte[]{0, 0, 0, 1});
        result.write(pps);
        return result.toByteArray();
    }

    static boolean isIdr(byte[] bytes) throws IOException {
        final boolean[] idr = {false};
        visit(bytes, (type, start, end) -> { if (type == 5) idr[0] = true; });
        return idr[0];
    }

    private interface NalVisitor {
        void accept(int type, int start, int end);
    }

    private static void visit(byte[] bytes, NalVisitor visitor) throws IOException {
        int prefix = prefixLength(bytes, 0);
        if (prefix == 0) {
            throw new IOException("NAL_FORMAT_UNSUPPORTED");
        }
        int cursor = 0;
        int count = 0;
        while (cursor < bytes.length) {
            prefix = prefixLength(bytes, cursor);
            int start = cursor + prefix;
            if (prefix == 0 || start >= bytes.length || ++count > 4096) {
                throw new IOException("NAL_FORMAT_INVALID");
            }
            int end = start + 1;
            while (end < bytes.length && prefixLength(bytes, end) == 0) end++;
            int type = bytes[start] & 31;
            if ((bytes[start] & 128) != 0 || type == 0 || type >= 24) {
                throw new IOException("NAL_FORMAT_INVALID");
            }
            visitor.accept(type, start, end);
            cursor = end;
        }
    }

    private static int prefixLength(byte[] bytes, int offset) {
        if (offset + 2 >= bytes.length || bytes[offset] != 0 || bytes[offset + 1] != 0) return 0;
        if (bytes[offset + 2] == 1) return 3;
        return offset + 3 < bytes.length && bytes[offset + 2] == 0 && bytes[offset + 3] == 1 ? 4 : 0;
    }
}
