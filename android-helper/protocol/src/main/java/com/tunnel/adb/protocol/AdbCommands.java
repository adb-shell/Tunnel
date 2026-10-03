package com.tunnel.adb.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Fixed-size privileged operations. Values are data, never commands or Intent components. */
public final class AdbCommands {
    public static final int TOUCH = 1, KEY = 2, NAVIGATE = 3, SCREENSHOT = 4, TREE = 5, DISPLAY = 6, RELEASE_INPUT = 7, CAPTURE_MODE = 8, OVERLAY_BLACK = 9, VIDEO_TASK = 10;
    public static final int OK = 0, UNSUPPORTED = 1, REJECTED = 2, BUSY = 3, FAILED = 4, STALE_GEOMETRY = 5;
    public static final int COMMAND_SIZE = 36, MAX_RESULT = 4 * 1024 * 1024 - 12;
    private AdbCommands() { }

    public static final class Command {
        public final long id;
        public final int operation, a, b, c, d, e, f;
        public Command(long id, int operation, int a, int b, int c, int d, int e, int f) throws IOException {
            if (id <= 0 || operation < TOUCH || operation > VIDEO_TASK) throw new IOException("OPERATION_INVALID");
            this.id = id; this.operation = operation; this.a = a; this.b = b; this.c = c; this.d = d; this.e = e; this.f = f;
            if (operation == TOUCH && (a < 0 || a > 3 || b < 0 || b > 1000000 || c < 0 || c > 1000000
                    || d < 1 || e < 1 || d > 4096 || e > 4096 || f != 0)) throw new IOException("TOUCH_INVALID");
            if (operation == KEY && (a < 0 || a > 1 || b < 1 || b > 288 || c < 0 || c > 0x7fffff || d != 0 || e != 0 || f != 0))
                throw new IOException("KEY_INVALID");
            if (operation == NAVIGATE && (a < 1 || a > 5 || b != 0 || c != 0 || d != 0 || e != 0 || f != 0))
                throw new IOException("NAVIGATION_INVALID");
            if (operation == DISPLAY && (a < 0 || a > 1 || b != 0 || c != 0 || d != 0 || e != 0 || f != 0))
                throw new IOException("DISPLAY_INVALID");
            if (operation == OVERLAY_BLACK && (a < 0 || a > 1 || b != 0 || c != 0 || d != 0 || e != 0 || f != 0))
                throw new IOException("OVERLAY_INVALID");
            if (operation == VIDEO_TASK && (a < 0 || a > 3 || b <= 0 || c != 0 || d != 0 || e != 0 || f != 0))
                throw new IOException("VIDEO_TASK_INVALID");
            if (operation == CAPTURE_MODE && (a < 0 || a > 3 || (b != 0 && b != 3) || c != 0 || d != 0 || e != 0 || f != 0))
                throw new IOException("CAPTURE_MODE_INVALID");
            if ((operation == SCREENSHOT || operation == TREE || operation == RELEASE_INPUT)
                    && (a != 0 || b != 0 || c != 0 || d != 0 || e != 0 || f != 0)) throw new IOException("ARGUMENT_INVALID");
        }
        public byte[] encode() {
            return ByteBuffer.allocate(COMMAND_SIZE).order(ByteOrder.BIG_ENDIAN).putLong(id).putInt(operation)
                    .putInt(a).putInt(b).putInt(c).putInt(d).putInt(e).putInt(f).array();
        }
        public static Command decode(byte[] data) throws IOException {
            if (data.length != COMMAND_SIZE) throw new IOException("OPERATION_INVALID");
            ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
            return new Command(b.getLong(), b.getInt(), b.getInt(), b.getInt(), b.getInt(), b.getInt(), b.getInt(), b.getInt());
        }
    }

    public static final class Result {
        public final long id;
        public final int code;
        private final byte[] body;
        public Result(long id, int code, byte[] body) throws IOException {
            if (id <= 0 || code < OK || code > STALE_GEOMETRY || body == null || body.length > MAX_RESULT)
                throw new IOException("RESULT_INVALID");
            this.id = id; this.code = code; this.body = body.clone();
        }
        public byte[] bodyCopy() { return body.clone(); }
        public byte[] encode() {
            return ByteBuffer.allocate(12 + body.length).order(ByteOrder.BIG_ENDIAN).putLong(id).putInt(code).put(body).array();
        }
        public static Result decode(byte[] data) throws IOException {
            if (data.length < 12 || data.length > MAX_RESULT + 12) throw new IOException("RESULT_INVALID");
            ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
            return new Result(b.getLong(), b.getInt(), Arrays.copyOfRange(data, 12, data.length));
        }
    }
}
