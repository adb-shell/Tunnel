package com.tunnel.adbhelper;

import android.app.UiAutomation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** One bounded, owned snapshot. Never retains framework nodes or prior screen text. */
final class HierarchyFrame {
    private static final int MAX_WINDOWS = 32, MAX_NODES = 1024, MAX_DEPTH = 32;
    private static final int MAX_CHILDREN = 64, MAX_LABEL = 128, TEXT_BUDGET = 16 * 1024;
    private static final long COLLECT_BUDGET_MS = 250;
    final int width, height;
    private final List<View> views = new ArrayList<>();
    private int textBudget = TEXT_BUDGET;
    private boolean hasContent;
    private boolean truncated;

    private static final class View {
        final Rect bounds, parent;
        final String text;
        final int depth, layer;
        final boolean clickable, password, window;
        View(Rect bounds, Rect parent, String text, int depth, int layer,
             boolean clickable, boolean password, boolean window) {
            this.bounds = bounds; this.parent = parent; this.text = text;
            this.depth = depth; this.layer = layer; this.clickable = clickable;
            this.password = password; this.window = window;
        }
    }
    private static final class Entry {
        final AccessibilityNodeInfo node;
        final int depth, layer;
        final Rect parent;
        final boolean password;
        Entry(AccessibilityNodeInfo node, int depth, int layer, Rect parent, boolean password) {
            this.node = node; this.depth = depth; this.layer = layer; this.parent = parent; this.password = password;
        }
    }
    private HierarchyFrame(int width, int height) { this.width = width; this.height = height; }

    static HierarchyFrame collect(UiAutomation automation, int width, int height) {
        HierarchyFrame frame = new HierarchyFrame(width, height);
        ArrayDeque<Entry> pending = new ArrayDeque<>();
        long deadline = SystemClock.uptimeMillis() + COLLECT_BUDGET_MS;
        List<AccessibilityWindowInfo> windows = null;
        try {
            try { windows = automation.getWindows(); } catch (RuntimeException unavailable) { /* Active-root fallback below. */ }
            if (windows != null) {
                // UiAutomation returns highest-layer windows first: prioritize dialogs/IME
                // during collection, then render ascending layers so they remain on top.
                int count = 0;
                for (AccessibilityWindowInfo window : windows) {
                    if (window == null) continue;
                    if (count++ >= MAX_WINDOWS || SystemClock.uptimeMillis() >= deadline) { frame.truncated = true; break; }
                    try {
                        Rect bounds = new Rect(); window.getBoundsInScreen(bounds);
                        int layer = window.getLayer();
                        if (!bounds.isEmpty()) frame.views.add(new View(bounds, null, "", 0, layer, false, false, true));
                        AccessibilityNodeInfo root = window.getRoot();
                        if (root != null) pending.add(new Entry(root, 0, layer, null, false));
                    } catch (RuntimeException staleWindow) { /* Window disappeared between enumeration and root access. */ }
                }
            }
            if (pending.isEmpty()) {
                try {
                    AccessibilityNodeInfo root = automation.getRootInActiveWindow();
                    if (root != null) pending.add(new Entry(root, 0, 0, null, false));
                } catch (RuntimeException staleWindow) { /* Empty frame is valid while windows transition. */ }
            }
            int visited = 0;
            while (!pending.isEmpty() && visited < MAX_NODES && SystemClock.uptimeMillis() < deadline) {
                Entry entry = pending.removeFirst();
                AccessibilityNodeInfo node = entry.node;
                visited++;
                try {
                    Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
                    boolean password = entry.password || node.isPassword();
                    // Password containers can expose labels through descendants too.
                    String text = "";
                    if (!password && frame.textBudget > 0) {
                        CharSequence label = node.getText();
                        if (label == null || label.length() == 0) label = node.getContentDescription();
                        if (label != null) {
                            int length = Math.min(Math.min(label.length(), MAX_LABEL), frame.textBudget);
                            text = label.subSequence(0, length).toString().replace('\n', ' ').replace('\r', ' ');
                            frame.textBudget -= length;
                        }
                    }
                    if (!bounds.isEmpty() && node.isVisibleToUser()) {
                        frame.views.add(new View(bounds, entry.parent, text, entry.depth, entry.layer,
                                node.isClickable() || node.isLongClickable() || node.isEditable(), password, false));
                        frame.hasContent = true;
                    }
                    if (entry.depth < MAX_DEPTH) {
                        int count = Math.min(node.getChildCount(), MAX_CHILDREN);
                        for (int i = 0; i < count && pending.size() + visited < MAX_NODES
                                && SystemClock.uptimeMillis() < deadline; i++) {
                            try {
                                AccessibilityNodeInfo child = node.getChild(i);
                                if (child != null) pending.add(new Entry(child, entry.depth + 1, entry.layer, bounds, password));
                            } catch (RuntimeException staleChild) { /* Continue the remaining nodes/windows. */ }
                        }
                    }
                } catch (RuntimeException staleNode) { /* Never fail the video task for a stale node. */ }
                finally { recycle(node); }
            }
            frame.truncated |= !pending.isEmpty() || visited >= MAX_NODES || SystemClock.uptimeMillis() >= deadline;
        } finally {
            while (!pending.isEmpty()) recycle(pending.removeFirst().node);
            if (windows != null) for (AccessibilityWindowInfo window : windows) {
                if (window != null) try { window.recycle(); } catch (RuntimeException ignored) { }
            }
        }
        frame.views.sort(Comparator.comparingInt(view -> view.layer));
        return frame;
    }

    /** The mutable bitmap is owned by the caller; all coordinates remain display-relative. */
    void draw(Bitmap target, boolean screenshotRequested, boolean screenshotAvailable) {
        Canvas canvas = new Canvas(target);
        float sx = target.getWidth() / (float) width, sy = target.getHeight() / (float) height;
        canvas.save(); canvas.scale(sx, sy);
        canvas.clipRect(0, 0, width, height);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float scale = Math.min(sx, sy);
        paint.setTextSize(14f / scale);
        for (View view : views) {
            Rect bounds = new Rect(view.bounds);
            if (!bounds.intersect(0, 0, width, height)) continue;
            if (view.parent != null && view.depth <= 8 && !view.parent.isEmpty()) {
                paint.setColor(screenshotAvailable ? 0x8066CCFF : 0xFF344B5D);
                paint.setStrokeWidth(.7f / scale);
                canvas.drawLine(view.parent.exactCenterX(), view.parent.exactCenterY(), bounds.exactCenterX(), bounds.top, paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth((view.window ? 1.7f : view.clickable ? 1.2f : .7f) / scale);
            paint.setColor(view.window ? 0xFFFFC857 : view.clickable ? 0xFF55E4EF : 0xFF8299AA);
            canvas.drawRect(bounds, paint);
            if (!view.password && !view.text.isEmpty() && !view.window) {
                canvas.save(); canvas.clipRect(bounds);
                paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
                paint.setShadowLayer(2f / scale, 0, 0, Color.BLACK);
                canvas.drawText(view.text, bounds.left + 2f / scale,
                        bounds.top + 2f / scale - paint.ascent(), paint);
                paint.clearShadowLayer(); canvas.restore();
            }
        }
        canvas.restore();
        String status = screenshotRequested && !screenshotAvailable ? "Screenshot unavailable - showing layout"
                : !hasContent ? "Waiting for window content" : "";
        if (!status.isEmpty()) {
            paint.setStyle(Paint.Style.FILL); paint.setTextSize(13f); paint.setColor(0xCC17212B);
            canvas.drawRect(0, 0, target.getWidth(), 26, paint);
            paint.setColor(Color.WHITE); canvas.drawText(status, 7, 18, paint);
        }
    }

    byte[] json() throws Exception {
        JSONArray nodes = new JSONArray();
        int bytes = 0;
        for (View view : views) {
            JSONObject node = new JSONObject().put("left", view.bounds.left).put("top", view.bounds.top)
                    .put("right", view.bounds.right).put("bottom", view.bounds.bottom).put("depth", view.depth)
                    .put("layer", view.layer).put("window", view.window).put("text", view.password ? "" : view.text)
                    .put("clickable", view.clickable).put("password", view.password);
            int length = node.toString().getBytes(StandardCharsets.UTF_8).length + 1;
            if (bytes + length > 240 * 1024) { truncated = true; break; }
            nodes.put(node); bytes += length;
        }
        return new JSONObject().put("width", width).put("height", height).put("nodes", nodes)
                .put("empty", !hasContent).put("truncated", truncated).toString().getBytes(StandardCharsets.UTF_8);
    }
    private static void recycle(AccessibilityNodeInfo node) {
        try { node.recycle(); } catch (RuntimeException ignored) { }
    }
}
