/*
 * Adapted from scrcpy v4.1, commit 2926c06c5dc3064ae6d8db706f1a98a37cfcf3f0.
 * Copyright (C) 2018 Genymobile
 * Copyright (C) 2018-2026 Romain Vimont
 * Licensed under the Apache License, Version 2.0; see LICENSE.scrcpy.
 * Tunnel modifications: display 0 only, non-secure mirroring, bounded snapshot,
 * report display changes to the owning encoder; no secure/new-display support.
 */
package com.tunnel.adbhelper;

import android.graphics.Rect;
import android.hardware.display.VirtualDisplay;
import android.os.IBinder;
import android.view.Surface;

import java.lang.reflect.Method;

/** Version-pinned hidden API adapter. Unsupported ROMs fail instead of changing permissions. */
final class DisplayCapture implements AutoCloseable {
    static final class Snapshot {
        final int width;
        final int height;
        final int rotation;
        final int layerStack;
        final int densityDpi;

        Snapshot(int width, int height, int rotation, int layerStack, int densityDpi) {
            if (width < 1 || height < 1 || width > 16384 || height > 16384 || rotation < 0 || rotation > 3) {
                throw new IllegalArgumentException("DISPLAY_INFO_INVALID");
            }
            this.width = width;
            this.height = height;
            this.rotation = rotation;
            this.layerStack = layerStack;
            this.densityDpi = densityDpi > 0 && densityDpi <= 1280 ? densityDpi : 160;
        }

        boolean sameAs(Snapshot other) {
            return width == other.width && height == other.height && rotation == other.rotation && layerStack == other.layerStack;
        }
    }

    private final Object manager;
    private final Method getDisplayInfo;
    private VirtualDisplay virtualDisplay;
    private IBinder surfaceDisplay;
    private Class<?> surfaceControl;

    DisplayCapture() throws ReflectiveOperationException {
        Class<?> managerClass = Class.forName("android.hardware.display.DisplayManagerGlobal");
        manager = managerClass.getDeclaredMethod("getInstance").invoke(null);
        if (manager == null) {
            throw new IllegalStateException("DISPLAY_MANAGER_UNAVAILABLE");
        }
        getDisplayInfo = managerClass.getMethod("getDisplayInfo", int.class);
    }

    Snapshot snapshot() throws ReflectiveOperationException {
        Object info = getDisplayInfo.invoke(manager, 0);
        if (info == null) {
            throw new IllegalStateException("DISPLAY_UNAVAILABLE");
        }
        Class<?> type = info.getClass();
        int densityDpi = 160;
        try { densityDpi = type.getDeclaredField("logicalDensityDpi").getInt(info); }
        catch (ReflectiveOperationException unavailable) { /* Keep capture available on vendor frameworks. */ }
        return new Snapshot(type.getDeclaredField("logicalWidth").getInt(info),
                type.getDeclaredField("logicalHeight").getInt(info),
                type.getDeclaredField("rotation").getInt(info),
                type.getDeclaredField("layerStack").getInt(info), densityDpi);
    }

    void start(Surface target, Snapshot source, int width, int height) throws ReflectiveOperationException {
        if (virtualDisplay != null || surfaceDisplay != null) {
            throw new IllegalStateException("DISPLAY_ALREADY_STARTED");
        }
        try {
            // Exact mirror-display signature used by scrcpy v4.1 DisplayManager.
            // AOSP Android 14/16 builds this with AUTO_MIRROR only, not SECURE;
            // fixed-tag source evidence is recorded in PROVENANCE.md.
            Method mirror = android.hardware.display.DisplayManager.class.getMethod("createVirtualDisplay",
                    String.class, int.class, int.class, int.class, Surface.class);
            virtualDisplay = (VirtualDisplay) mirror.invoke(null, "Tunnel local ADB probe", width, height, 0, target);
            if (virtualDisplay != null) {
                return;
            }
        } catch (ReflectiveOperationException ignored) {
            // Older Android versions use the SurfaceControl route below.
        }

        surfaceControl = Class.forName("android.view.SurfaceControl");
        surfaceDisplay = (IBinder) surfaceControl.getMethod("createDisplay", String.class, boolean.class)
                .invoke(null, "Tunnel local ADB probe", false);
        if (surfaceDisplay == null) {
            throw new IllegalStateException("DISPLAY_CAPTURE_UNSUPPORTED");
        }
        // Always non-secure, including Android 11. Protected buffers are not requested.
        surfaceControl.getMethod("openTransaction").invoke(null);
        try {
            surfaceControl.getMethod("setDisplaySurface", IBinder.class, Surface.class).invoke(null, surfaceDisplay, target);
            surfaceControl.getMethod("setDisplayProjection", IBinder.class, int.class, Rect.class, Rect.class)
                    .invoke(null, surfaceDisplay, 0, new Rect(0, 0, source.width, source.height), new Rect(0, 0, width, height));
            surfaceControl.getMethod("setDisplayLayerStack", IBinder.class, int.class).invoke(null, surfaceDisplay, source.layerStack);
        } finally {
            surfaceControl.getMethod("closeTransaction").invoke(null);
        }
    }

    @Override
    public void close() {
        if (virtualDisplay != null) {
            try { virtualDisplay.release(); } catch (RuntimeException ignored) { }
            virtualDisplay = null;
        }
        if (surfaceDisplay != null) {
            try {
                surfaceControl.getMethod("destroyDisplay", IBinder.class).invoke(null, surfaceDisplay);
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
            surfaceDisplay = null;
        }
    }
}
