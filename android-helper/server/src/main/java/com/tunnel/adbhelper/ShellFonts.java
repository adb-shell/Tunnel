package com.tunnel.adbhelper;

import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.graphics.fonts.SystemFonts;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Initializes fonts missing from an app_process launch, without probing native text APIs. */
final class ShellFonts {
    private static volatile Typeface defaultTypeface;
    private static boolean attempted;

    private ShellFonts() { }

    /** Called by the main thread before any frame/control worker is started. */
    static synchronized void prepare() {
        if (attempted) return;
        attempted = true;
        try {
            // Reading this field initializes Typeface on Android 11. Android 12+
            // uses lazy font initialization instead: normal applications receive
            // the font map in ActivityThread.handleBindApplication(), which this
            // shell process never enters. Reflection also avoids retaining an
            // optimized read of a DEFAULT field replaced by native initialization.
            Field field = Typeface.class.getField("DEFAULT");
            Typeface typeface = (Typeface) field.get(null);
            if (typeface == null) {
                // AOSP android-12.0.0_r1 through android-16.0.0_r1:
                // graphics/java/android/graphics/Typeface.java
                // This initializer installs both the Java and native defaults.
                Typeface.class.getMethod("loadPreinstalledSystemFontMap").invoke(null);
                typeface = (Typeface) field.get(null);
            }
            defaultTypeface = typeface;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            // Do not call create(), ascent(), measureText(), or drawText() to test
            // availability: libs/hwui/hwui/Typeface.cpp resolveDefault() aborts
            // the process when the native default is absent, outside Java catches.
            defaultTypeface = null;
        }
        if (defaultTypeface == null) {
            // A vendor's font-config loader may fail even though its actual font
            // files are readable. Build an explicit fallback chain independently
            // of DEFAULT; do not permanently turn all text off after one failure.
            try { defaultTypeface = fromInstalledFonts(); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { }
        }
        if (defaultTypeface == null) {
            // Only a fixed code, never font paths, vendor exception text or UI text.
            System.err.println("TUNNEL_ADB_FONTS:UNAVAILABLE");
        }
    }

    private static Typeface fromInstalledFonts() throws ReflectiveOperationException {
        List<Font> fonts = new ArrayList<>();
        try { fonts.addAll(SystemFonts.getAvailableFonts()); }
        catch (RuntimeException | LinkageError unavailable) { /* Fixed system-file fallback below. */ }
        // These files are optional. Supplement even a nonempty enumeration: a
        // partially initialized native font map may expose Latin but omit CJK.
        // The public enumeration also covers OEM/APEX/product-specific names.
        for (String name : new String[]{"Roboto-Regular.ttf", "RobotoStatic-Regular.ttf",
                "NotoSans-Regular.ttf", "NotoSansCJK-Regular.ttc", "NotoSansSC-Regular.otf",
                "DroidSansFallback.ttf", "NotoColorEmoji.ttf"}) {
            File file = new File("/system/fonts", name);
            if (!file.isFile() || !file.canRead()) continue;
            try { fonts.add(new Font.Builder(file).build()); }
            catch (java.io.IOException | RuntimeException invalidFont) { }
        }
        fonts.sort(Comparator.comparingInt(ShellFonts::priority)
                .thenComparing(font -> font.getFile() == null ? "" : font.getFile().getAbsolutePath())
                .thenComparingInt(Font::getTtcIndex));
        List<FontFamily> families = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Font font : fonts) {
            File file = font.getFile();
            if (file == null) continue;
            // Variations share glyph coverage; retain the nearest normal weight.
            String key = file.getAbsolutePath() + ':' + font.getTtcIndex();
            if (!seen.add(key)) continue;
            try { families.add(new FontFamily.Builder(font).build()); }
            catch (RuntimeException invalidFont) { continue; }
            if (families.size() >= 64) break;
        }
        if (families.isEmpty()) return null;
        FontFamily[] chain = families.toArray(new FontFamily[0]);
        // AOSP 11 has the array-only overload; newer releases also name the
        // family. Unlike CustomFallbackBuilder, these bootstrap methods do not
        // dereference an uninitialized DEFAULT. They own all native allocations.
        Method factory;
        try {
            factory = Typeface.class.getDeclaredMethod("createFromFamilies", String.class, FontFamily[].class);
        } catch (NoSuchMethodException olderAndroid) {
            factory = Typeface.class.getDeclaredMethod("createFromFamilies", FontFamily[].class);
        }
        factory.setAccessible(true);
        return (Typeface) (factory.getParameterTypes().length == 2
                ? factory.invoke(null, "tunnel-layout", chain)
                : factory.invoke(null, (Object) chain));
    }

    private static int priority(Font font) {
        String name = font.getFile() == null ? "" : font.getFile().getName().toLowerCase(Locale.ROOT);
        int group = name.contains("roboto") ? 0
                : name.contains("cjk") || name.contains("sanssc") || name.contains("fallback") ? 1
                : name.contains("emoji") || name.contains("symbol") ? 2 : 3;
        return group * 10000 + Math.abs(font.getStyle().getWeight() - 400)
                + font.getStyle().getSlant() * 1000;
    }

    static boolean isReady() { return defaultTypeface != null; }

    /** Null means callers must omit ALL text drawing and font measurement. */
    static Typeface typeface() { return defaultTypeface; }
}
