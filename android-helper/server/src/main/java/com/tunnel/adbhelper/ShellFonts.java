package com.tunnel.adbhelper;

import android.graphics.Typeface;

import java.lang.reflect.Field;

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
            // Only a fixed code, never font paths, vendor exception text or UI text.
            System.err.println("TUNNEL_ADB_FONTS:UNAVAILABLE");
        }
    }

    static boolean isReady() { return defaultTypeface != null; }

    /** Null means callers must omit ALL text drawing and font measurement. */
    static Typeface typeface() { return defaultTypeface; }
}
