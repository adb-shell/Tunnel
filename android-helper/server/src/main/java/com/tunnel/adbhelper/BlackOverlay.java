package com.tunnel.adbhelper;

import android.graphics.Rect;
import android.view.SurfaceControl;
import java.lang.reflect.Method;

/** An owned shell color layer, not display power and not an input window.
 * AOSP SKIP_SCREENSHOT excludes it from screenshots, mirroring and recording.
 * Older/modified ROMs without that API are explicitly unsupported: never replace
 * this with a secure window which would black out the controller's video too.
 */
final class BlackOverlay implements AutoCloseable {
    private SurfaceControl layer;
    private Method skipScreenshot, layerStack, color;

    boolean prepare() {
        if (layer != null) return true;
        try {
            Class<?> transaction = SurfaceControl.Transaction.class;
            skipScreenshot = transaction.getMethod("setSkipScreenshot", SurfaceControl.class, boolean.class);
            layerStack = transaction.getMethod("setLayerStack", SurfaceControl.class, int.class);
            color = transaction.getMethod("setColor", SurfaceControl.class, float[].class);
            Class<?> builderType = Class.forName("android.view.SurfaceControl$Builder");
            Object builder = builderType.getConstructor().newInstance();
            builderType.getMethod("setName", String.class).invoke(builder, "Tunnel ADB black overlay");
            builderType.getMethod("setColorLayer").invoke(builder);
            builderType.getMethod("setHidden", boolean.class).invoke(builder, true);
            layer = (SurfaceControl) builderType.getMethod("build").invoke(builder);
            if (layer == null || !layer.isValid()) throw new IllegalStateException();
            try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
                skipScreenshot.invoke(t, layer, true);
                color.invoke(t, layer, new float[]{0f, 0f, 0f});
                // Cover either orientation without racing a display-rotation callback.
                // The compositor clips the layer to the physical display bounds.
                transaction.getMethod("setWindowCrop", SurfaceControl.class, Rect.class)
                        .invoke(t, layer, new Rect(0, 0, 16384, 16384));
                t.setLayer(layer, Integer.MAX_VALUE - 1).setAlpha(layer, 1f).setVisibility(layer, false).apply();
            }
            return true;
        } catch (Exception unavailable) { close(); return false; }
    }

    boolean set(boolean enabled, int displayLayerStack) {
        if (!enabled && layer == null) return true;
        if (!prepare()) return false;
        try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
            layerStack.invoke(t, layer, displayLayerStack);
            skipScreenshot.invoke(t, layer, true);
            t.setVisibility(layer, enabled);
            t.apply();
            return true;
        } catch (Exception rejected) { close(); return false; }
    }

    @Override public void close() {
        SurfaceControl previous = layer;
        layer = null;
        if (previous == null) return;
        try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
            SurfaceControl.Transaction.class.getMethod("remove", SurfaceControl.class).invoke(t, previous);
            t.apply();
        } catch (Exception ignored) { }
        finally { previous.release(); }
    }
}
