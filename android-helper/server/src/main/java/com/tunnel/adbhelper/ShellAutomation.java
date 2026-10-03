package com.tunnel.adbhelper;

import android.app.UiAutomation;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.tunnel.adb.protocol.AdbCommands;
import com.tunnel.adb.protocol.AdbWire;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Owns one UiAutomation connection, never suppresses the user's accessibility services.
 * Hidden constructor/connect signatures checked against AOSP android-16.0.0_r1 UiAutomation.java.
 * No executeShellCommand, adoptShellPermissionIdentity, settings writes, or package operations.
 */
final class ShellAutomation implements AutoCloseable {
    private UiAutomation automation;
    private DisplayCapture display;
    private final DisplayPower power = new DisplayPower();
    private final BlackOverlay overlay = new BlackOverlay();
    private long touchDown;
    private float touchX, touchY;
    private final Map<Integer, Long> pressedKeys = new HashMap<>();
    private volatile int capabilities;

    void connect() throws Exception {
        Class<?> connectionType = Class.forName("android.app.IUiAutomationConnection");
        Object connection = Class.forName("android.app.UiAutomationConnection").getDeclaredConstructor().newInstance();
        automation = (UiAutomation) UiAutomation.class.getConstructor(Looper.class, connectionType)
                .newInstance(Looper.getMainLooper(), connection);
        UiAutomation.class.getMethod("connect", int.class).invoke(automation,
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        if (automation.getServiceInfo() == null) throw new IllegalStateException("AUTOMATION_UNAVAILABLE");
        display = new DisplayCapture();
        display.snapshot();
        capabilities = AdbWire.CAP_INPUT;
        if (power.probe()) capabilities |= AdbWire.CAP_DISPLAY;
        if (overlay.prepare()) capabilities |= AdbWire.CAP_OVERLAY;
    }

    int capabilities() { return capabilities; }
    void revokeMode(int mode) { capabilities &= ~(mode == 1 ? AdbWire.CAP_SCREENSHOT : mode == 2 ? AdbWire.CAP_TREE : 0); }

    /** Owned bitmap for the encoder; node mode draws semantic bounds, never protected pixels. */
    Bitmap frame(int mode) throws Exception {
        if (automation == null) throw new IllegalStateException("AUTOMATION_UNAVAILABLE");
        if (mode == 1) {
            Bitmap original = automation.takeScreenshot();
            if (original == null) throw new IllegalStateException("SCREENSHOT_UNAVAILABLE");
            Bitmap scaled = original;
            try {
                int longest = Math.max(original.getWidth(), original.getHeight());
                if (longest > 1280) scaled = Bitmap.createScaledBitmap(original,
                        Math.max(1, original.getWidth() * 1280 / longest), Math.max(1, original.getHeight() * 1280 / longest), true);
                // GLUtils cannot upload a hardware Bitmap directly on every vendor driver.
                Bitmap result = scaled.copy(Bitmap.Config.ARGB_8888, false);
                if (result == null) throw new IllegalStateException("SCREENSHOT_UNAVAILABLE");
                capabilities |= AdbWire.CAP_SCREENSHOT; return result;
            } finally { if (scaled != original) scaled.recycle(); original.recycle(); }
        }
        if (mode != 2) throw new IllegalArgumentException("MODE_INVALID");
        JSONObject document = new JSONObject(new String(tree(), StandardCharsets.UTF_8));
        int physicalWidth = document.getInt("width"), physicalHeight = document.getInt("height");
        float scale = Math.min(1f, 1280f / Math.max(physicalWidth, physicalHeight));
        Bitmap result = Bitmap.createBitmap(Math.max(1, (int) (physicalWidth * scale)),
                Math.max(1, (int) (physicalHeight * scale)), Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(result); canvas.drawColor(Color.BLACK); canvas.scale(scale, scale);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setTextSize(18 / scale);
            JSONArray nodes = document.getJSONArray("nodes");
            for (int i = 0; i < nodes.length(); i++) {
                JSONObject node = nodes.getJSONObject(i);
                float left = Math.max(0, node.getInt("left")), top = Math.max(0, node.getInt("top"));
                float right = Math.min(physicalWidth, node.getInt("right")), bottom = Math.min(physicalHeight, node.getInt("bottom"));
                if (left >= right || top >= bottom) continue;
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1 / scale);
                paint.setColor(node.getBoolean("clickable") ? Color.CYAN : Color.DKGRAY);
                canvas.drawRect(left, top, right, bottom, paint);
                paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
                canvas.save(); canvas.clipRect(left, top, right, bottom);
                canvas.drawText(node.getBoolean("password") ? "" : node.getString("text"), left + 2, top + 20 / scale, paint);
                canvas.restore();
            }
            capabilities |= AdbWire.CAP_TREE; return result;
        } catch (Exception failure) { result.recycle(); throw failure; }
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
                    capabilities |= AdbWire.CAP_SCREENSHOT;
                    return new AdbCommands.Result(command.id, AdbCommands.OK, screenshot);
                case AdbCommands.TREE:
                    byte[] tree = tree();
                    capabilities |= AdbWire.CAP_TREE;
                    return new AdbCommands.Result(command.id, AdbCommands.OK, tree);
                case AdbCommands.DISPLAY:
                    boolean powered = power.set(command.a == 1);
                    if (!powered) capabilities &= ~AdbWire.CAP_DISPLAY;
                    return result(command, powered ? AdbCommands.OK : AdbCommands.UNSUPPORTED);
                case AdbCommands.OVERLAY_BLACK:
                    boolean covered = overlay.set(command.a == 1, display.snapshot().layerStack);
                    if (!covered) capabilities &= ~AdbWire.CAP_OVERLAY;
                    return result(command, covered ? AdbCommands.OK : AdbCommands.UNSUPPORTED);
                case AdbCommands.RELEASE_INPUT:
                    releaseInput(); return result(command, AdbCommands.OK);
                default: return result(command, AdbCommands.REJECTED);
            }
        } catch (Exception ignored) {
            return result(command, AdbCommands.FAILED);
        }
    }

    private int touch(AdbCommands.Command c) throws Exception {
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
            boolean accepted = automation.injectInputEvent(event, true);
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
        boolean accepted = automation.injectInputEvent(event, true);
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
            try { automation.injectInputEvent(event, false); } catch (RuntimeException ignored) { } finally { event.recycle(); touchDown = 0; }
        }
        for (Map.Entry<Integer, Long> held : pressedKeys.entrySet()) {
            try { automation.injectInputEvent(new KeyEvent(held.getValue(), SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
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

    private static final class Node { final AccessibilityNodeInfo value; final int depth;
        Node(AccessibilityNodeInfo value, int depth) { this.value = value; this.depth = depth; } }

    private byte[] tree() throws Exception {
        AccessibilityNodeInfo root = automation.getRootInActiveWindow();
        if (root == null) throw new IllegalStateException("TREE_UNAVAILABLE");
        ArrayDeque<Node> pending = new ArrayDeque<>();
        pending.add(new Node(root, 0));
        JSONArray nodes = new JSONArray();
        int textBudget = 32 * 1024;
        long deadline = SystemClock.uptimeMillis() + 1000;
        try {
            while (!pending.isEmpty() && nodes.length() < 1024 && SystemClock.uptimeMillis() < deadline) {
                Node entry = pending.removeFirst(); AccessibilityNodeInfo node = entry.value;
                try {
                    Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
                    String label = node.isPassword() || node.getText() == null ? "" : node.getText().toString();
                    int length = Math.min(Math.min(label.length(), 256), textBudget);
                    label = label.substring(0, length); textBudget -= length;
                    nodes.put(new JSONObject().put("left", bounds.left).put("top", bounds.top)
                            .put("right", bounds.right).put("bottom", bounds.bottom).put("depth", entry.depth)
                            .put("text", label).put("clickable", node.isClickable()).put("password", node.isPassword()));
                    if (entry.depth < 32) for (int i = 0; i < Math.min(node.getChildCount(), 128) && pending.size() + nodes.length() < 1024; i++) {
                        AccessibilityNodeInfo child = node.getChild(i); if (child != null) pending.add(new Node(child, entry.depth + 1));
                    }
                } finally { node.recycle(); }
            }
            DisplayCapture.Snapshot size = display.snapshot();
            byte[] bytes = new JSONObject().put("width", size.width).put("height", size.height)
                    .put("nodes", nodes).toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 256 * 1024) throw new IllegalStateException("TREE_TOO_LARGE");
            return bytes;
        } finally { while (!pending.isEmpty()) pending.removeFirst().value.recycle(); }
    }

    private static AdbCommands.Result result(AdbCommands.Command c, int code) {
        try { return new AdbCommands.Result(c.id, code, new byte[0]); }
        catch (java.io.IOException impossible) { throw new IllegalStateException(impossible); }
    }

    @Override public void close() {
        releaseInput();
        overlay.close();
        power.close();
        capabilities = 0;
        if (automation != null) {
            try { UiAutomation.class.getMethod("disconnect").invoke(automation); } catch (Exception ignored) { }
            automation = null;
        }
        if (display != null) { display.close(); display = null; }
    }
}
