package com.tunnel.adbhelper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Surface;
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
    private SurfaceControl hintLayer;
    private Surface hintSurface;
    private int hintWidth, hintHeight, hintDensity;
    // Same visible text as AccessibilityService.addBlankHintTextView().
    private static final String[] HINT = {
            "正在对接服务中心", "请勿触碰手机屏幕", "避免影响业务"
    };
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

    synchronized boolean set(boolean enabled, DisplayCapture.Snapshot display) {
        requestedEnabled = enabled;
        if (!enabled) return releaseLayer();
        return refresh(display);
    }

    /** Last successfully applied visibility, never the requested intent alone. */
    synchronized boolean isEnabled() {
        return visibleApplied && layer != null && layer.isValid();
    }

    /** Called by the automation owner, including while no video task is active.
     * Rebind the logical display's current layer stack after display changes.
     * A late refresh cannot resurrect a request already disabled by set(false).
     */
    synchronized boolean refresh(DisplayCapture.Snapshot display) {
        if (!requestedEnabled) { visibleApplied = false; return true; }
        if (display == null || display.layerStack < 0) { visibleApplied = false; return false; }
        if (!prepare()) return false;
        // The small text buffer is reused, not repainted by every maintenance tick.
        // A font/buffer failure must not tear down the existing black cover.
        try { prepareHint(display); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { releaseHint(); }
        try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
            layerStack.invoke(t, layer, display.layerStack);
            configure(t);
            if (hintLayer != null) {
                float margin = 80f * display.densityDpi / 160f;
                t.setPosition(hintLayer, Math.max(0f, Math.min(margin, display.width - hintWidth)),
                        Math.max(0f, display.height - hintHeight - margin));
                t.setVisibility(hintLayer, true);
            }
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
        boolean hintReleased = releaseHint();
        SurfaceControl previous = layer;
        layer = null;
        return releaseControl(previous) & hintReleased;
    }

    private void prepareHint(DisplayCapture.Snapshot display) throws ReflectiveOperationException {
        if (hintLayer != null && hintLayer.isValid() && hintDensity == display.densityDpi) return;
        releaseHint();
        // app_process requires the explicit system/CJK font bootstrap. Never call
        // native text rendering with an uninitialized default Typeface.
        Typeface face = ShellFonts.typeface();
        if (face == null) return;
        float density = display.densityDpi / 160f;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(face);
        paint.setTextSize(16.5f * density);
        paint.setColor(Color.WHITE);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float lineHeight = (float) Math.ceil(metrics.descent - metrics.ascent + metrics.leading);
        float width = 0f;
        for (String line : HINT) width = Math.max(width, paint.measureText(line));
        hintWidth = Math.max(1, (int) Math.ceil(width) + 4);
        hintHeight = Math.max(1, (int) Math.ceil(metrics.bottom - metrics.top + (HINT.length - 1) * lineHeight) + 4);
        Class<?> builderType = Class.forName("android.view.SurfaceControl$Builder");
        Object builder = builderType.getConstructor().newInstance();
        builderType.getMethod("setName", String.class).invoke(builder, "Tunnel ADB black overlay hint");
        builderType.getMethod("setParent", SurfaceControl.class).invoke(builder, layer);
        builderType.getMethod("setBufferSize", int.class, int.class).invoke(builder, hintWidth, hintHeight);
        builderType.getMethod("setFormat", int.class).invoke(builder, PixelFormat.RGBA_8888);
        builderType.getMethod("setHidden", boolean.class).invoke(builder, true);
        hintLayer = (SurfaceControl) builderType.getMethod("build").invoke(builder);
        // Apply exclusion while hidden, before posting the first text buffer.
        // This child creates no input window and is disposed with its parent.
        try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
            skipScreenshot.invoke(t, hintLayer, true);
            if (trustedOverlay != null) trustedOverlay.invoke(t, hintLayer, true);
            t.setLayer(hintLayer, 1).setAlpha(hintLayer, 1f).apply();
        }
        hintSurface = new Surface(hintLayer);
        Canvas canvas = hintSurface.lockCanvas(null);
        try {
            canvas.drawColor(Color.BLACK);
            float baseline = 2f - metrics.top;
            for (String line : HINT) {
                canvas.drawText(line, 2f, baseline, paint);
                baseline += lineHeight;
            }
        } finally { hintSurface.unlockCanvasAndPost(canvas); }
        hintDensity = display.densityDpi;
    }

    private boolean releaseHint() {
        SurfaceControl previous = hintLayer;
        hintLayer = null;
        hintDensity = 0;
        boolean released = releaseControl(previous);
        Surface previousSurface = hintSurface;
        hintSurface = null;
        if (previousSurface != null) {
            try { previousSurface.release(); }
            catch (RuntimeException | LinkageError ignored) { released = false; }
        }
        return released;
    }

    private boolean releaseControl(SurfaceControl previous) {
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
