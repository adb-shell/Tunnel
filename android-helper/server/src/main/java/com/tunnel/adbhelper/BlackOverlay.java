package com.tunnel.adbhelper;

import android.graphics.Rect;
import android.os.Build;
import android.view.SurfaceControl;
import java.lang.reflect.Method;

/** An owned shell color layer, not display power and not an input window.
 * AOSP SKIP_SCREENSHOT excludes it from screenshots, mirroring and recording.
 * Older/modified ROMs without that API are explicitly unsupported: never replace
 * this with a secure window which would black out the controller's video too.
 */
final class BlackOverlay implements AutoCloseable {
    // This belongs to the user's black-screen request, not a video task or codec.
    // A compositor layer may need replacement without revoking that request.
    private boolean requestedEnabled;
    private boolean visibleApplied;
    private SurfaceControl layer;
    private Method skipScreenshot, trustedOverlay, layerStack, color, crop, remove;
    private static final Rect COVERAGE = new Rect(0, 0, 16384, 16384);

    /** Probe/create a hidden layer. Capability discovery must never black a phone. */
    synchronized boolean prepare() {
        if (layer != null && layer.isValid()) return true;
        releaseLayer();
        try {
            Class<?> transaction = SurfaceControl.Transaction.class;
            skipScreenshot = transaction.getMethod("setSkipScreenshot", SurfaceControl.class, boolean.class);
            trustedOverlay = null;
            try {
                // Android 12+ treats even a layer without an input channel as
                // an occluder. SKIP_SCREENSHOT only affects capture, not input:
                // an opaque, untrusted color layer can therefore reject touch
                // injection into every window below it. This shell-owned layer
                // must be trusted before it becomes visible. The boolean API
                // is retained by AOSP Android 12 through 16 (including 16's
                // newer int overload) and requires ACCESS_SURFACE_FLINGER.
                trustedOverlay = transaction.getMethod("setTrustedOverlay", SurfaceControl.class, boolean.class);
            } catch (NoSuchMethodException unavailable) {
                // AOSP 11 predates input-occlusion blocking. On newer vendor
                // frameworks, fail instead of showing a touch-blocking cover.
                if (Build.VERSION.SDK_INT >= 31) throw unavailable;
            }
            layerStack = transaction.getMethod("setLayerStack", SurfaceControl.class, int.class);
            color = transaction.getMethod("setColor", SurfaceControl.class, float[].class);
            remove = transaction.getMethod("remove", SurfaceControl.class);
            try {
                // Public crop API on recent Android; retain the old spelling for
                // Android 11/vendor frameworks. Neither path uses FLAG_SECURE.
                crop = transaction.getMethod("setCrop", SurfaceControl.class, Rect.class);
            } catch (NoSuchMethodException olderFramework) {
                crop = transaction.getMethod("setWindowCrop", SurfaceControl.class, Rect.class);
            }
            Class<?> builderType = Class.forName("android.view.SurfaceControl$Builder");
            Object builder = builderType.getConstructor().newInstance();
            builderType.getMethod("setName", String.class).invoke(builder, "Tunnel ADB black overlay");
            builderType.getMethod("setColorLayer").invoke(builder);
            builderType.getMethod("setHidden", boolean.class).invoke(builder, true);
            layer = (SurfaceControl) builderType.getMethod("build").invoke(builder);
            if (layer == null || !layer.isValid()) throw new IllegalStateException();
            try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
                // Cover either orientation without racing a display-rotation callback.
                // The compositor clips the layer to the physical display bounds.
                configure(t);
                t.setVisibility(layer, false).apply();
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            releaseLayer();
            return false;
        }
    }

    synchronized boolean set(boolean enabled, int displayLayerStack) {
        requestedEnabled = enabled;
        if (!enabled) return releaseLayer();
        return refresh(displayLayerStack);
    }

    /** Last successfully applied visibility, never the requested intent alone. */
    synchronized boolean isEnabled() {
        return visibleApplied && layer != null && layer.isValid();
    }

    /** Called by the automation owner, including while no video task is active.
     * Rebind the logical display's current layer stack after display changes.
     * A late refresh cannot resurrect a request already disabled by set(false).
     */
    synchronized boolean refresh(int displayLayerStack) {
        if (!requestedEnabled) { visibleApplied = false; return true; }
        if (displayLayerStack < 0) { visibleApplied = false; return false; }
        if (!prepare()) return false;
        try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
            layerStack.invoke(t, layer, displayLayerStack);
            configure(t);
            t.setVisibility(layer, true);
            t.apply();
            visibleApplied = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError rejected) {
            // The next maintenance pass may recreate a lost/invalid layer. Keep
            // request state; never substitute display power or a secure window.
            releaseLayer();
            return false;
        }
    }

    private void configure(SurfaceControl.Transaction t) throws ReflectiveOperationException {
        // Exclusion and black fill are in the same transaction as visibility.
        // No transient unexcluded frame is exposed during creation/recreation.
        skipScreenshot.invoke(t, layer, true);
        // Only this owned visual layer is exempt from occlusion checks. Never
        // disable the system's untrusted-touch protection or change app flags.
        if (trustedOverlay != null) trustedOverlay.invoke(t, layer, true);
        color.invoke(t, layer, new float[]{0f, 0f, 0f});
        crop.invoke(t, layer, COVERAGE);
        t.setPosition(layer, 0f, 0f).setLayer(layer, Integer.MAX_VALUE - 1).setAlpha(layer, 1f);
    }

    private boolean releaseLayer() {
        visibleApplied = false;
        SurfaceControl previous = layer;
        layer = null;
        if (previous == null) return true;
        boolean released = true;
        try {
            if (previous.isValid()) {
                try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
                    // Hide before removing, including cleanup after partial setup.
                    t.setVisibility(previous, false);
                    Method removal = remove != null ? remove : SurfaceControl.Transaction.class
                            .getMethod("remove", SurfaceControl.class);
                    removal.invoke(t, previous);
                    t.apply();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            released = false;
        } finally {
            try { previous.release(); }
            catch (RuntimeException | LinkageError ignored) { released = false; }
        }
        return released;
    }

    @Override public synchronized void close() {
        requestedEnabled = false;
        releaseLayer();
    }
}
