package com.tunnel.adbhelper;

import android.os.Build;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;

import com.tunnel.adb.protocol.AdbWire;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Local, short-lived ADB capture diagnostic. Not a remote-control server. */
public final class Server {
    private Server() { }

    public static void main(String[] args) {
        Lifecycle lifecycle = new Lifecycle();
        AdbWire.Bootstrap options = null;
        byte[] secret = null;
        Thread captureThread = null;
        lifecycle.startWatchdog();
        try {
            if (args.length != 0 || Process.myUid() != 2000) {
                throw new IOException("SHELL_UID_REQUIRED");
            }
            if (Build.VERSION.SDK_INT < 30 || Build.VERSION.SDK_INT > 36) {
                throw new IOException("ANDROID_VERSION_UNSUPPORTED");
            }
            options = AdbWire.readBootstrap(System.in);
            secret = options.secretCopy();

            // SERVER is the helper's protocol role, independent of TCP direction.
            // Both sockets are mutually authenticated before any capture API is called.
            Socket videoSocket = lifecycle.connect(options.videoPort);
            AdbWire.Session video = AdbWire.authenticateServer(videoSocket.getInputStream(),
                    videoSocket.getOutputStream(), secret, options.epoch, AdbWire.CHANNEL_VIDEO, new SecureRandom());
            lifecycle.installVideo(video);
            Socket controlSocket = lifecycle.connect(options.controlPort);
            AdbWire.Session control = AdbWire.authenticateServer(controlSocket.getInputStream(),
                    controlSocket.getOutputStream(), secret, options.epoch, AdbWire.CHANNEL_CONTROL, new SecureRandom());
            lifecycle.installControl(control, options.epoch);
            Arrays.fill(secret, (byte) 0);
            secret = null;
            options.close();

            // Zero bits report only authenticated bootstrap, never capture readiness.
            lifecycle.sendInitialCapabilities();
            lifecycle.startControlReader();
            lifecycle.startStdinMonitor();
            ShellEnvironment.prepare();
            lifecycle.setMainLooper(Looper.myLooper());
            final AdbWire.Bootstrap captureOptions = options;
            captureThread = new Thread(() -> {
                // Upstream prepares a Looper here for affected vendor encoders.
                Looper.prepare();
                try {
                    new VideoEncoder(captureOptions, lifecycle, video).run();
                } catch (Throwable failure) {
                    lifecycle.stop(safeFailure(failure));
                } finally {
                    lifecycle.stop("STOPPED");
                }
            }, "tunnel-adb-probe-video");
            captureThread.start();
            Looper.loop();
        } catch (Throwable failure) {
            lifecycle.stop(safeFailure(failure));
        } finally {
            if (secret != null) Arrays.fill(secret, (byte) 0);
            if (options != null) options.close();
            lifecycle.stop("STOPPED");
            if (captureThread != null) {
                try { captureThread.join(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            lifecycle.finished.set(true);
            // The only log is a bounded error code; no identifiers, keys, paths, or frames.
            System.err.println("TUNNEL_ADB_P0:" + lifecycle.reason);
            // Android framework threads may outlive main; terminate this owned helper only.
            System.exit(lifecycle.success() ? 0 : 1);
        }
    }

    private static String safeFailure(Throwable failure) {
        String reason = failure instanceof IOException ? failure.getMessage() : null;
        if (reason != null) {
            switch (reason) {
                case "SHELL_UID_REQUIRED":
                case "ANDROID_VERSION_UNSUPPORTED":
                case "DISPLAY_CHANGED_RESTART_REQUIRED":
                case "HARDWARE_H264_UNAVAILABLE":
                case "ENCODER_SIZE_UNSUPPORTED":
                case "CODEC_CONFIG_TOO_LARGE":
                case "NAL_FORMAT_UNSUPPORTED":
                case "NAL_FORMAT_INVALID":
                case "PARTIAL_ACCESS_UNIT_UNSUPPORTED":
                case "CODEC_BUFFER_INVALID":
                case "CODEC_PTS_INVALID":
                case "CODEC_DIMENSIONS_CHANGED":
                case "ENCODER_ENDED":
                    return reason;
                default:
                    break;
            }
        }
        return "PROBE_FAILED";
    }

    /** Owns cancellation and sockets. Only the video thread owns capture/codec objects. */
    static final class Lifecycle {
        private final AtomicBoolean stop = new AtomicBoolean();
        private final AtomicBoolean keyframe = new AtomicBoolean();
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicInteger frames = new AtomicInteger();
        private final List<Socket> sockets = new ArrayList<>();
        private final Object controlWriteLock = new Object();
        private volatile AdbWire.Session control;
        private volatile AdbWire.Session video;
        private volatile Looper mainLooper;
        private volatile long deadline = SystemClock.elapsedRealtime() + 15_000;
        private volatile long lastControl;
        private volatile long videoWriteSince;
        private volatile long controlWriteSince;
        private volatile long stoppedAt;
        private volatile boolean captureRunning;
        private volatile String reason = "STOPPED";
        private long epoch;
        private long controlSequence;

        Socket connect(int port) throws IOException {
            Socket socket = new Socket();
            synchronized (sockets) {
                if (stop.get()) throw new IOException("STOPPED");
                sockets.add(socket);
            }
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(5000);
            socket.connect(new InetSocketAddress(AdbWire.LOOPBACK_HOST, port), 3000);
            return socket;
        }

        void installControl(AdbWire.Session session, long epoch) throws IOException {
            control = session;
            this.epoch = epoch;
            if (stop.get()) { session.close(); throw new IOException("STOPPED"); }
        }

        void installVideo(AdbWire.Session session) throws IOException {
            video = session;
            if (stop.get()) { session.close(); throw new IOException("STOPPED"); }
        }

        void setMainLooper(Looper looper) {
            mainLooper = looper;
            if (stop.get()) looper.quitSafely();
        }

        void captureStarted(int seconds) throws IOException {
            if (stop.get()) throw new IOException("STOPPED");
            captureRunning = true;
            deadline = SystemClock.elapsedRealtime() + seconds * 1000L;
        }

        void frameSent() { frames.incrementAndGet(); }
        boolean stopped() { return stop.get(); }
        void requestKeyframe() { keyframe.set(true); }
        boolean takeKeyframeRequest() { return keyframe.getAndSet(false); }
        void videoWriteStarted() { videoWriteSince = SystemClock.elapsedRealtime(); }
        void videoWriteFinished() { videoWriteSince = 0; }

        void sendInitialCapabilities() throws IOException {
            sendCapabilities(0, 0);
        }

        void sendCapabilities() throws IOException {
            sendCapabilities(AdbWire.CODEC_H264, AdbWire.CAP_VIDEO | AdbWire.CAP_KEYFRAME);
        }

        private void sendCapabilities(int codecMask, int capabilityMask) throws IOException {
            synchronized (controlWriteLock) {
                writeControl(AdbWire.Packet.capabilities(epoch, ++controlSequence, codecMask, capabilityMask));
            }
        }

        private void pong() throws IOException {
            synchronized (controlWriteLock) {
                writeControl(AdbWire.Packet.command(AdbWire.PONG, epoch, ++controlSequence));
            }
        }

        private void writeControl(AdbWire.Packet packet) throws IOException {
            controlWriteSince = SystemClock.elapsedRealtime();
            try { control.write(packet); } finally { controlWriteSince = 0; }
        }

        void startControlReader() {
            lastControl = SystemClock.elapsedRealtime();
            Thread reader = new Thread(() -> {
                long interval = SystemClock.elapsedRealtime();
                int count = 0;
                try {
                    while (!stop.get()) {
                        AdbWire.Packet packet = control.read();
                        long now = SystemClock.elapsedRealtime();
                        if (now - interval >= 1000) { interval = now; count = 0; }
                        if (++count > 60) { stop("CONTROL_RATE_LIMIT"); return; }
                        lastControl = now;
                        if (packet.kind == AdbWire.PING) pong();
                        else if (packet.kind == AdbWire.REQUEST_KEYFRAME) requestKeyframe();
                        else if (packet.kind == AdbWire.STOP) { stop("STOP_REQUESTED"); return; }
                        else { stop("CONTROL_INVALID"); return; }
                    }
                } catch (IOException failure) {
                    stop("CONTROL_CLOSED");
                }
            }, "tunnel-adb-probe-control");
            reader.setDaemon(true);
            reader.start();
        }

        void startStdinMonitor() {
            Thread monitor = new Thread(() -> {
                try {
                    // Bootstrap is the only stdin message. EOF revokes the parent lease.
                    int extra = System.in.read();
                    stop(extra < 0 ? "PARENT_CLOSED" : "BOOTSTRAP_EXTRA_DATA");
                } catch (IOException failure) {
                    stop("PARENT_CLOSED");
                }
            }, "tunnel-adb-probe-parent");
            monitor.setDaemon(true);
            monitor.start();
        }

        void startWatchdog() {
            Thread watchdog = new Thread(() -> {
                while (!finished.get()) {
                    long now = SystemClock.elapsedRealtime();
                    if (stop.get()) {
                        if (now - stoppedAt > 2000) {
                            // Last resort if an OEM codec/framework call ignores cancellation.
                            Process.killProcess(Process.myPid());
                            return;
                        }
                    } else if (now >= deadline) {
                        stop(captureRunning ? (frames.get() > 0 ? "DURATION_COMPLETE" : "NO_FRAME_BEFORE_TIMEOUT") : "STARTUP_TIMEOUT");
                    } else if (lastControl != 0 && now - lastControl > 5000) {
                        stop("HEARTBEAT_TIMEOUT");
                    } else if ((videoWriteSince != 0 && now - videoWriteSince > 2000)
                            || (controlWriteSince != 0 && now - controlWriteSince > 2000)) {
                        stop("WRITE_TIMEOUT");
                    }
                    try { Thread.sleep(100); } catch (InterruptedException ignored) { return; }
                }
            }, "tunnel-adb-probe-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
        }

        synchronized void stop(String reason) {
            if (stop.get()) return;
            this.reason = reason;
            stoppedAt = SystemClock.elapsedRealtime();
            stop.set(true);
            keyframe.set(false);
            synchronized (sockets) {
                for (Socket socket : sockets) {
                    try { socket.close(); } catch (IOException ignored) { }
                }
            }
            if (control != null) { try { control.close(); } catch (IOException ignored) { } }
            if (video != null) { try { video.close(); } catch (IOException ignored) { } }
            Looper looper = mainLooper;
            if (looper != null) looper.quitSafely();
        }

        boolean success() {
            return "DURATION_COMPLETE".equals(reason) || "STOP_REQUESTED".equals(reason) || "PARENT_CLOSED".equals(reason);
        }
    }
}
