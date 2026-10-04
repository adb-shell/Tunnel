package com.tunnel.adbhelper;

import android.app.UiAutomation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
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
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One bounded, owned snapshot. Never retains framework nodes or prior screen text. */
final class HierarchyFrame {
    private static final int MAX_WINDOWS = 32, MAX_NODES = 6144, MAX_DEPTH = 64;
    private static final int WINDOW_NODES = 1536, MAX_CHILDREN = 1024, MAX_LABEL = 1024;
    private static final int TEXT_BUDGET = 16 * 1024;
    private static final long COLLECT_BUDGET_MS = 350, PASS_BUDGET_MS = 700, FRESH_MS = 1500;
    final int width, height;
    private final List<View> views = new ArrayList<>();
    private int textBudget = TEXT_BUDGET;
    private boolean hasContent;
    private boolean semanticContent;
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
        private long lastRequestedAt;
        private boolean dirty;
        private int emptyPasses;
        private int width, height, rotation = -1;
        private HierarchyFrame latest;
        private final Map<Integer, AccessibilityEvent> events = new LinkedHashMap<>();
        private volatile long inputUntil;
        private long nextInputPauseAt; // written only by the input worker

        void inputActivity() {
            long now = SystemClock.uptimeMillis();
            // Reserve time for input without starving frames during a long drag.
            if (now >= nextInputPauseAt) {
                inputUntil = now + 120;
                nextInputPauseAt = now + 350;
            }
        }

        synchronized void contentChanged(AccessibilityEvent event) {
            int type = event.getEventType();
            int changes = AccessibilityEvent.TYPE_WINDOWS_CHANGED | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                    | AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED | AccessibilityEvent.TYPE_VIEW_FOCUSED
                    | AccessibilityEvent.TYPE_VIEW_SCROLLED;
            // Copy at most one event per window while hierarchy is requested.
            // getSource() is Binder work and belongs exclusively to the collector.
            if (closed || worker == null || (type & changes) == 0
                    || SystemClock.uptimeMillis() - lastRequestedAt > FRESH_MS) return;
            int id = event.getWindowId();
            if (id >= 0) {
                AccessibilityEvent old = events.put(id, AccessibilityEvent.obtain(event));
                if (old != null) old.recycle();
                if (events.size() > MAX_WINDOWS) {
                    Integer first = events.keySet().iterator().next();
                    events.remove(first).recycle();
                }
            }
            dirty = true; pending = true; notifyAll();
        }

        synchronized void invalidate() {
            revision++; pending = false; latest = null; completedAt = 0; emptyPasses = 0;
            lastRequestedAt = 0; dirty = true; nextCollectAt = 0;
            for (AccessibilityEvent event : events.values()) event.recycle();
            events.clear();
            notifyAll();
        }

        private synchronized boolean isCurrent(long expected) {
            return !closed && revision == expected;
        }

        private synchronized void publish(long expected, HierarchyFrame frame) {
            if (!closed && revision == expected) {
                // Collector already retains fresh content per window. A missing
                // app must not suppress fresh IME/system-window updates here.
                latest = frame;
                completedAt = frame.hasContent ? frame.contentAt : SystemClock.uptimeMillis();
            }
        }

        synchronized HierarchyFrame snapshot(UiAutomation source, DisplayCapture.Snapshot size) {
            if (width != size.width || height != size.height || rotation != size.rotation) {
                invalidate(); width = size.width; height = size.height; rotation = size.rotation;
            }
            if (!closed) {
                lastRequestedAt = SystemClock.uptimeMillis();
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
            if (latest != null && SystemClock.uptimeMillis() - completedAt <= FRESH_MS) return latest;
            return new HierarchyFrame(width, height);
        }

        private void collectLoop() {
            Collector collector = new Collector();
            long collectorRevision = -1;
            try {
                while (true) {
                    long expected;
                    int w, h, empty;
                    boolean changed;
                    UiAutomation source;
                    Map<Integer, AccessibilityEvent> changes;
                    synchronized (this) {
                        while (!closed) {
                            if (collectorRevision != revision) {
                                collector = new Collector(); collectorRevision = revision;
                            }
                            if (!pending) { wait(); continue; }
                            long delay = nextCollectAt - SystemClock.uptimeMillis();
                            delay = Math.max(delay, inputUntil - SystemClock.uptimeMillis());
                            if (delay <= 0) break;
                            wait(delay);
                        }
                        if (closed) return;
                        pending = false; expected = revision;
                        w = width; h = height; source = automation; empty = emptyPasses;
                        changed = dirty; dirty = false;
                        changes = new HashMap<>(events); events.clear();
                    }
                    long started = SystemClock.uptimeMillis();
                    if ((changed || empty > 0 || started - lastCacheClear >= 500)
                            && started - lastCacheClear >= 100 && isCurrent(expected)) {
                        Queries.clearCache(source); lastCacheClear = started;
                    }
                    HierarchyFrame frame;
                    try { frame = collector.collect(source, w, h,
                            () -> isCurrent(expected) && SystemClock.uptimeMillis() >= inputUntil,
                            (empty & 1) != 0, changes, partial -> publish(expected, partial)); }
                    catch (RuntimeException unavailable) { frame = new HierarchyFrame(w, h); }
                    finally { for (AccessibilityEvent event : changes.values()) event.recycle(); }
                    synchronized (this) {
                        if (closed) return;
                        long finished = SystemClock.uptimeMillis();
                        // Binder timeouts cannot be interrupted. Do not start a
                        // second collector or continuously hammer a slow app's UI
                        // thread as soon as each timed-out traversal returns.
                        long elapsed = finished - started;
                        // Slow windows get their own cooldown in Collector. Do not
                        // delay the IME and every other window for the same timeout.
                        nextCollectAt = finished + (elapsed > PASS_BUDGET_MS ? 200 : 100);
                        if (revision == expected && finished >= inputUntil) {
                            emptyPasses = frame.semanticContent ? 0 : 1 + emptyPasses % 2;
                            publish(expected, frame);
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

    /** Worker-owned per-window snapshots. One slow app cannot permanently consume
     * the IME's quota; unfinished windows are first in the following pass. */
    private static final class Collector {
        private final Map<Integer, HierarchyFrame> cache = new HashMap<>();
        private final Map<Integer, Long> attempted = new HashMap<>(), retryAfter = new HashMap<>();

        HierarchyFrame collect(UiAutomation automation, int width, int height,
                               BooleanSupplier current, boolean activeFirst,
                               Map<Integer, AccessibilityEvent> events, Consumer<HierarchyFrame> publish) {
            List<AccessibilityWindowInfo> windows = null;
            List<AccessibilityWindowInfo> ordered = new ArrayList<>();
            try {
                try { windows = automation.getWindows(); }
                catch (RuntimeException unavailable) { /* Active-root fallback below. */ }
                if (!current.getAsBoolean()) return new HierarchyFrame(width, height);
                if (windows != null) for (AccessibilityWindowInfo window : windows) {
                    if (window != null && ordered.size() < MAX_WINDOWS) ordered.add(window);
                }
                ordered.sort(Comparator.comparingInt(HierarchyFrame::windowPriority)
                        .thenComparing(Comparator.comparingInt(AccessibilityWindowInfo::getLayer).reversed()));
                if (ordered.isEmpty()) {
                    cache.clear(); attempted.clear(); retryAfter.clear();
                    HierarchyFrame frame = new HierarchyFrame(width, height);
                    AccessibilityNodeInfo root = Queries.activeRoot(automation);
                    if (root != null) {
                        frame.selectedWindowId = root.getWindowId();
                        recordContent(frame, frame.selectedWindowId, collectRoot(frame, root, 0, current));
                    }
                    return frame;
                }
                Set<Integer> visible = new HashSet<>();
                for (AccessibilityWindowInfo window : ordered) visible.add(window.getId());
                cache.keySet().retainAll(visible);
                attempted.keySet().retainAll(visible);
                retryAfter.keySet().retainAll(visible);
                int selected = ordered.get(0).getId();
                for (AccessibilityWindowInfo window : ordered) {
                    if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        selected = window.getId(); break;
                    }
                }
                // Retain display-layer ordering for composition, but schedule the
                // oldest/unqueried window first. Priority breaks ties (app, IME).
                List<AccessibilityWindowInfo> schedule = new ArrayList<>(ordered);
                schedule.sort(Comparator.comparingLong(w -> attempted.getOrDefault(w.getId(), 0L)));
                long passStarted = SystemClock.uptimeMillis();
                for (AccessibilityWindowInfo window : schedule) {
                    if (!current.getAsBoolean()) break;
                    long started = SystemClock.uptimeMillis();
                    if (started - passStarted >= PASS_BUDGET_MS) break;
                    int id = window.getId();
                    if (started < retryAfter.getOrDefault(id, 0L)) continue;
                    attempted.put(id, started);
                    HierarchyFrame part = new HierarchyFrame(width, height);
                    part.selectedWindowId = id;
                    AccessibilityNodeInfo root = null;
                    AccessibilityNodeInfo focusRoot = null;
                    try {
                        try {
                            root = id == selected && activeFirst
                                    ? Queries.activeRoot(automation) : Queries.windowRoot(window);
                        } catch (RuntimeException unavailable) { /* Event-source fallback remains available. */ }
                        // A missing window root is common during app transitions;
                        // retry through the active-window route in this pass.
                        if (root == null && id == selected && !activeFirst && current.getAsBoolean()) {
                            try { root = Queries.activeRoot(automation); }
                            catch (RuntimeException unavailable) { /* Continue to event-source fallback. */ }
                        }
                        if (root != null && root.getWindowId() == id) {
                            if (id == selected) focusRoot = AccessibilityNodeInfo.obtain(root);
                            AccessibilityNodeInfo owned = root; root = null;
                            boolean content = collectRoot(part, owned, window.getLayer(), current);
                            recordContent(part, id, content);
                            // Publish before another potentially slow Binder call.
                            if (part.hasContent) {
                                cache.put(id, part);
                                publish.accept(compose(ordered, width, height, selected));
                            }
                        }
                        AccessibilityEvent event = events.get(id);
                        if (event != null && current.getAsBoolean()
                                && SystemClock.uptimeMillis() - event.getEventTime() <= FRESH_MS) {
                            AccessibilityNodeInfo source = null;
                            try {
                                source = event.getSource();
                                if (source != null && source.getWindowId() == id && current.getAsBoolean()) {
                                    // Virtual views may be reachable from events
                                    // while their window root is still incomplete.
                                    HierarchyFrame extra = new HierarchyFrame(width, height);
                                    AccessibilityNodeInfo owned = source; source = null;
                                    collectRoot(extra, owned, window.getLayer(), current);
                                    // An event source may itself be the only useful
                                    // virtual view, not a child of our traversal root.
                                    boolean content = !extra.views.isEmpty();
                                    if (content) {
                                        Set<Rect> freshBounds = new HashSet<>();
                                        for (View view : extra.views) freshBounds.add(view.bounds);
                                        part.views.removeIf(view -> freshBounds.contains(view.bounds));
                                        int keep = WINDOW_NODES - extra.views.size();
                                        if (part.views.size() > keep) {
                                            part.views.subList(keep, part.views.size()).clear();
                                            part.truncated = true;
                                        }
                                        part.views.addAll(extra.views);
                                        part.hasContent = true;
                                        part.semanticContent |= extra.semanticContent;
                                        part.truncated |= extra.truncated;
                                        recordContent(part, id, true);
                                    }
                                }
                            } finally { if (source != null) recycle(source); }
                        }
                        if (focusRoot != null && current.getAsBoolean()
                                && SystemClock.uptimeMillis() - started < COLLECT_BUDGET_MS) {
                            View focused = focusedView(focusRoot, window.getLayer());
                            if (focused != null) {
                                part.views.removeIf(view -> view.bounds.equals(focused.bounds));
                                if (part.views.size() >= WINDOW_NODES) {
                                    part.views.remove(part.views.size() - 1); part.truncated = true;
                                }
                                part.views.add(focused);
                                part.hasContent = true; part.semanticContent = true;
                                recordContent(part, id, true);
                            }
                        }
                    } catch (RuntimeException unavailable) { /* Other windows remain usable. */ }
                    finally {
                        if (root != null) recycle(root);
                        if (focusRoot != null) recycle(focusRoot);
                    }
                    long finished = SystemClock.uptimeMillis();
                    if (!current.getAsBoolean()) break;
                    // A timeout is local to this window, not to ADB/input/video.
                    retryAfter.put(id, finished + (finished - started > PASS_BUDGET_MS
                            ? Math.min(2000, finished - started) : 0));
                    HierarchyFrame old = cache.get(id);
                    if (part.hasContent || old == null || finished - old.contentAt > FRESH_MS) cache.put(id, part);
                    publish.accept(compose(ordered, width, height, selected));
                }
                return compose(ordered, width, height, selected);
            } finally {
                if (windows != null) for (AccessibilityWindowInfo window : windows) {
                    if (window != null) try { window.recycle(); } catch (RuntimeException ignored) { }
                }
            }
        }

        private HierarchyFrame compose(List<AccessibilityWindowInfo> windows, int width, int height, int selected) {
            HierarchyFrame frame = new HierarchyFrame(width, height);
            frame.selectedWindowId = selected;
            long now = SystemClock.uptimeMillis();
            for (AccessibilityWindowInfo window : windows) {
                HierarchyFrame part = cache.get(window.getId());
                if (part == null || !part.hasContent || now - part.contentAt > FRESH_MS) continue;
                frame.hasContent = true;
                frame.selectedWindowHasContent |= window.getId() == selected;
                frame.semanticContent |= window.getId() == selected && part.semanticContent;
                frame.contentAt = Math.max(frame.contentAt, part.contentAt);
                frame.truncated |= part.truncated;
                for (View view : part.views) {
                    if (frame.views.size() >= MAX_NODES) { frame.truncated = true; break; }
                    // Window layers may change without their ID changing.
                    frame.views.add(new View(view.bounds, view.parent, view.text, view.depth,
                            window.getLayer(), view.clickable, view.password, view.window));
                }
            }
            frame.views.sort(Comparator.comparingInt(view -> view.layer));
            return frame;
        }
    }

    private static int windowPriority(AccessibilityWindowInfo window) {
        if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION && window.isFocused()) return 0;
        if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) return 1;
        if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) return window.isActive() ? 2 : 3;
        return 4;
    }

    private static View focusedView(AccessibilityNodeInfo root, int layer) {
        AccessibilityNodeInfo focus = null;
        try {
            focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focus == null || !focus.refresh() || focus.getWindowId() != root.getWindowId()) return null;
            Rect bounds = new Rect(); focus.getBoundsInScreen(bounds);
            if (bounds.isEmpty()) return null;
            String text = label(focus, MAX_LABEL);
            if (text.isEmpty() && !focus.isEditable()) return null;
            return new View(bounds, null, text, 0, layer, true, focus.isPassword(), false);
        } catch (RuntimeException unavailable) { return null; }
        finally { if (focus != null && focus != root) recycle(focus); }
    }

    private static String label(AccessibilityNodeInfo node, int budget) {
        CharSequence label = node.getText();
        if (label == null || label.length() == 0) label = node.getContentDescription();
        if (label == null || label.length() == 0) label = node.getHintText();
        if (label == null || label.length() == 0) label = node.getStateDescription();
        if (label == null) return "";
        int length = Math.min(Math.min(label.length(), MAX_LABEL), budget);
        if (length > 0 && length < label.length() && Character.isHighSurrogate(label.charAt(length - 1))) length--;
        return label.subSequence(0, length).toString().replace("\r\n", "\n").replace('\r', '\n');
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
            while (current.getAsBoolean() && !pending.isEmpty() && visited < WINDOW_NODES && frame.views.size() < WINDOW_NODES) {
                Entry entry = pending.removeFirst();
                AccessibilityNodeInfo node = entry.node;
                boolean retained = false;
                try {
                    if (entry.nextChild < 0) {
                        visited++;
                        entry.bounds = new Rect(); node.getBoundsInScreen(entry.bounds);
                        boolean onScreen = !entry.bounds.isEmpty()
                                && Rect.intersects(entry.bounds, new Rect(0, 0, width, height));
                        // Render the text actually exposed by Android, including
                        // password nodes. Masked/omitted OS text is not reconstructed.
                        String text = "";
                        if (onScreen && frame.textBudget > 0) {
                            text = label(node, frame.textBudget);
                            frame.textBudget -= text.length();
                        }
                        // Like the existing accessibility hierarchy renderer, draw
                        // returned screen bounds even underneath a local overlay.
                        if (onScreen) {
                            frame.views.add(new View(entry.bounds, entry.parent, text, entry.depth, entry.layer,
                                    node.isClickable() || node.isLongClickable() || node.isEditable(),
                                    node.isPassword(), false));
                            // A bare DecorView rectangle is not usable app content.
                            // Continue recovery when a virtual/WebView root exposes
                            // no descendants or labels during an app transition.
                            content |= entry.depth > 0 || !text.isEmpty()
                                    || node.isClickable() || node.isEditable() || node.isScrollable();
                            // Container outlines alone must not disable alternate
                            // root recovery in WebViews and virtual-node providers.
                            frame.semanticContent |= !text.isEmpty() || node.isClickable()
                                    || node.isEditable() || node.isScrollable();
                            frame.hasContent |= content;
                        }
                        entry.nextChild = 0;
                    }
                    if (entry.depth < MAX_DEPTH && entry.nextChild < Math.min(node.getChildCount(), MAX_CHILDREN)
                            && pending.size() + visited < WINDOW_NODES && current.getAsBoolean()
                            && SystemClock.uptimeMillis() < deadline) {
                        // Round-robin parents and returned children. Do not spend
                        // every frame walking the first deep branch, or fetch all
                        // siblings before drawing any of their returned fields.
                        // After the deadline, drain already-returned local fields
                        // without issuing any more child Binder queries.
                        AccessibilityNodeInfo child = null;
                        try { child = Queries.child(node, entry.nextChild++); }
                        catch (RuntimeException staleChild) { /* Resume the next sibling. */ }
                        pending.addLast(entry);
                        retained = true;
                        if (child != null) {
                            pending.addLast(new Entry(child, entry.depth + 1, entry.layer, entry.bounds));
                        }
                    } else if (entry.nextChild < node.getChildCount()) {
                        frame.truncated = true;
                    }
                } catch (RuntimeException staleNode) { /* Never fail the video task for a stale node. */ }
                finally { if (!retained) recycle(node); }
            }
            frame.truncated |= !pending.isEmpty() || visited >= WINDOW_NODES || SystemClock.uptimeMillis() >= deadline;
        } finally {
            while (!pending.isEmpty()) recycle(pending.removeFirst().node);
        }
        return content;
    }

    /** Use the platform's interruptible, bounded descendant batches, as the
     * AccessibilityService renderer does. Flags=0 disabled descendant batching
     * and made long lists/WebViews exhaust the budget on uncached child queries.
     * Never use FLAG_PREFETCH_UNINTERRUPTIBLE on the shared input connection. */
    private static final class Queries {
        private static final Method ACTIVE_ROOT = find(UiAutomation.class, "getRootInActiveWindow", int.class);
        private static final Method WINDOW_ROOT = find(AccessibilityWindowInfo.class, "getRoot", int.class);
        private static final Method CHILD = find(AccessibilityNodeInfo.class, "getChild", int.class, int.class);
        private static final Method CLEAR_CACHE = find(UiAutomation.class, "clearCache");

        static void clearCache(UiAutomation source) {
            try {
                if (CLEAR_CACHE != null) CLEAR_CACHE.invoke(source);
                else {
                    // Pre-33 cache lives in this helper process. There is only
                    // one owned UiAutomation here; no APK service is altered.
                    Class<?> client = Class.forName("android.view.accessibility.AccessibilityInteractionClient");
                    Object instance = client.getMethod("getInstance").invoke(null);
                    client.getMethod("clearCache").invoke(instance);
                }
            }
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
            return ACTIVE_ROOT == null ? source.getRootInActiveWindow()
                    : invoke(ACTIVE_ROOT, source, AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_HYBRID);
        }
        static AccessibilityNodeInfo windowRoot(AccessibilityWindowInfo window) {
            return WINDOW_ROOT == null ? window.getRoot()
                    : invoke(WINDOW_ROOT, window, AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_HYBRID);
        }
        static AccessibilityNodeInfo child(AccessibilityNodeInfo node, int index) {
            return CHILD == null ? node.getChild(index)
                    : invoke(CHILD, node, index, AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_HYBRID);
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
                : !selectedWindowHasContent ? "Waiting for window content"
                : truncated ? "Partial window content" : "";
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
