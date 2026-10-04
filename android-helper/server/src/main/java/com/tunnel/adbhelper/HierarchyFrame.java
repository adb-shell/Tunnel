package com.tunnel.adbhelper;

import android.app.UiAutomation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One bounded, owned snapshot. Never retains framework nodes or prior screen text. */
final class HierarchyFrame {
    private static final int MAX_WINDOWS = 32, MAX_NODES = 1024, MAX_DEPTH = 32;
    private static final int MAX_CHILDREN = 64, MAX_LABEL = 512, TEXT_BUDGET = 16 * 1024;
    private static final long COLLECT_BUDGET_MS = 250;
    final int width, height;
    private final List<View> views = new ArrayList<>();
    private int textBudget = TEXT_BUDGET;
    private boolean hasContent;
    private boolean truncated;
    private int selectedWindowId = -1;
    private boolean selectedWindowHasContent;
    private long contentAt;

    /** A single coalescing collector. Slow accessibility Binder calls must never
     * block the encoder, input worker, or create replacement collector threads. */
    static final class Provider {
        private Thread worker;
        private UiAutomation automation;
        private boolean closed, pending;
        private long revision, completedAt, nextCollectAt;
        private long lastCacheClear;
        private int emptyPasses;
        private int width, height, rotation = -1;
        private HierarchyFrame latest;

        synchronized void invalidate() {
            revision++; pending = false; latest = null; completedAt = 0; emptyPasses = 0;
            notifyAll();
        }

        private synchronized boolean isCurrent(long expected) {
            return !closed && revision == expected;
        }

        private synchronized void publish(long expected, HierarchyFrame frame) {
            long now = SystemClock.uptimeMillis();
            if (!closed && revision == expected && frame.hasContent
                    && (frame.selectedWindowHasContent || latest == null
                        || frame.selectedWindowId != latest.selectedWindowId || now - completedAt > 1500)) {
                latest = frame; completedAt = frame.contentAt;
            }
        }

        synchronized HierarchyFrame snapshot(UiAutomation source, DisplayCapture.Snapshot size) {
            if (width != size.width || height != size.height || rotation != size.rotation) {
                invalidate(); width = size.width; height = size.height; rotation = size.rotation;
            }
            if (!closed) {
                automation = source;
                pending = true;
                if (worker == null) {
                    worker = new Thread(this::collectLoop, "tunnel-adb-hierarchy");
                    worker.setDaemon(true); worker.start();
                }
                notifyAll();
            }
            // Do not keep displaying previous application text indefinitely when
            // an OEM stops answering accessibility requests.
            if (latest != null && SystemClock.uptimeMillis() - completedAt <= 1500) return latest;
            return new HierarchyFrame(width, height);
        }

        private void collectLoop() {
            try {
                while (true) {
                    long expected;
                    int w, h, empty;
                    UiAutomation source;
                    synchronized (this) {
                        while (!closed) {
                            if (!pending) { wait(); continue; }
                            long delay = nextCollectAt - SystemClock.uptimeMillis();
                            if (delay <= 0) break;
                            wait(delay);
                        }
                        if (closed) return;
                        pending = false; expected = revision;
                        w = width; h = height; source = automation; empty = emptyPasses;
                    }
                    long started = SystemClock.uptimeMillis();
                    if (empty > 0 && started - lastCacheClear >= 1000 && isCurrent(expected)) {
                        Queries.clearCache(source); lastCacheClear = started;
                    }
                    HierarchyFrame frame;
                    try { frame = collect(source, w, h, () -> isCurrent(expected), (empty & 1) != 0,
                            partial -> publish(expected, partial)); }
                    catch (RuntimeException unavailable) { frame = new HierarchyFrame(w, h); }
                    synchronized (this) {
                        if (closed) return;
                        long finished = SystemClock.uptimeMillis();
                        // Binder timeouts cannot be interrupted. Do not start a
                        // second collector or continuously hammer a slow app's UI
                        // thread as soon as each timed-out traversal returns.
                        long elapsed = finished - started;
                        nextCollectAt = finished + (elapsed > COLLECT_BUDGET_MS
                                ? Math.min(1000, elapsed) : 200);
                        if (revision == expected) {
                            emptyPasses = frame.selectedWindowHasContent ? 0 : 1 + emptyPasses % 2;
                            // A transient null response in the SAME app window must
                            // not immediately replace its last good picture. Do not
                            // refresh the TTL or carry text across window changes.
                            if (frame.selectedWindowHasContent || latest == null
                                    || frame.selectedWindowId != latest.selectedWindowId
                                    || finished - completedAt > 1500) {
                                latest = frame; completedAt = frame.hasContent ? frame.contentAt : finished;
                            }
                        }
                    }
                }
            } catch (InterruptedException shutdown) { Thread.currentThread().interrupt(); }
        }

        synchronized boolean close() {
            closed = true; invalidate(); notifyAll();
            if (worker != null) worker.interrupt();
            // Never disconnect a shared UiAutomation while a vendor Binder call
            // still uses it. The helper process owns its final OS cleanup.
            return worker == null || !worker.isAlive();
        }
    }

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
        int nextChild = -1;
        Rect bounds;
        Entry(AccessibilityNodeInfo node, int depth, int layer, Rect parent) {
            this.node = node; this.depth = depth; this.layer = layer; this.parent = parent;
        }
    }
    private HierarchyFrame(int width, int height) { this.width = width; this.height = height; }

    private static HierarchyFrame collect(UiAutomation automation, int width, int height,
                                          BooleanSupplier current, boolean activeFirst,
                                          Consumer<HierarchyFrame> publish) {
        HierarchyFrame frame = new HierarchyFrame(width, height);
        List<AccessibilityWindowInfo> windows = null;
        int queries = 0;
        long queryTime = 0;
        java.util.Set<Integer> visitedWindows = new java.util.HashSet<>();
        try {
            // Window enumeration does not depend on the active app answering a
            // node query. Do it BEFORE the potentially slow active-root request.
            try { windows = automation.getWindows(); }
            catch (RuntimeException unavailable) { /* Active-root fallback below. */ }
            if (!current.getAsBoolean()) return frame;
            List<AccessibilityWindowInfo> ordered = new ArrayList<>();
            if (windows != null) for (AccessibilityWindowInfo window : windows) {
                if (window != null && ordered.size() < MAX_WINDOWS) ordered.add(window);
            }
            // Prioritize app content, but do not filter system windows/IME/widgets.
            // Layer order is restored only when publishing immutable snapshots.
            ordered.sort(Comparator.comparingInt(HierarchyFrame::windowPriority)
                    .thenComparing(Comparator.comparingInt(AccessibilityWindowInfo::getLayer).reversed()));
            if (!ordered.isEmpty()) frame.selectedWindowId = ordered.get(0).getId();

            // Alternate query routes after an empty pass. A single OEM Binder
            // timeout must not make us retry the same broken route forever.
            if (activeFirst || ordered.isEmpty()) {
                queryTime += collectActive(automation, frame, current, visitedWindows, ordered);
                queries++;
                if (frame.hasContent) publish.accept(frame.copy());
            }
            for (AccessibilityWindowInfo window : ordered) {
                if (!current.getAsBoolean()) break;
                if (queries >= 4 || queryTime >= COLLECT_BUDGET_MS || frame.views.size() >= MAX_NODES) {
                    frame.truncated = true; break;
                }
                if (visitedWindows.contains(window.getId())) continue;
                AccessibilityNodeInfo root = null;
                long started = SystemClock.uptimeMillis();
                try { root = Queries.windowRoot(window); }
                catch (RuntimeException staleWindow) { /* Try another window while within quota. */ }
                queryTime += SystemClock.uptimeMillis() - started;
                queries++;
                if (root == null) continue;
                if (root.getWindowId() != window.getId()) { recycle(root); continue; }
                boolean content = collectRoot(frame, root, window.getLayer(), current);
                if (content) visitedWindows.add(window.getId());
                recordContent(frame, window.getId(), content);
                // Publish useful app nodes before querying an IME/system window:
                // a slow secondary window must not hold the first picture hostage.
                if (frame.hasContent) publish.accept(frame.copy());
            }
            if (!frame.selectedWindowHasContent && !activeFirst && !ordered.isEmpty()
                    && current.getAsBoolean() && queries < 4 && queryTime < COLLECT_BUDGET_MS) {
                collectActive(automation, frame, current, visitedWindows, ordered);
            }
        } finally {
            if (windows != null) for (AccessibilityWindowInfo window : windows) {
                if (window != null) try { window.recycle(); } catch (RuntimeException ignored) { }
            }
        }
        frame.views.sort(Comparator.comparingInt(view -> view.layer));
        return frame;
    }

    private static int windowPriority(AccessibilityWindowInfo window) {
        if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
            return window.isFocused() ? 0 : window.isActive() ? 1 : 2;
        }
        return window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD ? 3 : 4;
    }

    private static long collectActive(UiAutomation automation, HierarchyFrame frame,
                                      BooleanSupplier current, java.util.Set<Integer> visitedWindows,
                                      List<AccessibilityWindowInfo> windows) {
        if (!current.getAsBoolean()) return 0;
        AccessibilityNodeInfo root;
        long started = SystemClock.uptimeMillis();
        try { root = Queries.activeRoot(automation); }
        catch (RuntimeException unavailable) { return SystemClock.uptimeMillis() - started; }
        long queryTime = SystemClock.uptimeMillis() - started;
        if (root == null) return queryTime;
        int windowId = root.getWindowId();
        int layer = 0;
        boolean listed = windows.isEmpty();
        for (AccessibilityWindowInfo window : windows) {
            if (window.getId() == windowId) { listed = true; layer = window.getLayer(); break; }
        }
        if (!listed) { recycle(root); return queryTime; } // A stale root from an already replaced window.
        if (visitedWindows.contains(windowId)) { recycle(root); return queryTime; }
        if (frame.selectedWindowId == -1) frame.selectedWindowId = windowId;
        boolean content = collectRoot(frame, root, layer, current);
        if (content) visitedWindows.add(windowId);
        recordContent(frame, windowId, content);
        return queryTime;
    }

    private static void recordContent(HierarchyFrame frame, int windowId, boolean content) {
        if (!content) return;
        if (windowId == frame.selectedWindowId) {
            frame.selectedWindowHasContent = true;
            frame.contentAt = SystemClock.uptimeMillis();
        } else if (!frame.selectedWindowHasContent) {
            frame.contentAt = SystemClock.uptimeMillis();
        }
    }

    private HierarchyFrame copy() {
        HierarchyFrame copy = new HierarchyFrame(width, height);
        copy.views.addAll(views);
        copy.views.sort(Comparator.comparingInt(view -> view.layer));
        copy.textBudget = textBudget; copy.hasContent = hasContent;
        copy.truncated = truncated; copy.selectedWindowId = selectedWindowId;
        copy.selectedWindowHasContent = selectedWindowHasContent;
        copy.contentAt = contentAt;
        return copy;
    }

    private static boolean collectRoot(HierarchyFrame frame, AccessibilityNodeInfo root, int layer,
                                    BooleanSupplier current) {
        ArrayDeque<Entry> pending = new ArrayDeque<>();
        pending.add(new Entry(root, 0, layer, null));
        int width = frame.width, height = frame.height;
        // Node traversal has its own budget, not the time already spent obtaining
        // the root. This is a scheduling budget, not a cancellable Binder timeout.
        long deadline = SystemClock.uptimeMillis() + COLLECT_BUDGET_MS;
        boolean content = false;
        try {
            int visited = 0;
            while (current.getAsBoolean() && !pending.isEmpty() && visited < MAX_NODES && frame.views.size() < MAX_NODES
                    && (SystemClock.uptimeMillis() < deadline || pending.peekFirst().nextChild < 0)) {
                Entry entry = pending.removeFirst();
                AccessibilityNodeInfo node = entry.node;
                boolean retained = false;
                try {
                    if (entry.nextChild < 0) {
                        visited++;
                        entry.bounds = new Rect(); node.getBoundsInScreen(entry.bounds);
                        // Render the text actually exposed by Android, including
                        // password nodes. Masked/omitted OS text is not reconstructed.
                        String text = "";
                        if (frame.textBudget > 0) {
                            CharSequence label = node.getText();
                            if (label == null || label.length() == 0) label = node.getContentDescription();
                            if (label == null || label.length() == 0) label = node.getHintText();
                            if (label == null || label.length() == 0) label = node.getStateDescription();
                            if (label != null) {
                                int length = Math.min(Math.min(label.length(), MAX_LABEL), frame.textBudget);
                                // Do not split an emoji/supplementary character at
                                // the bounded UTF-16 edge. Preserve real line breaks.
                                if (length > 0 && length < label.length()
                                        && Character.isHighSurrogate(label.charAt(length - 1))) length--;
                                text = label.subSequence(0, length).toString().replace("\r\n", "\n").replace('\r', '\n');
                                frame.textBudget -= length;
                            }
                        }
                        // Like the existing accessibility hierarchy renderer, draw
                        // returned screen bounds even underneath a local overlay.
                        if (!entry.bounds.isEmpty() && Rect.intersects(entry.bounds, new Rect(0, 0, width, height))) {
                            frame.views.add(new View(entry.bounds, entry.parent, text, entry.depth, entry.layer,
                                    node.isClickable() || node.isLongClickable() || node.isEditable(),
                                    node.isPassword(), false));
                            // A bare DecorView rectangle is not usable app content.
                            // Continue recovery when a virtual/WebView root exposes
                            // no descendants or labels during an app transition.
                            content |= entry.depth > 0 || !text.isEmpty()
                                    || node.isClickable() || node.isEditable() || node.isScrollable();
                            frame.hasContent |= content;
                        }
                        entry.nextChild = 0;
                    }
                    if (entry.depth < MAX_DEPTH && entry.nextChild < Math.min(node.getChildCount(), MAX_CHILDREN)
                            && pending.size() + visited < MAX_NODES && current.getAsBoolean()
                            && SystemClock.uptimeMillis() < deadline) {
                        // Visit/draw each returned child immediately, as the app's
                        // accessibility renderer does. Fetching every sibling first
                        // could spend the entire budget in getChild(), then recycle
                        // all of them unseen and draw only the root on every frame.
                        // The loop may render this already-returned child's local
                        // fields after the deadline, but never fetch another child.
                        AccessibilityNodeInfo child = null;
                        try { child = Queries.child(node, entry.nextChild++); }
                        catch (RuntimeException staleChild) { /* Resume the next sibling. */ }
                        pending.addFirst(entry);
                        retained = true;
                        if (child != null) {
                            pending.addFirst(new Entry(child, entry.depth + 1, entry.layer, entry.bounds));
                        }
                    }
                } catch (RuntimeException staleNode) { /* Never fail the video task for a stale node. */ }
                finally { if (!retained) recycle(node); }
            }
            frame.truncated |= !pending.isEmpty() || visited >= MAX_NODES || SystemClock.uptimeMillis() >= deadline;
        } finally {
            while (!pending.isEmpty()) recycle(pending.removeFirst().node);
        }
        return content;
    }

    /** Newer Android defaults to descendant prefetch, which can schedule much
     * more work on the target application's UI thread than one requested node.
     * Probe overloads separately: the root overload is absent on some releases
     * which already provide the child overload. Old OSes keep their native API. */
    private static final class Queries {
        private static final Method ACTIVE_ROOT = find(UiAutomation.class, "getRootInActiveWindow", int.class);
        private static final Method WINDOW_ROOT = find(AccessibilityWindowInfo.class, "getRoot", int.class);
        private static final Method CHILD = find(AccessibilityNodeInfo.class, "getChild", int.class, int.class);
        private static final Method CLEAR_CACHE = find(UiAutomation.class, "clearCache");

        static void clearCache(UiAutomation source) {
            if (CLEAR_CACHE == null) return;
            try { CLEAR_CACHE.invoke(source); }
            catch (ReflectiveOperationException | RuntimeException unavailable) { }
        }

        private static Method find(Class<?> type, String name, Class<?>... parameters) {
            try { return type.getMethod(name, parameters); }
            catch (ReflectiveOperationException | SecurityException unavailable) { return null; }
        }
        private static AccessibilityNodeInfo invoke(Method method, Object target, Object... args) {
            try { return (AccessibilityNodeInfo) method.invoke(target, args); }
            catch (ReflectiveOperationException failed) {
                // Do not retry the same potentially slow Binder request with the
                // legacy API after an invocation failure.
                throw new IllegalStateException("HIERARCHY_QUERY_FAILED");
            }
        }
        static AccessibilityNodeInfo activeRoot(UiAutomation source) {
            return ACTIVE_ROOT == null ? source.getRootInActiveWindow() : invoke(ACTIVE_ROOT, source, 0);
        }
        static AccessibilityNodeInfo windowRoot(AccessibilityWindowInfo window) {
            return WINDOW_ROOT == null ? window.getRoot() : invoke(WINDOW_ROOT, window, 0);
        }
        static AccessibilityNodeInfo child(AccessibilityNodeInfo node, int index) {
            return CHILD == null ? node.getChild(index) : invoke(CHILD, node, index, 0);
        }
    }

    /** The mutable bitmap is owned by the caller; all coordinates remain display-relative. */
    void draw(Bitmap target, boolean screenshotRequested, boolean screenshotAvailable) {
        Canvas canvas = new Canvas(target);
        float sx = target.getWidth() / (float) width, sy = target.getHeight() / (float) height;
        canvas.save(); canvas.scale(sx, sy);
        canvas.clipRect(0, 0, width, height);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // app_process does not run ActivityThread's font-map initialization.
        // A null native default font can abort the entire helper, beyond Java
        // exception handling. Never measure/draw text until initialization passed.
        Typeface typeface = ShellFonts.typeface();
        boolean drawText = typeface != null;
        if (drawText) paint.setTypeface(typeface);
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
            if (drawText && !view.text.isEmpty() && !view.window) {
                // A label/layout failure must not discard the whole frame or
                // interrupt input. Other nodes and the screenshot remain valid.
                try { HierarchyText.draw(canvas, view.text, bounds, scale, typeface); }
                catch (RuntimeException invalidLabel) { }
            }
        }
        canvas.restore();
        String status = screenshotRequested && !screenshotAvailable ? "Screenshot unavailable - showing layout"
                : !selectedWindowHasContent ? "Waiting for window content" : "";
        if (!status.isEmpty() || !drawText) {
            paint.setStyle(Paint.Style.FILL); paint.setTextSize(13f); paint.setColor(0xCC17212B);
            canvas.drawRect(0, 0, target.getWidth(), 26, paint);
            paint.setColor(Color.WHITE);
            if (drawText) canvas.drawText(status, 7, 18, paint);
            else {
                // Font-free visible fallback, including before the first root.
                // Keep the bitmap/encoder/input alive on vendor font failures.
                paint.setColor(0xFFFFC857); paint.setStrokeWidth(2f);
                canvas.drawLine(8, 7, 20, 19, paint);
                canvas.drawLine(20, 7, 8, 19, paint);
            }
        }
    }

    byte[] json() throws Exception {
        JSONArray nodes = new JSONArray();
        int bytes = 0;
        for (View view : views) {
            JSONObject node = new JSONObject().put("left", view.bounds.left).put("top", view.bounds.top)
                    .put("right", view.bounds.right).put("bottom", view.bounds.bottom).put("depth", view.depth)
                    .put("layer", view.layer).put("window", view.window).put("text", view.text)
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
