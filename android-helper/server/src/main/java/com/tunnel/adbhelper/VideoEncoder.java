/*
 * Capture/codec lifecycle and format options adapted from scrcpy v4.1
 * SurfaceEncoder, commit 2926c06c5dc3064ae6d8db706f1a98a37cfcf3f0.
 * Copyright (C) 2018 Genymobile
 * Copyright (C) 2018-2026 Romain Vimont
 * Licensed under the Apache License, Version 2.0; see LICENSE.scrcpy.
 * Tunnel modifications: one bounded H264 probe, authenticated owned packets,
 * explicit keyframe requests, non-secure capture, stop on display changes.
 */
package com.tunnel.adbhelper;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Surface;

import com.tunnel.adb.protocol.AdbWire;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

final class VideoEncoder {
    private static final String MIME = "video/avc";
    private final AdbWire.Bootstrap options;
    private final Server.Lifecycle lifecycle;
    private final AdbWire.Session output;
    private long sequence;
    private long configRevision;
    private long lastPts;
    private byte[] sentConfig;
    private boolean waitForKey = true;

    VideoEncoder(AdbWire.Bootstrap options, Server.Lifecycle lifecycle, AdbWire.Session output) {
        this.options = options;
        this.lifecycle = lifecycle;
        this.output = output;
    }

    void run() throws Exception {
        DisplayCapture capture = null;
        MediaCodec encoder = null;
        Surface surface = null;
        boolean started = false;
        try {
            ensureRunning();
            capture = new DisplayCapture();
            DisplayCapture.Snapshot snapshot = capture.snapshot();
            MediaCodecInfo selected = findEncoder();
            MediaCodecInfo.VideoCapabilities caps = selected.getCapabilitiesForType(MIME).getVideoCapabilities();
            int alignment = Math.max(2, Math.max(caps.getWidthAlignment(), caps.getHeightAlignment()));
            double scale = Math.min(1.0, (double) options.maxSize / Math.max(snapshot.width, snapshot.height));
            int width = (int) (snapshot.width * scale) / alignment * alignment;
            int height = (int) (snapshot.height * scale) / alignment * alignment;
            if (width <= 0 || height <= 0 || !caps.areSizeAndRateSupported(width, height, options.fps)) {
                throw new IOException("ENCODER_SIZE_UNSUPPORTED");
            }
            MediaFormat format = MediaFormat.createVideoFormat(MIME, width, height);
            format.setInteger(MediaFormat.KEY_BIT_RATE, options.bitrate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, options.fps);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
            format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000);
            format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED);
            format.setInteger(MediaFormat.KEY_PRIORITY, 0);
            format.setInteger(MediaFormat.KEY_LATENCY, 1);
            format.setFloat("max-fps-to-encoder", options.fps);
            ensureRunning();
            encoder = MediaCodec.createByCodecName(selected.getName());
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = encoder.createInputSurface();
            ensureRunning();
            capture.start(surface, snapshot, width, height);
            ensureRunning();
            encoder.start();
            started = true;
            lifecycle.captureStarted(options.durationSeconds);
            lifecycle.sendCapabilities();
            stream(encoder, capture, snapshot, width, height);
        } finally {
            if (capture != null) capture.close();
            if (encoder != null) {
                if (started) {
                    try { encoder.stop(); } catch (RuntimeException ignored) { }
                }
                try { encoder.release(); } catch (RuntimeException ignored) { }
            }
            if (surface != null) surface.release();
            if (sentConfig != null) Arrays.fill(sentConfig, (byte) 0);
        }
    }

    private void stream(MediaCodec encoder, DisplayCapture capture, DisplayCapture.Snapshot initial, int width, int height)
            throws Exception {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        H264AnnexB configuration = new H264AnnexB();
        long nextDisplayCheck = 0;
        long lastKeyRequest = 0;
        while (!lifecycle.stopped()) {
            long now = SystemClock.elapsedRealtime();
            if (now >= nextDisplayCheck) {
                if (!initial.sameAs(capture.snapshot())) throw new IOException("DISPLAY_CHANGED_RESTART_REQUIRED");
                nextDisplayCheck = now + 250;
            }
            if (now - lastKeyRequest >= 500 && lifecycle.takeKeyframeRequest()) {
                Bundle bundle = new Bundle();
                bundle.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
                encoder.setParameters(bundle);
                lastKeyRequest = now;
            }
            int index = encoder.dequeueOutputBuffer(info, 50_000);
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat outputFormat = encoder.getOutputFormat();
                if (outputFormat.getInteger(MediaFormat.KEY_WIDTH) != width
                        || outputFormat.getInteger(MediaFormat.KEY_HEIGHT) != height) {
                    throw new IOException("CODEC_DIMENSIONS_CHANGED");
                }
                for (String key : new String[]{"csd-0", "csd-1"}) {
                    ByteBuffer csd = outputFormat.getByteBuffer(key);
                    if (csd != null && csd.hasRemaining()) configuration.readConfiguration(copy(csd, AdbWire.MAX_CONFIG));
                }
                publishConfiguration(configuration, width, height);
                continue;
            }
            if (index < 0) continue;
            try {
                if ((info.flags & MediaCodec.BUFFER_FLAG_PARTIAL_FRAME) != 0) {
                    throw new IOException("PARTIAL_ACCESS_UNIT_UNSUPPORTED");
                }
                if (info.size > 0) {
                    ByteBuffer buffer = encoder.getOutputBuffer(index);
                    if (buffer == null || info.offset < 0 || info.size > AdbWire.MAX_PAYLOAD
                            || (long) info.offset + info.size > buffer.capacity()) {
                        throw new IOException("CODEC_BUFFER_INVALID");
                    }
                    ByteBuffer view = buffer.duplicate();
                    view.position(info.offset);
                    view.limit(info.offset + info.size);
                    byte[] bytes = copy(view, AdbWire.MAX_PAYLOAD);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        configuration.readConfiguration(bytes);
                        publishConfiguration(configuration, width, height);
                    } else {
                        boolean key = H264AnnexB.isIdr(bytes);
                        if (configRevision == 0 || (waitForKey && !key)) {
                            lifecycle.requestKeyframe();
                        } else {
                            if (info.presentationTimeUs < lastPts || info.presentationTimeUs < 0) {
                                throw new IOException("CODEC_PTS_INVALID");
                            }
                            send(AdbWire.Packet.of(AdbWire.VIDEO_FRAME, key ? AdbWire.FLAG_KEY_FRAME : 0,
                                    options.epoch, configRevision, ++sequence, info.presentationTimeUs, width, height, bytes));
                            lifecycle.frameSent();
                            lastPts = info.presentationTimeUs;
                            waitForKey = false;
                        }
                    }
                    Arrays.fill(bytes, (byte) 0);
                }
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 && !lifecycle.stopped()) {
                    throw new IOException("ENCODER_ENDED");
                }
            } finally {
                encoder.releaseOutputBuffer(index, false);
            }
        }
    }

    private void publishConfiguration(H264AnnexB configuration, int width, int height) throws IOException {
        byte[] next = configuration.configuration();
        if (next != null && !Arrays.equals(next, sentConfig)) {
            send(AdbWire.Packet.of(AdbWire.VIDEO_CONFIG, 0, options.epoch, ++configRevision, ++sequence,
                    0, width, height, next));
            if (sentConfig != null) Arrays.fill(sentConfig, (byte) 0);
            sentConfig = next;
            waitForKey = true;
            lifecycle.requestKeyframe();
        }
    }

    private void send(AdbWire.Packet packet) throws IOException {
        ensureRunning();
        lifecycle.videoWriteStarted();
        try { output.write(packet); } finally { lifecycle.videoWriteFinished(); }
    }

    private void ensureRunning() throws IOException {
        if (lifecycle.stopped()) throw new IOException("STOPPED");
    }

    private static byte[] copy(ByteBuffer source, int limit) throws IOException {
        ByteBuffer view = source.duplicate();
        if (view.remaining() <= 0 || view.remaining() > limit) throw new IOException("CODEC_BUFFER_INVALID");
        byte[] result = new byte[view.remaining()];
        view.get(result);
        return result;
    }

    private static MediaCodecInfo findEncoder() throws IOException {
        // Hardware-only diagnostic: no false hardware claim or silent software fallback.
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (!info.isEncoder() || !info.isHardwareAccelerated()) continue;
            for (String type : info.getSupportedTypes()) {
                if (!MIME.equalsIgnoreCase(type)) continue;
                for (int color : info.getCapabilitiesForType(type).colorFormats) {
                    if (color == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) return info;
                }
            }
        }
        throw new IOException("HARDWARE_H264_UNAVAILABLE");
    }
}
