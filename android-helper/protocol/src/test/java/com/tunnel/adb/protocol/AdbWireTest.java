package com.tunnel.adb.protocol;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class AdbWireTest {
    private interface IoAction { void run() throws Exception; }
    private static void rejects(IoAction action) throws Exception {
        try { action.run(); fail("Expected protocol rejection"); } catch (IOException expected) { }
    }
    private static byte[] secret() { byte[] key = new byte[32]; Arrays.fill(key, (byte) 0x5a); return key; }

    @Test public void typedOperationsRejectUnknownFieldsAndTruncations() throws Exception {
        byte[] touch = new AdbCommands.Command(1, AdbCommands.TOUCH, 0, 500000, 500000, 720, 1280, 0).encode();
        for (int length = 0; length < touch.length; length++) {
            final byte[] truncated = Arrays.copyOf(touch, length);
            rejects(() -> AdbCommands.Command.decode(truncated));
        }
        rejects(() -> new AdbCommands.Command(1, 99, 0, 0, 0, 0, 0, 0));
        rejects(() -> new AdbCommands.Command(1, AdbCommands.TOUCH, 0, 1000001, 0, 720, 1280, 0));
        rejects(() -> new AdbCommands.Command(1, AdbCommands.KEY, 0, 29, 0, 1, 0, 0));
        rejects(() -> new AdbCommands.Command(1, AdbCommands.CAPTURE_MODE, 4, 0, 0, 0, 0, 0));
        rejects(() -> AdbWire.Packet.of(AdbWire.VIDEO_CONFIG, 6, 9, 1, 1, 0, 720, 1280, new byte[]{1}));
        rejects(() -> AdbWire.Packet.of(AdbWire.VIDEO_FRAME, 2, 9, 1, 1, 0, 720, 1280, new byte[]{1}));
    }

    @Test(timeout = 10000) public void typedOperationAndResultRespectAuthenticatedDirection() throws Exception {
        try (Pair pair = new Pair(AdbWire.CHANNEL_CONTROL, secret())) {
            byte[] command = new AdbCommands.Command(1, AdbCommands.RELEASE_INPUT, 0, 0, 0, 0, 0, 0).encode();
            pair.client.write(AdbWire.Packet.of(AdbWire.OPERATION, 0, 9, 0, 1, 0, 0, 0, command));
            assertEquals(AdbCommands.RELEASE_INPUT, AdbCommands.Command.decode(pair.server.read().payloadCopy()).operation);
            byte[] body = {10}; AdbCommands.Result result = new AdbCommands.Result(1, AdbCommands.OK, body); body[0] = 0;
            pair.server.write(AdbWire.Packet.of(AdbWire.RESULT, 0, 9, 0, 1, 0, 0, 0, result.encode()));
            assertEquals(10, AdbCommands.Result.decode(pair.client.read().payloadCopy()).bodyCopy()[0]);
            rejects(() -> pair.client.write(AdbWire.Packet.of(AdbWire.RESULT, 0, 9, 0, 2, 0, 0, 0, result.encode())));
        }
    }

    @Test public void bootstrapIsFixedOwnedBoundedAndRejectsEveryTruncation() throws Exception {
        byte[] input = secret();
        AdbWire.Bootstrap boot = new AdbWire.Bootstrap(input, 9, 1234, 1235, 1280, 30, 4000000, 10);
        Arrays.fill(input, (byte) 0);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); AdbWire.writeBootstrap(bytes, boot);
        byte[] wire = bytes.toByteArray(); assertEquals(74, wire.length);
        AdbWire.Bootstrap read = AdbWire.readBootstrap(new ByteArrayInputStream(wire));
        assertArrayEquals(secret(), read.secretCopy()); assertEquals(10, read.durationSeconds);
        assertEquals(0, read.initialMode);
        for (int n = 0; n < wire.length; n++) {
            final byte[] truncated = Arrays.copyOf(wire, n);
            rejects(() -> AdbWire.readBootstrap(new ByteArrayInputStream(truncated)));
        }
        rejects(() -> new AdbWire.Bootstrap(secret(), 9, 1, 1, 1280, 30, 4000000, 10));
        rejects(() -> new AdbWire.Bootstrap(secret(), 9, 1, 2, 1280, 30, 4000000, 3601));
        boot.close(); read.close(); rejects(read::secretCopy);
    }

    @Test public void controlOnlyBootstrapAndOverlayCommandsAreBounded() throws Exception {
        try (AdbWire.Bootstrap boot = new AdbWire.Bootstrap(secret(), 9, 1234, 1235,
                1280, 30, 4000000, 0, 3)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            AdbWire.writeBootstrap(out, boot);
            try (AdbWire.Bootstrap read = AdbWire.readBootstrap(new ByteArrayInputStream(out.toByteArray()))) {
                assertEquals(3, read.initialMode);
                assertEquals(0, read.durationSeconds);
            }
            byte[] oldVersion = out.toByteArray(); oldVersion[5] = 3;
            rejects(() -> AdbWire.readBootstrap(new ByteArrayInputStream(oldVersion)));
        }
        rejects(() -> new AdbWire.Bootstrap(secret(), 9, 1, 2, 1280, 30, 4000000, 0, 4));
        assertEquals(AdbCommands.OVERLAY_BLACK, AdbCommands.Command.decode(
                new AdbCommands.Command(1, AdbCommands.OVERLAY_BLACK, 1, 0, 0, 0, 0, 0).encode()).operation);
        rejects(() -> new AdbCommands.Command(1, AdbCommands.OVERLAY_BLACK, 2, 0, 0, 0, 0, 0));
        rejects(() -> new AdbCommands.Command(1, AdbCommands.OVERLAY_BLACK, 1, 1, 0, 0, 0, 0));
        AdbWire.Packet.capabilities(9, 1, 0, AdbWire.CAP_OVERLAY);
    }


    @Test(timeout = 10000) public void videoTasksRemainIndependentOnOneAuthenticatedSession() throws Exception {
        try (Pair pair = new Pair(AdbWire.CHANNEL_VIDEO, secret())) {
            pair.server.write(AdbWire.Packet.of(AdbWire.VIDEO_CONFIG, 0, 9, 1, 1, 0, 720, 1280, 10, new byte[]{1}));
            assertEquals(10, pair.client.read().taskId);
            pair.server.write(AdbWire.Packet.of(AdbWire.VIDEO_FRAME, 1, 9, 1, 2, 100, 720, 1280, 10, new byte[]{2}));
            assertEquals(10, pair.client.read().taskId);
            pair.server.write(AdbWire.Packet.of(AdbWire.VIDEO_CONFIG, 0, 9, 2, 3, 0, 720, 1280, 11, new byte[]{3}));
            assertEquals(11, pair.client.read().taskId);
            // A restarted codec may reset PTS, but must still start with a key frame.
            pair.server.write(AdbWire.Packet.of(AdbWire.VIDEO_FRAME, 1, 9, 2, 4, 1, 720, 1280, 11, new byte[]{4}));
            assertEquals(11, pair.client.read().taskId);
            rejects(() -> pair.server.write(AdbWire.Packet.of(AdbWire.VIDEO_FRAME, 0, 9, 2, 5, 2, 720, 1280, 10, new byte[]{5})));
        }
        rejects(() -> new AdbCommands.Command(1, AdbCommands.VIDEO_TASK, 0, 0, 0, 0, 0, 0));
        rejects(() -> new AdbCommands.Command(1, AdbCommands.VIDEO_TASK, 4, 1, 0, 0, 0, 0));
        assertEquals(10, AdbCommands.Command.decode(new AdbCommands.Command(1,
                AdbCommands.VIDEO_TASK, 3, 10, 0, 0, 0, 0).encode()).b);
        rejects(() -> AdbWire.Packet.of(AdbWire.VIDEO_CONFIG, 0, 9, 1, 1, 0, 720, 1280, 0, new byte[]{1}));
        rejects(() -> AdbWire.Packet.of(AdbWire.PING, 0, 9, 0, 1, 0, 0, 0, 1, new byte[0]));
        try (Pair pair = new Pair(AdbWire.CHANNEL_CONTROL, secret())) {
            byte[] state = java.nio.ByteBuffer.allocate(8).putInt(10).putInt(2).array();
            pair.server.write(AdbWire.Packet.of(AdbWire.VIDEO_STATE, 0, 9, 0, 1, 0, 0, 0, state));
            assertEquals(AdbWire.VIDEO_STATE, pair.client.read().kind);
            pair.client.write(AdbWire.Packet.command(AdbWire.PING, 9, 1));
            assertEquals(AdbWire.PING, pair.server.read().kind);
        }
    }

    @Test public void sessionDurationRoundTripsAndOlderHelpersAreRejected() throws Exception {
        try (AdbWire.Bootstrap boot = new AdbWire.Bootstrap(secret(), 9, 1234, 1235, 1280, 30,
                4000000, AdbWire.SESSION_DURATION)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); AdbWire.writeBootstrap(bytes, boot);
            byte[] wire = bytes.toByteArray();
            try (AdbWire.Bootstrap read = AdbWire.readBootstrap(new ByteArrayInputStream(wire))) {
                assertEquals(AdbWire.SESSION_DURATION, read.durationSeconds);
            }
            wire[4] = 0; wire[5] = 2;
            rejects(() -> AdbWire.readBootstrap(new ByteArrayInputStream(wire)));
        }
        rejects(() -> new AdbWire.Bootstrap(secret(), 9, 1, 2, 1280, 30, 4000000, -1));
    }

    @Test public void packetsOwnPayloadAndRejectBoundsAndUnknownCommands() throws Exception {
        byte[] data = {1, 2};
        AdbWire.Packet p = AdbWire.Packet.of(AdbWire.VIDEO_CONFIG, 0, 9, 1, 1, 0, 1280, 720, data);
        data[0] = 7; byte[] copy = p.payloadCopy(); copy[0] = 8;
        assertEquals(1, p.payloadCopy()[0]);
        rejects(() -> AdbWire.Packet.command(99, 9, 1));
        rejects(() -> AdbWire.Packet.command(AdbWire.STOP, -1, 1));
        rejects(() -> AdbWire.Packet.command(AdbWire.STOP, 9, 0));
        rejects(() -> AdbWire.Packet.capabilities(9, 1, 2, 0));
        rejects(() -> AdbWire.Packet.of(AdbWire.VIDEO_FRAME, 1, 9, 1, 1, 0,
                Integer.MAX_VALUE, Integer.MAX_VALUE, data));
        rejects(() -> AdbWire.Packet.of(AdbWire.VIDEO_CONFIG, 0, 9, 1, 1, 0,
                1280, 720, new byte[AdbWire.MAX_CONFIG + 1]));
    }

    @Test(timeout = 10000) public void mutuallyAuthenticatesAndBindsControlDirectionsAndSequence() throws Exception {
        try (Pair pair = new Pair(AdbWire.CHANNEL_CONTROL, secret())) {
            pair.server.write(AdbWire.Packet.capabilities(9, 1, 1, 3));
            assertEquals(AdbWire.CAPABILITIES, pair.client.read().kind);
            pair.client.write(AdbWire.Packet.command(AdbWire.PING, 9, 1));
            assertEquals(AdbWire.PING, pair.server.read().kind);
            pair.server.write(AdbWire.Packet.command(AdbWire.PONG, 9, 2));
            assertEquals(2, pair.client.read().sequence);
            rejects(() -> pair.client.write(AdbWire.Packet.command(AdbWire.STOP, 9, 1)));
        }
        try (Pair pair = new Pair(AdbWire.CHANNEL_CONTROL, secret())) {
            rejects(() -> pair.client.write(AdbWire.Packet.capabilities(9, 1, 1, 3)));
        }
    }

    @Test(timeout = 10000) public void videoRequiresConfigThenKeyframeAndMonotonicPts() throws Exception {
        try (Pair pair = new Pair(AdbWire.CHANNEL_VIDEO, secret())) {
            pair.server.write(video(AdbWire.VIDEO_CONFIG, 0, 1, 0)); pair.client.read();
            pair.server.write(video(AdbWire.VIDEO_FRAME, 1, 2, 500)); pair.client.read();
            rejects(() -> pair.server.write(video(AdbWire.VIDEO_FRAME, 0, 3, 499)));
        }
        try (Pair pair = new Pair(AdbWire.CHANNEL_VIDEO, secret())) {
            rejects(() -> pair.server.write(video(AdbWire.VIDEO_FRAME, 1, 1, 0)));
        }
        try (Pair pair = new Pair(AdbWire.CHANNEL_VIDEO, secret())) {
            pair.server.write(video(AdbWire.VIDEO_CONFIG, 0, 1, 0)); pair.client.read();
            rejects(() -> pair.server.write(video(AdbWire.VIDEO_FRAME, 0, 2, 0)));
        }
    }

    @Test(timeout = 10000) public void tamperingAndMaliciousLengthsPoisonSession() throws Exception {
        for (int offset : new int[] {48, 52, 56}) {
            try (Pair pair = new Pair(AdbWire.CHANNEL_CONTROL, secret())) {
                pair.output.tamperAt = offset; // Payload length, task identity, or authenticated payload.
                pair.server.write(AdbWire.Packet.capabilities(9, 1, 1, 3));
                rejects(pair.client::read); rejects(pair.client::read);
            }
        }
    }

    @Test(timeout = 10000) public void wrongSecretCannotEstablishSession() throws Exception {
        byte[] wrong = secret(); wrong[0] ^= 1;
        rejects(() -> { try (Pair ignored = new Pair(AdbWire.CHANNEL_CONTROL, wrong)) { } });
    }

    @Test(timeout = 10000) public void epochAndConfigCannotCrossSession() throws Exception {
        try (Pair pair = new Pair(AdbWire.CHANNEL_CONTROL, secret())) {
            rejects(() -> pair.client.write(AdbWire.Packet.command(AdbWire.STOP, 10, 1)));
        }
        try (Pair pair = new Pair(AdbWire.CHANNEL_VIDEO, secret())) {
            pair.server.write(video(AdbWire.VIDEO_CONFIG, 0, 1, 0)); pair.client.read();
            rejects(() -> pair.server.write(AdbWire.Packet.of(AdbWire.VIDEO_FRAME, 1, 9, 2, 2,
                    0, 1280, 720, new byte[] {1})));
        }
        rejects(() -> { try (Pair ignored = new Pair(AdbWire.CHANNEL_CONTROL, secret(), 10)) { } });
    }

    @Test(timeout = 10000) public void partialRecordFailsClosed() throws Exception {
        try (Pair pair = new Pair(AdbWire.CHANNEL_CONTROL, secret())) {
            pair.output.truncateAt = 20;
            rejects(() -> pair.server.write(AdbWire.Packet.capabilities(9, 1, 1, 3)));
            rejects(pair.client::read);
        }
    }

    private static AdbWire.Packet video(int kind, int flags, long sequence, long pts) throws IOException {
        return AdbWire.Packet.of(kind, flags, 9, 1, sequence, pts, 1280, 720, new byte[] {0, 0, 0, 1, 7});
    }
    private static final class MutatingOutput extends FilterOutputStream {
        int tamperAt = -1, truncateAt = -1;
        MutatingOutput(OutputStream out) { super(out); }
        @Override public void write(byte[] b, int off, int len) throws IOException {
            if (truncateAt >= 0 && truncateAt < len) {
                out.write(b, off, truncateAt); out.flush(); throw new IOException("Synthetic truncation");
            }
            if (truncateAt >= 0) truncateAt -= len;
            byte[] owned = Arrays.copyOfRange(b, off, off + len);
            if (tamperAt >= 0) {
                if (tamperAt < len) { owned[tamperAt] ^= (byte) 0x80; tamperAt = -1; }
                else tamperAt -= len;
            }
            out.write(owned);
        }
    }
    private static final class Pair implements AutoCloseable {
        Socket left, right;
        AdbWire.Session client, server;
        MutatingOutput output;
        Pair(int channel, byte[] serverSecret) throws Exception {
            this(channel, serverSecret, 9);
        }
        Pair(int channel, byte[] serverSecret, long serverEpoch) throws Exception {
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName(AdbWire.LOOPBACK_HOST))) {
                left = new Socket(AdbWire.LOOPBACK_HOST, listener.getLocalPort()); right = listener.accept();
                left.setSoTimeout(2000); right.setSoTimeout(2000);
                output = new MutatingOutput(right.getOutputStream());
                Future<AdbWire.Session> future = worker.submit(() -> AdbWire.authenticateServer(
                        right.getInputStream(), output, serverSecret, serverEpoch, channel, new SecureRandom()));
                client = AdbWire.authenticateClient(left.getInputStream(), left.getOutputStream(),
                        secret(), 9, channel, new SecureRandom());
                server = future.get(3, TimeUnit.SECONDS);
            } catch (Exception e) { close(); throw e; }
            finally { worker.shutdownNow(); }
        }
        @Override public void close() throws IOException {
            if (left != null) left.close(); if (right != null) right.close();
            if (client != null) client.close(); if (server != null) server.close();
        }
    }
}
