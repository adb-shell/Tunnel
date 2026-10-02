/*
 * Hidden API signatures adapted from scrcpy v4.1 SurfaceControl/DisplayControl.
 * Copyright (C) 2018 Genymobile; Copyright (C) 2018-2026 Romain Vimont.
 * Apache-2.0; see LICENSE.scrcpy and PROVENANCE.md.
 */
package com.tunnel.adbhelper;

import android.os.IBinder;
import java.lang.reflect.Method;

/** The physical display backing logical display 0 only. Never toggles POWER or unlocks. */
final class DisplayPower implements AutoCloseable {
    private IBinder token;
    private Method setPower;
    private boolean changed;

    boolean probe() {
        try {
            Class<?> surface = Class.forName("android.view.SurfaceControl");
            setPower = surface.getMethod("setDisplayPowerMode", IBinder.class, int.class);
            try { token = (IBinder) surface.getMethod("getInternalDisplayToken").invoke(null); }
            catch (ReflectiveOperationException ignored) {
                Class<?> global = Class.forName("android.hardware.display.DisplayManagerGlobal");
                Object manager = global.getMethod("getInstance").invoke(null);
                Object info = global.getMethod("getDisplayInfo", int.class).invoke(manager, 0);
                Object address = info.getClass().getField("address").get(info);
                long physicalId = (Long) address.getClass().getMethod("getPhysicalDisplayId").invoke(address);
                Class<?> control;
                try { surface.getMethod("getPhysicalDisplayToken", long.class); control = surface; }
                catch (NoSuchMethodException removed) {
                    // Android 14+ moves physical display lookup into the framework services jar.
                    Class<?> factory = Class.forName("com.android.internal.os.ClassLoaderFactory");
                    ClassLoader loader = (ClassLoader) factory.getDeclaredMethod("createClassLoader", String.class,
                            String.class, String.class, ClassLoader.class, int.class, boolean.class, String.class)
                            .invoke(null, "/system/framework/services.jar", null, null, ClassLoader.getSystemClassLoader(), 0, true, null);
                    control = loader.loadClass("com.android.server.display.DisplayControl");
                    Method load = Runtime.class.getDeclaredMethod("loadLibrary0", Class.class, String.class);
                    load.setAccessible(true); load.invoke(Runtime.getRuntime(), control, "android_servers");
                }
                token = (IBinder) control.getMethod("getPhysicalDisplayToken", long.class).invoke(null, physicalId);
            }
            return token != null;
        } catch (Throwable unavailable) { token = null; setPower = null; return false; }
    }

    boolean set(boolean on) {
        if (token == null || setPower == null) return false;
        try {
            if (on && !changed) return true; // Never wake a display this instance did not turn off.
            if (!on && !changed) {
                Class<?> global = Class.forName("android.hardware.display.DisplayManagerGlobal");
                Object manager = global.getMethod("getInstance").invoke(null);
                Object info = global.getMethod("getDisplayInfo", int.class).invoke(manager, 0);
                if (info.getClass().getField("state").getInt(info) != 2) return false;
            }
            setPower.invoke(null, token, on ? 2 : 0);
            changed = !on;
            return true;
        } catch (Exception failure) { return false; }
    }

    @Override public void close() { if (changed) set(true); token = null; setPower = null; }
}
