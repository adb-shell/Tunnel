package com.tunnel.adbhelper;

import android.app.UiAutomation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityEvent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.tunnel.adb.protocol.AdbCommands;
import com.tunnel.adb.protocol.AdbWire;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Owns one UiAutomation connection, never suppresses the user's accessibility services.
 * Hidden constructor/connect signatures checked against AOSP android-16.0.0_r1 UiAutomation.java.
 * No executeShellCommand, adoptShellPermissionIdentity, settings writes, or package operations.
 */
final class ShellAutomation implements AutoCloseable {
    private UiAutomation automation;
    private DisplayCapture display;
    private final DisplayPower power = new DisplayPower();
    private final BlackOverlay overlay = new BlackOverlay();
    private final InputInjector injector = new InputInjector();
    private final HierarchyFrame.Provider hierarchy = new HierarchyFrame.Provider();
    private long touchDown;
    private float touchX, touchY;
    private final Map<Integer, Long> pressedKeys = new HashMap<>();
    // Frame acquisition and control/effect maintenance use separate workers.
    private final AtomicInteger capabilities = new AtomicInteger();

    void connect() throws Exception {
        Class<?> connectionType = Class.forName("android.app.IUiAutomationConnection");
        Object connection = Class.forName("android.app.UiAutomationConnection").getDeclaredConstructor().newInstance();
        automation = (UiAutomation) UiAutomation.class.getConstructor(Looper.class, connectionType)
                .newInstance(Looper.getMainLooper(), connection);
        UiAutomation.class.getMethod("connect", int.class).invoke(automation,
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        AccessibilityServiceInfo info = automation.getServiceInfo();
        if (info == null) throw new IllegalStateException("AUTOMATION_UNAVAILABLE");
        // These are this UiAutomation connection's flags only. Never alter the
        // user's AccessibilityService configuration or suppress their services.
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.packageNames = null;
        info.notificationTimeout = 50;
        try { automation.setServiceInfo(info); }
        catch (RuntimeException unsupportedFlags) { /* Keep shell input and active-root fallback available. */ }
        automation.setOnAccessibilityEventListener(event -> {
            try { hierarchy.contentChanged(event); }
            finally { event.recycle(); }
        });
        display = new DisplayCapture();
        display.snapshot();
        capabilities.set(AdbWire.CAP_INPUT);
        if (power.probe()) addCapabilities(AdbWire.CAP_DISPLAY);
        if (overlay.prepare()) addCapabilities(AdbWire.CAP_OVERLAY);
    }

    int capabilities() { return capabilities.get(); }
    private void addCapabilities(int bits) { capabilities.updateAndGet(current -> current | bits); }
    private void removeCapabilities(int bits) { capabilities.updateAndGet(current -> current & ~bits); }
    void revokeMode(int mode) { removeCapabilities(mode == 1 ? AdbWire.CAP_SCREENSHOT : mode == 2 ? AdbWire.CAP_TREE : 0); }

    /** Refresh only our own desired effect; transient display changes are retryable. */
    void maintainEffects() {
        if (automation == null || display == null) return;
        try { overlay.refresh(display.snapshot().layerStack); }
        catch (Exception displayTransition) { /* Next periodic refresh retries without changing intent/capabilities. */ }
    }

    int effectState() { return overlay.isEnabled() ? 1 : 0; }

    void frameTaskChanged() { hierarchy.invalidate(); }

    /** Owned bitmap for the encoder; hierarchy is semantic layout, never protected pixels. */
    Bitmap frame(int mode) throws Exception {
        if (automation == null || display == null) throw new IllegalStateException("AUTOMATION_UNAVAILABLE");
        if (mode == 1) return screenshotFrame();
        if (mode != 2 && mode != 4) throw new IllegalArgumentException("MODE_INVALID");
        DisplayCapture.Snapshot size = display.snapshot();
        Bitmap result = null;
        boolean screenshotAvailable = false;
        boolean transferred = false;
        try {
            if (mode == 4) {
                try {
                    result = screenshotFrame();
                    // A rotation between display metadata and screenshot capture must
                    // not stretch old-orientation pixels under new node coordinates.
                    if (Math.abs((long) result.getWidth() * size.height - (long) result.getHeight() * size.width)
                            > 32L * Math.max(size.width, size.height)) {
                        result.recycle(); result = null;
                    } else screenshotAvailable = true;
                } catch (Exception screenshotUnavailable) {
                    if (result != null) { result.recycle(); result = null; }
                    removeCapabilities(AdbWire.CAP_SCREENSHOT);
                    // Mode 4 remains active: collect/draw hierarchy on a neutral background.
                }
            }
            if (result == null) {
                float scale = Math.min(1f, 1280f / Math.max(size.width, size.height));
                result = Bitmap.createBitmap(Math.max(1, (int) (size.width * scale)),
                        Math.max(1, (int) (size.height * scale)), Bitmap.Config.ARGB_8888);
                result.eraseColor(Color.BLACK);
            }
            hierarchy.snapshot(automation, size).draw(result, mode == 4, screenshotAvailable);
            addCapabilities(AdbWire.CAP_TREE);
            transferred = true;
            return result;
        } finally {
            // Until return, this worker owns every fallback/composition allocation.
            if (!transferred && result != null) result.recycle();
        }
    }

    private Bitmap screenshotFrame() throws Exception {
        Bitmap original = automation.takeScreenshot();
        if (original == null) throw new IllegalStateException("SCREENSHOT_UNAVAILABLE");
        Bitmap scaled = original;
        try {
            int longest = Math.max(original.getWidth(), original.getHeight());
            if (longest > 1280) scaled = Bitmap.createScaledBitmap(original,
                    Math.max(1, original.getWidth() * 1280 / longest), Math.max(1, original.getHeight() * 1280 / longest), true);
            // Mutable software pixels support Canvas overlays and all vendor GL upload paths.
            Bitmap result = scaled.copy(Bitmap.Config.ARGB_8888, true);
            if (result == null) throw new IllegalStateException("SCREENSHOT_UNAVAILABLE");
            addCapabilities(AdbWire.CAP_SCREENSHOT);
            return result;
        } finally { if (scaled != original) scaled.recycle(); original.recycle(); }
    }

    AdbCommands.Result execute(AdbCommands.Command command) {
        try {
            if (automation == null || display == null) return result(command, AdbCommands.UNSUPPORTED);
            switch (command.operation) {
                case AdbCommands.TOUCH: return result(command, touch(command));
                case AdbCommands.KEY: return result(command, key(command.a, command.b, command.c) ? AdbCommands.OK : AdbCommands.REJECTED);
                case AdbCommands.NAVIGATE:
                    int[] navigation = {0, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH,
                            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN};
                    return result(command, press(navigation[command.a]) ? AdbCommands.OK : AdbCommands.REJECTED);
                case AdbCommands.SCREENSHOT:
                    byte[] screenshot = screenshot();
                    addCapabilities(AdbWire.CAP_SCREENSHOT);
                    return new AdbCommands.Result(command.id, AdbCommands.OK, screenshot);
                case AdbCommands.TREE:
                    byte[] tree = tree();
                    addCapabilities(AdbWire.CAP_TREE);
                    return new AdbCommands.Result(command.id, AdbCommands.OK, tree);
                case AdbCommands.DISPLAY:
                    boolean powered = power.set(command.a == 1);
                    if (!powered) removeCapabilities(AdbWire.CAP_DISPLAY);
                    return result(command, powered ? AdbCommands.OK : AdbCommands.UNSUPPORTED);
                case AdbCommands.OVERLAY_BLACK:
                    boolean covered = overlay.set(command.a == 1, display.snapshot().layerStack);
                    return result(command, covered ? AdbCommands.OK : AdbCommands.UNSUPPORTED);
                case AdbCommands.TOUCH_BLOCK:
                    // Retired wire number: old peers cannot reactivate touch blocking.
                    return result(command, AdbCommands.UNSUPPORTED);
                case AdbCommands.RELEASE_INPUT:
                    releaseInput(); return result(command, AdbCommands.OK);
                default: return result(command, AdbCommands.REJECTED);
            }
        } catch (Exception ignored) {
            return result(command, AdbCommands.FAILED);
        }
    }

    private int touch(AdbCommands.Command c) throws Exception {
        // Stop starting new node queries while the app is handling a gesture.
        // Injection never depends on whether this coordinate has a drawn node.
        hierarchy.inputActivity();
        DisplayCapture.Snapshot size = display.snapshot();
        // The frame dimensions must represent the current display orientation/aspect.
        if (Math.abs((long) c.d * size.height - (long) c.e * size.width) > 32L * Math.max(size.width, size.height)) {
            releaseInput(); return AdbCommands.STALE_GEOMETRY;
        }
        if (c.a == MotionEvent.ACTION_DOWN && touchDown != 0) return AdbCommands.OK;
        // Hover and a late release after reconfiguration are harmless idempotent no-ops.
        if (c.a != MotionEvent.ACTION_DOWN && touchDown == 0) return AdbCommands.OK;
        long now = SystemClock.uptimeMillis();
        if (c.a == MotionEvent.ACTION_DOWN) touchDown = now;
        touchX = (float) ((size.width - 1) * (c.b / 1000000.0));
        touchY = (float) ((size.height - 1) * (c.c / 1000000.0));
        MotionEvent event = MotionEvent.obtain(touchDown, now, c.a, touchX, touchY, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            boolean accepted = injector.inject(automation, event, true);
            // A window transition may reject one event without revoking shell
            // input authority. Cancel this gesture, but permit the next one.
            if (!accepted) releaseInput();
            return accepted ? AdbCommands.OK : AdbCommands.REJECTED;
        } finally {
            event.recycle();
            if (c.a == MotionEvent.ACTION_UP || c.a == MotionEvent.ACTION_CANCEL) touchDown = 0;
        }
    }

    private boolean key(int action, int code, int meta) {
        if (code == KeyEvent.KEYCODE_POWER || code == KeyEvent.KEYCODE_SLEEP || code == KeyEvent.KEYCODE_WAKEUP)
            return false;
        long now = SystemClock.uptimeMillis();
        Long down = pressedKeys.get(code);
        if (action == KeyEvent.ACTION_UP && down == null) return true;
        if (action == KeyEvent.ACTION_DOWN && down != null) return true; // no synthetic repeat storm
        if (down == null) down = now;
        KeyEvent event = new KeyEvent(down, now, action, code, 0, meta,
                KeyEvent.KEYCODE_UNKNOWN, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);
        boolean accepted = injector.inject(automation, event, true);
        if (action == KeyEvent.ACTION_DOWN && accepted) pressedKeys.put(code, down);
        if (action == KeyEvent.ACTION_UP) pressedKeys.remove(code);
        if (!accepted) releaseInput();
        return accepted;
    }

    private boolean press(int code) {
        if (!key(KeyEvent.ACTION_DOWN, code, 0)) return false;
        return key(KeyEvent.ACTION_UP, code, 0);
    }

    synchronized void releaseInput() {
        if (automation == null) return;
        if (touchDown != 0) {
            MotionEvent event = MotionEvent.obtain(touchDown, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, touchX, touchY, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try { injector.inject(automation, event, false); } catch (RuntimeException ignored) { } finally { event.recycle(); touchDown = 0; }
        }
        for (Map.Entry<Integer, Long> held : pressedKeys.entrySet()) {
            try { injector.inject(automation, new KeyEvent(held.getValue(), SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
                    held.getKey(), 0, 0, KeyEvent.KEYCODE_UNKNOWN, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD), false); }
            catch (RuntimeException ignored) { }
        }
        pressedKeys.clear();
    }

    private byte[] screenshot() throws Exception {
        Bitmap bitmap = automation.takeScreenshot();
        if (bitmap == null) throw new IllegalStateException("SCREENSHOT_UNAVAILABLE");
        Bitmap scaled = bitmap;
        try {
            int longest = Math.max(bitmap.getWidth(), bitmap.getHeight());
            if (longest > 1280) scaled = Bitmap.createScaledBitmap(bitmap,
                    Math.max(1, bitmap.getWidth() * 1280 / longest), Math.max(1, bitmap.getHeight() * 1280 / longest), true);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, output) || output.size() > AdbCommands.MAX_RESULT)
                throw new IllegalStateException("SCREENSHOT_TOO_LARGE");
            return output.toByteArray();
        } finally { if (scaled != bitmap) scaled.recycle(); bitmap.recycle(); }
    }

    private byte[] tree() throws Exception {
        DisplayCapture.Snapshot size = display.snapshot();
        return hierarchy.snapshot(automation, size).json();
    }

    private static AdbCommands.Result result(AdbCommands.Command c, int code) {
        try { return new AdbCommands.Result(c.id, code, new byte[0]); }
        catch (java.io.IOException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Server must stop/join frame and control workers before disposing this shared connection. */
    @Override public void close() {
        releaseInput();
        try { overlay.close(); } catch (RuntimeException ignored) { }
        try { power.close(); } catch (RuntimeException ignored) { }
        capabilities.set(0);
        if (automation != null) {
            try { automation.setOnAccessibilityEventListener(null); }
            catch (RuntimeException disconnected) { }
        }
        boolean collectorEnded = hierarchy.close();
        if (automation != null && collectorEnded) {
            try { UiAutomation.class.getMethod("disconnect").invoke(automation); } catch (Exception ignored) { }
            automation = null;
        }
        if (display != null) { display.close(); display = null; }
    }
}
