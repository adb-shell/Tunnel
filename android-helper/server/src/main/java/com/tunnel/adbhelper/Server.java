package com.tunnel.adbhelper;

import android.os.Build;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.graphics.Bitmap;

import com.tunnel.adb.protocol.AdbWire;
import com.tunnel.adb.protocol.AdbCommands;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Authenticated local helper; the APK owns consent, endpoint scope and remote lease. */
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
            lifecycle.task = new Lifecycle.VideoTask(options.initialMode == 3 ? 0 : 1, options.initialMode);
            lifecycle.controlOnly = options.initialMode == 3;
            lifecycle.startAutomation();
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
                try { captureThread.join(750); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            if (lifecycle.automationThread != null) {
                try { lifecycle.automationThread.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
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
        private final AtomicBoolean releaseInput = new AtomicBoolean();
        private final ArrayBlockingQueue<AdbCommands.Command> operations = new ArrayBlockingQueue<>(64);
        private final List<Socket> sockets = new ArrayList<>();
        private final Object controlWriteLock = new Object();
        private volatile AdbWire.Session control;
        private volatile AdbWire.Session video;
        private volatile Looper mainLooper;
        private volatile long deadline = SystemClock.elapsedRealtime() + 15_000;
        private volatile long lastControl;
        private volatile long videoWriteSince;
        private volatile long videoProgressAt;
        private volatile long controlWriteSince;
        private volatile long stoppedAt;
        private volatile boolean captureRunning;
        private VideoTask unavailableTask;
        private boolean controlOnly;
        private volatile int automationCapabilities;
        private volatile long operationSince;
        private volatile long frameOperationSince;
        private volatile Thread automationThread;
        private volatile Thread frameThread;
        static final class VideoTask {
            final int id, mode;
            VideoTask(int id, int mode) { this.id = id; this.mode = mode; }
        }
        private volatile VideoTask task = new VideoTask(0, 3);
        private Bitmap bitmap;
        private int bitmapTask;
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

        void captureStarted(int seconds, VideoTask expected) throws IOException {
            synchronized (this) {
                if (stop.get()) throw new IOException("STOPPED");
                if (task != expected || captureRunning) return;
                deadline = seconds == AdbWire.SESSION_DURATION ? 0 : SystemClock.elapsedRealtime() + seconds * 1000L;
                captureRunning = true;
            }
            // Never hold the lifecycle monitor across socket I/O: the watchdog
            // needs it to cancel a blocked write and close the socket.
            videoState(expected.id, 1);
        }

        void displayChanged() { releaseInput.set(true); }
        int captureMode() { return task.mode; }
        VideoTask videoTask() { return task; }
        synchronized Bitmap takeBitmap(VideoTask expected) {
            if (bitmapTask != expected.id) return null;
            Bitmap value = bitmap; bitmap = null; return value;
        }
        private synchronized void offerBitmap(Bitmap value, VideoTask expected) {
            if (task != expected || (stop.get() && value != null)) { if (value != null) value.recycle(); return; }
            if (bitmap != null) bitmap.recycle(); bitmap = value; bitmapTask = expected.id;
        }
        private synchronized boolean selectVideo(int id, int mode) {
            // A lost reply may cause the APK to resend the exact command. ACK
            // that identity without disposing the bitmap/codec or changing intent.
            if (id == task.id) return mode == task.mode;
            if (id < task.id) return false;
            if (bitmap != null) bitmap.recycle(); bitmap = null;
            task = new VideoTask(id, mode); captureRunning = false;
            keyframe.set(true);
            return true;
        }
        void videoTaskInterrupted(VideoTask expected) {
            synchronized (this) {
                if (task != expected || expected.mode == 3 || unavailableTask == expected) return;
                // A transient provider failure is not a user stop. Keep this task's
                // identity/intent and let its sole worker recover without spawning.
                unavailableTask = expected;
                captureRunning = false;
                if (bitmap != null) bitmap.recycle();
                bitmap = null;
            }
            try { videoState(expected.id, 2); sendCapabilities(); }
            catch (IOException closed) { stop("CONTROL_CLOSED"); }
        }
        private void videoState(int id, int state) throws IOException {
            if (id <= 0) return;
            synchronized (controlWriteLock) {
                writeControl(AdbWire.Packet.of(AdbWire.VIDEO_STATE, 0, epoch, 0,
                        ++controlSequence, 0, 0, 0, ByteBuffer.allocate(8).putInt(id).putInt(state).array()));
            }
        }

        void frameSent(VideoTask expected) throws IOException {
            boolean recovered;
            synchronized (this) {
                if (task != expected) return;
                frames.incrementAndGet();
                recovered = unavailableTask == expected;
                unavailableTask = null;
                captureRunning = true;
            }
            if (recovered) { videoState(expected.id, 1); sendCapabilities(); }
        }
        void videoProgress() { videoProgressAt = SystemClock.elapsedRealtime(); }
        void videoIdle() { videoProgressAt = 0; }
        boolean stopped() { return stop.get(); }
        void requestKeyframe() { keyframe.set(true); }
        boolean takeKeyframeRequest() { return keyframe.getAndSet(false); }
        void videoWriteStarted() { videoWriteSince = SystemClock.elapsedRealtime(); }
        void videoWriteFinished() { videoWriteSince = 0; }

        void sendInitialCapabilities() throws IOException {
            sendCapabilities(0, 0);
        }

        void sendCapabilities() throws IOException {
            sendCapabilities(captureRunning ? AdbWire.CODEC_H264 : 0,
                    (captureRunning ? AdbWire.CAP_VIDEO | AdbWire.CAP_KEYFRAME : 0) | automationCapabilities);
        }

        private void result(AdbCommands.Result result) throws IOException {
            synchronized (controlWriteLock) {
                writeControl(AdbWire.Packet.of(AdbWire.RESULT, 0, epoch, 0, ++controlSequence, 0, 0, 0, result.encode()));
            }
        }

        void startAutomation() {
            automationThread = new Thread(() -> {
                ShellAutomation automation = new ShellAutomation();
                try {
                    operationSince = SystemClock.elapsedRealtime();
                    try { automation.connect(); } catch (Exception unavailable) { automation.close(); }
                    operationSince = 0;
                    automationCapabilities = automation.capabilities();
                    // A control-only helper must not create a display/codec or
                    // expire merely because it intentionally produces no video.
                    // Heartbeat, parent EOF and operation watchdogs still apply.
                    if (controlOnly && automationCapabilities != 0) deadline = 0;
                    sendCapabilities();
                    startFrameWorker(automation);
                    long nextEffectsCheck = 0;
                    int lastEffects = -1;
                    while (!stop.get()) {
                        if (releaseInput.getAndSet(false)) {
                            operationSince = SystemClock.elapsedRealtime();
                            try { automation.releaseInput(); }
                            finally { operationSince = 0; }
                        }
                        AdbCommands.Command command = operations.poll(40, TimeUnit.MILLISECONDS);
                        if (command != null) {
                            operationSince = SystemClock.elapsedRealtime();
                            AdbCommands.Result reply;
                            if (command.operation == AdbCommands.VIDEO_TASK) {
                                boolean accepted = selectVideo(command.b, command.a);
                                reply = new AdbCommands.Result(command.id,
                                        accepted ? AdbCommands.OK : AdbCommands.REJECTED, new byte[0]);
                                if (accepted && command.a == 3) videoState(command.b, 0);
                            } else if (command.operation == AdbCommands.CAPTURE_MODE) {
                                // Video tasks require a new explicit identity.
                                reply = new AdbCommands.Result(command.id, AdbCommands.REJECTED, new byte[0]);
                            } else reply = automation.execute(command);
                            operationSince = 0;
                            result(reply);
                        }
                        if (SystemClock.elapsedRealtime() >= nextEffectsCheck) {
                            operationSince = SystemClock.elapsedRealtime();
                            try { automation.maintainEffects(); }
                            finally { operationSince = 0; }
                            nextEffectsCheck = SystemClock.elapsedRealtime() + 500;
                        }
                        int next = automation.capabilities();
                        if (next != automationCapabilities) { automationCapabilities = next; sendCapabilities(); }
                        int effects = automation.effectState();
                        if (effects != lastEffects) {
                            synchronized (controlWriteLock) {
                                writeControl(AdbWire.Packet.of(AdbWire.EFFECTS_STATE, 0, epoch, 0,
                                        ++controlSequence, 0, 0, 0, ByteBuffer.allocate(4).putInt(effects).array()));
                            }
                            lastEffects = effects;
                        }
                    }
                } catch (Exception failure) {
                    if (!stop.get()) stop("OPERATION_CHANNEL_FAILED");
                } finally {
                    // UiAutomation is shared with the frame worker. A stuck OEM
                    // Binder call must not race disconnect/dispose on this thread.
                    stop("OPERATION_CHANNEL_ENDED");
                    if (awaitFrameWorker()) automation.close();
                    // If the worker ignored interruption, process exit/watchdog
                    // reclaims the connection and FDs instead of unsafe close().
                    offerBitmap(null, task);
                }
            }, "tunnel-adb-operations");
            automationThread.setDaemon(true);
            automationThread.start();
        }

        private void startFrameWorker(ShellAutomation automation) {
            frameThread = new Thread(() -> {
                VideoTask previous = null;
                long nextFrame = 0;
                int consecutiveFailures = 0;
                try {
                    while (!stop.get()) {
                        VideoTask current = task;
                        if (current != previous) { previous = current; nextFrame = 0; consecutiveFailures = 0; }
                        if ((current.mode != 1 && current.mode != 2 && current.mode != 4)
                                || SystemClock.elapsedRealtime() < nextFrame) {
                            Thread.sleep(40);
                            continue;
                        }
                        boolean failed = false;
                        frameOperationSince = SystemClock.elapsedRealtime();
                        try {
                            offerBitmap(automation.frame(current.mode), current);
                            consecutiveFailures = 0;
                        } catch (Exception unavailable) {
                            // Rotation, animation and a temporarily unavailable
                            // screenshot may fail one call. Retry the same provider,
                            // not a different source, until explicit replacement/off.
                            consecutiveFailures = Math.min(5, consecutiveFailures + 1);
                            failed = true;
                        } finally { frameOperationSince = 0; }
                        if (failed && !stop.get()) videoTaskInterrupted(current);
                        nextFrame = SystemClock.elapsedRealtime() + (failed
                                ? Math.min(1000, 100L << consecutiveFailures)
                                : current.mode == 2 ? 500 : 200);
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    frameOperationSince = 0;
                    if (!stop.get()) stop("FRAME_WORKER_ENDED");
                }
            }, "tunnel-adb-frame-acquisition");
            frameThread.setDaemon(true);
            frameThread.start();
        }

        private boolean awaitFrameWorker() {
            Thread worker = frameThread;
            if (worker == null) return true;
            worker.interrupt();
            // stop() interrupts the operation owner as well. Clear that flag
            // temporarily so shutdown actually gets its bounded join window.
            boolean interrupted = Thread.interrupted();
            long until = SystemClock.elapsedRealtime() + 750;
            while (worker.isAlive()) {
                long remaining = until - SystemClock.elapsedRealtime();
                if (remaining <= 0) break;
                try { worker.join(remaining); }
                catch (InterruptedException cancellation) { interrupted = true; }
            }
            if (interrupted) Thread.currentThread().interrupt();
            return !worker.isAlive();
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
                long lastOperationId = 0;
                try {
                    while (!stop.get()) {
                        AdbWire.Packet packet = control.read();
                        long now = SystemClock.elapsedRealtime();
                        if (now - interval >= 1000) { interval = now; count = 0; }
                        if (++count > 240) { stop("CONTROL_RATE_LIMIT"); return; }
                        lastControl = now;
                        if (packet.kind == AdbWire.PING) pong();
                        else if (packet.kind == AdbWire.REQUEST_KEYFRAME) requestKeyframe();
                        else if (packet.kind == AdbWire.STOP) { stop("STOP_REQUESTED"); return; }
                        else if (packet.kind == AdbWire.OPERATION) {
                            AdbCommands.Command command = AdbCommands.Command.decode(packet.payloadCopy());
                            if (command.id <= lastOperationId) { stop("OPERATION_REPLAY"); return; }
                            lastOperationId = command.id;
                            if (!operations.offer(command)) result(new AdbCommands.Result(command.id, AdbCommands.BUSY, new byte[0]));
                        }
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
                    // Read each volatile start marker once: a concurrent reset
                    // to zero between the guard and subtraction is not a timeout.
                    long controlOperation = operationSince;
                    long frameOperation = frameOperationSince;
                    long codecProgress = videoProgressAt;
                    long controlWrite = controlWriteSince;
                    long videoWrite = videoWriteSince;
                    if (stop.get()) {
                        if (now - stoppedAt > 2000) {
                            // Last resort if an OEM codec/framework call ignores cancellation.
                            Process.killProcess(Process.myPid());
                            return;
                        }
                    } else if (deadline != 0 && now >= deadline) {
                        stop(captureRunning ? (frames.get() > 0 ? "DURATION_COMPLETE" : "NO_FRAME_BEFORE_TIMEOUT") : "STARTUP_TIMEOUT");
                    } else if (lastControl != 0 && now - lastControl > 5000) {
                        stop("HEARTBEAT_TIMEOUT");
                    } else if (controlOperation != 0 && now - controlOperation > 10000) {
                        stop("OPERATION_TIMEOUT");
                    } else if (frameOperation != 0 && now - frameOperation > 10000) {
                        stop("FRAME_WORKER_BLOCKED");
                    } else if (codecProgress != 0 && now - codecProgress > 15000) {
                        // An OEM codec that never returns cannot be interrupted
                        // safely inside Java. Recreate the helper, not the daemon
                        // or the remote authorization, so a new request can run.
                        stop("CODEC_WORKER_BLOCKED");
                    } else if (controlWrite != 0 && now - controlWrite > 2000) {
                        stop("CONTROL_WRITE_TIMEOUT");
                    } else if (videoWrite != 0 && now - videoWrite > 5000) {
                        // A broken local socket is distinct from an encoder task
                        // failure. A partially written authenticated record cannot
                        // be resumed; the APK must recreate this helper, keeping
                        // its separate connection authorization intact.
                        stop("VIDEO_IPC_BLOCKED");
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
            releaseInput.set(true);
            operations.clear();
            Thread operationsOwner = automationThread;
            if (operationsOwner != null) operationsOwner.interrupt();
            Thread frameOwner = frameThread;
            if (frameOwner != null) frameOwner.interrupt();
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
