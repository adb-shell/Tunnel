package com.tunnel.adb.protocol;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Local authenticated framing. Only typed, bounded operations exist; never shell strings.
 * Callers must set socket deadlines before authentication/read and cancel blocked writes
 * by closing the socket. HMAC authenticates records; it does not encrypt video.
 */
public final class AdbWire {
    public static final String LOOPBACK_HOST = "127.0.0.1";
    public static final int VERSION = 6, CHANNEL_VIDEO = 1, CHANNEL_CONTROL = 2;
    /** Production sessions end on cancellation/disconnect, not elapsed usage time. */
    public static final int SESSION_DURATION = 0;
    public static final int CAPABILITIES = 1, VIDEO_CONFIG = 2, VIDEO_FRAME = 3;
    public static final int REQUEST_KEYFRAME = 4, STOP = 5, PING = 6, PONG = 7;
    public static final int OPERATION = 8, RESULT = 9, VIDEO_STATE = 10, EFFECTS_STATE = 11;
    public static final int FLAG_KEY_FRAME = 1, CODEC_H264 = 1;
    public static final int CAP_VIDEO = 1, CAP_KEYFRAME = 2;
    public static final int CAP_INPUT = 4, CAP_SCREENSHOT = 8, CAP_TREE = 16, CAP_DISPLAY = 32;
    public static final int CAP_OVERLAY = 64;
    public static final int CAP_TOUCH_BLOCK = 128;
    public static final int ALL_CAPS = CAP_VIDEO | CAP_KEYFRAME | CAP_INPUT | CAP_SCREENSHOT | CAP_TREE | CAP_DISPLAY | CAP_OVERLAY | CAP_TOUCH_BLOCK;
    public static final int MAX_PAYLOAD = 8 * 1024 * 1024, MAX_CONFIG = 64 * 1024;
    public static final int MAX_SIDE = 4096, MAX_PIXELS = 8 * 1024 * 1024;
    private static final int MAGIC = 0x54414442, HEADER_SIZE = 56, NONCE_SIZE = 32; // TADB
    private static final int CLIENT = 1, SERVER = 2;
    private static final int BOOTSTRAP_MAGIC = 0x54414254; // TABT
    private AdbWire() {}

    /** Fixed stdin-only startup record; never put this secret in argv, URLs or logs. */
    public static final class Bootstrap implements Closeable {
        public final long epoch;
        public final int videoPort, controlPort, maxSize, fps, bitrate, durationSeconds, initialMode;
        private final byte[] secret;
        private boolean destroyed;
        public Bootstrap(byte[] secret, long epoch, int videoPort, int controlPort,
                         int maxSize, int fps, int bitrate, int durationSeconds) throws IOException {
            this(secret, epoch, videoPort, controlPort, maxSize, fps, bitrate, durationSeconds, 0);
        }
        public Bootstrap(byte[] secret, long epoch, int videoPort, int controlPort,
                         int maxSize, int fps, int bitrate, int durationSeconds, int initialMode) throws IOException {
            if (secret == null || secret.length != 32 || epoch <= 0 || videoPort < 1 || videoPort > 65535
                    || controlPort < 1 || controlPort > 65535 || videoPort == controlPort
                    || maxSize < 256 || maxSize > 1920 || fps < 1 || fps > 60
                    || bitrate < 128000 || bitrate > 16000000 || durationSeconds < SESSION_DURATION || durationSeconds > 3600
                    || initialMode < 0 || initialMode > 4)
                throw invalid("bootstrap bounds");
            this.secret = secret.clone(); this.epoch = epoch;
            this.videoPort = videoPort; this.controlPort = controlPort; this.maxSize = maxSize;
            this.fps = fps; this.bitrate = bitrate; this.durationSeconds = durationSeconds;
            this.initialMode = initialMode;
        }
        public synchronized byte[] secretCopy() throws IOException {
            if (destroyed) throw invalid("destroyed bootstrap");
            return secret.clone();
        }
        @Override public synchronized void close() { destroyed = true; Arrays.fill(secret, (byte) 0); }
    }

    public static Bootstrap readBootstrap(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(input);
        if (in.readInt() != BOOTSTRAP_MAGIC || in.readUnsignedShort() != VERSION) throw invalid("bootstrap version");
        byte[] secret = new byte[32];
        try {
            in.readFully(secret);
            return new Bootstrap(secret, in.readLong(), in.readInt(), in.readInt(), in.readInt(),
                    in.readInt(), in.readInt(), in.readInt(), in.readInt());
        } finally { Arrays.fill(secret, (byte) 0); }
    }

    public static void writeBootstrap(OutputStream output, Bootstrap bootstrap) throws IOException {
        byte[] secret = bootstrap.secretCopy();
        try {
            DataOutputStream out = new DataOutputStream(output);
            out.writeInt(BOOTSTRAP_MAGIC); out.writeShort(VERSION); out.write(secret); out.writeLong(bootstrap.epoch);
            out.writeInt(bootstrap.videoPort); out.writeInt(bootstrap.controlPort); out.writeInt(bootstrap.maxSize);
            out.writeInt(bootstrap.fps); out.writeInt(bootstrap.bitrate); out.writeInt(bootstrap.durationSeconds);
            out.writeInt(bootstrap.initialMode); out.flush();
        } finally { Arrays.fill(secret, (byte) 0); }
    }

    /** Immutable, owned packet. sequence starts at one independently per channel/direction. */
    public static final class Packet {
        public final int kind, flags, width, height, taskId;
        public final long epoch, configRevision, sequence, ptsUs;
        private final byte[] payload;
        private Packet(int kind, int flags, long epoch, long configRevision, long sequence,
                       long ptsUs, int width, int height, int taskId, byte[] payload) throws IOException {
            validate(kind, flags, epoch, configRevision, sequence, ptsUs, width, height, payload.length);
            this.kind = kind; this.flags = flags; this.epoch = epoch;
            this.configRevision = configRevision; this.sequence = sequence; this.ptsUs = ptsUs;
            this.width = width; this.height = height; this.taskId = taskId; this.payload = payload;
            if ((kind == VIDEO_CONFIG || kind == VIDEO_FRAME) ? taskId <= 0 : taskId != 0)
                throw invalid("task identity");
            if (kind == VIDEO_STATE) {
                ByteBuffer state = bytes(payload);
                int task = state.getInt(), value = state.getInt();
                if (task <= 0 || value < 0 || value > 2) throw invalid("video state");
            }
            if (kind == EFFECTS_STATE && (bytes(payload).getInt() & ~3) != 0)
                throw invalid("effects state");
            if (kind == CAPABILITIES) {
                ByteBuffer b = bytes(payload);
                if ((b.getInt() & ~CODEC_H264) != 0 || (b.getInt() & ~ALL_CAPS) != 0)
                    throw invalid("capability bits");
            }
        }
        public static Packet of(int kind, int flags, long epoch, long configRevision, long sequence,
                                long ptsUs, int width, int height, byte[] payload) throws IOException {
            return of(kind, flags, epoch, configRevision, sequence, ptsUs, width, height,
                    kind == VIDEO_CONFIG || kind == VIDEO_FRAME ? 1 : 0, payload);
        }
        public static Packet of(int kind, int flags, long epoch, long configRevision, long sequence,
                                long ptsUs, int width, int height, int taskId, byte[] payload) throws IOException {
            if (payload == null) throw invalid("missing payload");
            // Reject excessive allocations before making the owned copy.
            validate(kind, flags, epoch, configRevision, sequence, ptsUs, width, height, payload.length);
            return new Packet(kind, flags, epoch, configRevision, sequence, ptsUs, width, height, taskId, payload.clone());
        }
        public static Packet capabilities(long epoch, long sequence, int codecs, int capabilities) throws IOException {
            return of(CAPABILITIES, 0, epoch, 0, sequence, 0, 0, 0,
                    bytes(new byte[8]).putInt(codecs).putInt(capabilities).array());
        }
        public static Packet command(int kind, long epoch, long sequence) throws IOException {
            return of(kind, 0, epoch, 0, sequence, 0, 0, 0, new byte[0]);
        }
        public int payloadLength() { return payload.length; }
        public byte[] payloadCopy() { return payload.clone(); }
    }

    public static Session authenticateClient(InputStream in, OutputStream out, byte[] secret,
                                             long epoch, int channel, SecureRandom random) throws IOException {
        return authenticate(in, out, secret, epoch, channel, random, true);
    }
    public static Session authenticateServer(InputStream in, OutputStream out, byte[] secret,
                                             long epoch, int channel, SecureRandom random) throws IOException {
        return authenticate(in, out, secret, epoch, channel, random, false);
    }

    private static Session authenticate(InputStream input, OutputStream output, byte[] secret,
                                         long epoch, int channel, SecureRandom random, boolean client) throws IOException {
        if (secret == null || secret.length != 32 || epoch <= 0 || random == null
                || (channel != CHANNEL_VIDEO && channel != CHANNEL_CONTROL)) throw invalid("session parameters");
        DataInputStream in = new DataInputStream(input);
        DataOutputStream out = new DataOutputStream(output);
        byte[] key = secret.clone(), cn = new byte[NONCE_SIZE], sn = new byte[NONCE_SIZE];
        try {
            if (client) {
                random.nextBytes(cn);
                out.write(prefix(epoch, channel, CLIENT)); out.write(cn); out.flush();
                readPrefix(in, epoch, channel, SERVER); in.readFully(sn);
            } else {
                readPrefix(in, epoch, channel, CLIENT); in.readFully(cn);
                random.nextBytes(sn);
                out.write(prefix(epoch, channel, SERVER)); out.write(sn);
            }
            byte[] transcript = bytes(new byte[16 + 2 * NONCE_SIZE])
                    .put(prefix(epoch, channel, CLIENT)).put(cn).put(sn).array();
            if (client) {
                verify(in, mac(key, "server-proof", transcript));
                out.write(mac(key, "client-proof", transcript)); out.flush();
                verify(in, mac(key, "server-ready", transcript));
            } else {
                out.write(mac(key, "server-proof", transcript)); out.flush();
                verify(in, mac(key, "client-proof", transcript));
                out.write(mac(key, "server-ready", transcript)); out.flush();
            }
            return new Session(in, out, epoch, channel, client,
                    mac(key, client ? "s2c-record" : "c2s-record", transcript),
                    mac(key, client ? "c2s-record" : "s2c-record", transcript));
        } finally {
            Arrays.fill(key, (byte) 0); Arrays.fill(cn, (byte) 0); Arrays.fill(sn, (byte) 0);
        }
    }

    /** One connection, one epoch and channel. Any read/write error permanently poisons it.
     * Owns the supplied streams after authentication; close() cancels I/O and clears keys.
     * Concurrent one-reader/one-writer is supported without holding a common blocking lock.
     */
    public static final class Session implements Closeable {
        private final DataInputStream in;
        private final DataOutputStream out;
        private final long epoch;
        private final int channel;
        private final boolean client;
        private final byte[] readKey, writeKey;
        private final Object readLock = new Object(), writeLock = new Object();
        private final Tracker reads = new Tracker(), writes = new Tracker();
        private volatile boolean closed;
        private Session(DataInputStream in, DataOutputStream out, long epoch, int channel,
                        boolean client, byte[] readKey, byte[] writeKey) {
            this.in = in; this.out = out; this.epoch = epoch; this.channel = channel;
            this.client = client; this.readKey = readKey; this.writeKey = writeKey;
        }
        public Packet read() throws IOException {
            synchronized (readLock) {
                try {
                    ensureOpen();
                    byte[] header = new byte[HEADER_SIZE]; in.readFully(header);
                    ByteBuffer b = bytes(header);
                    if (b.getInt() != MAGIC || b.getShort() != VERSION) throw invalid("record version");
                    int kind = b.get() & 255, flags = b.get() & 255;
                    long ep = b.getLong(), cfg = b.getLong(), seq = b.getLong(), pts = b.getLong();
                    int width = b.getInt(), height = b.getInt(), length = b.getInt(), taskId = b.getInt();
                    validate(kind, flags, ep, cfg, seq, pts, width, height, length);
                    checkDirection(kind, !client);
                    if (ep != epoch) throw invalid("epoch");
                    byte[] payload = new byte[length]; in.readFully(payload);
                    verify(in, mac(readKey, "record", header, payload));
                    ensureOpen();
                    Packet p = new Packet(kind, flags, ep, cfg, seq, pts, width, height, taskId, payload);
                    reads.accept(p); return p;
                } catch (IOException e) { poison(); throw e; }
            }
        }
        public void write(Packet p) throws IOException {
            synchronized (writeLock) {
                try {
                    ensureOpen();
                    if (p == null || p.epoch != epoch) throw invalid("epoch");
                    checkDirection(p.kind, client); writes.accept(p);
                    byte[] header = bytes(new byte[HEADER_SIZE]).putInt(MAGIC).putShort((short) VERSION)
                            .put((byte) p.kind).put((byte) p.flags).putLong(p.epoch).putLong(p.configRevision)
                            .putLong(p.sequence).putLong(p.ptsUs).putInt(p.width).putInt(p.height)
                            .putInt(p.payload.length).putInt(p.taskId).array();
                    out.write(header); out.write(p.payload); out.write(mac(writeKey, "record", header, p.payload)); out.flush();
                } catch (IOException e) { poison(); throw e; }
            }
        }
        private void checkDirection(int kind, boolean fromClient) throws IOException {
            boolean allowed = channel == CHANNEL_VIDEO
                    ? !fromClient && (kind == VIDEO_CONFIG || kind == VIDEO_FRAME)
                    : fromClient ? kind == REQUEST_KEYFRAME || kind == STOP || kind == PING || kind == OPERATION
                    : kind == CAPABILITIES || kind == PONG || kind == RESULT || kind == VIDEO_STATE || kind == EFFECTS_STATE;
            if (!allowed) throw invalid("channel or direction");
        }
        private void ensureOpen() throws IOException { if (closed) throw invalid("closed session"); }
        private void poison() { try { close(); } catch (IOException ignored) { } }
        @Override public void close() throws IOException {
            closed = true;
            Arrays.fill(readKey, (byte) 0); Arrays.fill(writeKey, (byte) 0);
            IOException failure = null;
            try { in.close(); } catch (IOException e) { failure = e; }
            try { out.close(); } catch (IOException e) { if (failure == null) failure = e; }
            if (failure != null) throw failure;
        }
    }

    private static final class Tracker {
        long sequence, config, lastPts = -1;
        int width, height, taskId;
        boolean needsKey = true;
        void accept(Packet p) throws IOException {
            if (sequence == Long.MAX_VALUE || p.sequence != sequence + 1) throw invalid("sequence");
            if (p.kind == VIDEO_CONFIG) {
                if (p.configRevision <= config) throw invalid("config revision");
                config = p.configRevision; width = p.width; height = p.height; taskId = p.taskId; needsKey = true; lastPts = -1;
            } else if (p.kind == VIDEO_FRAME) {
                if (config == 0 || p.configRevision != config || p.width != width || p.height != height || p.taskId != taskId)
                    throw invalid("frame config");
                if (p.ptsUs < lastPts || (needsKey && (p.flags & FLAG_KEY_FRAME) == 0)) throw invalid("frame order");
                lastPts = p.ptsUs; needsKey = false;
            }
            sequence = p.sequence;
        }
    }

    private static void validate(int kind, int flags, long epoch, long config, long sequence,
                                 long pts, int width, int height, int length) throws IOException {
        if (epoch <= 0 || sequence <= 0 || config < 0 || pts < 0 || length < 0 || length > MAX_PAYLOAD)
            throw invalid("record bounds");
        if (kind == VIDEO_CONFIG || kind == VIDEO_FRAME) {
            if (config == 0 || width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE
                    || (long) width * height > MAX_PIXELS || length == 0) throw invalid("video bounds");
            if (kind == VIDEO_CONFIG ? (flags != 0 && flags != 2 && flags != 4 && flags != 8) || pts != 0 || length > MAX_CONFIG
                    : (flags & ~FLAG_KEY_FRAME) != 0) throw invalid("video flags");
        } else {
            if (flags != 0 || config != 0 || pts != 0 || width != 0 || height != 0) throw invalid("control metadata");
            if (kind == CAPABILITIES || kind == VIDEO_STATE) { if (length != 8) throw invalid("capability size"); }
            else if (kind == EFFECTS_STATE) { if (length != 4) throw invalid("effects size"); }
            else if (kind == OPERATION) { if (length != AdbCommands.COMMAND_SIZE) throw invalid("operation size"); }
            else if (kind == RESULT) { if (length < 12 || length > 4 * 1024 * 1024) throw invalid("result size"); }
            else if (kind == REQUEST_KEYFRAME || kind == STOP || kind == PING || kind == PONG) {
                if (length != 0) throw invalid("control size");
            } else throw invalid("record kind");
        }
    }
    private static byte[] prefix(long epoch, int channel, int role) {
        return bytes(new byte[16]).putInt(MAGIC).putShort((short) VERSION)
                .put((byte) channel).put((byte) role).putLong(epoch).array();
    }
    private static void readPrefix(DataInputStream in, long epoch, int channel, int role) throws IOException {
        byte[] actual = new byte[16]; in.readFully(actual);
        if (!MessageDigest.isEqual(prefix(epoch, channel, role), actual)) throw invalid("handshake context");
    }
    private static void verify(DataInputStream in, byte[] expected) throws IOException {
        byte[] actual = new byte[32]; in.readFully(actual);
        if (!MessageDigest.isEqual(expected, actual)) throw invalid("authentication");
    }
    private static byte[] mac(byte[] key, String purpose, byte[]... data) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(("Tunnel/ADB/v" + VERSION + "/" + purpose + "\0").getBytes(StandardCharsets.US_ASCII));
            for (byte[] part : data) mac.update(part);
            return mac.doFinal();
        } catch (GeneralSecurityException e) { throw new IOException("ADB protocol cryptography unavailable", e); }
    }
    private static ByteBuffer bytes(byte[] bytes) { return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN); }
    private static IOException invalid(String reason) { return new IOException("ADB protocol rejected: " + reason); }
}
