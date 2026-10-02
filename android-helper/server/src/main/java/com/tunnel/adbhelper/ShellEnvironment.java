/*
 * Adapted from scrcpy v4.1, commit 2926c06c5dc3064ae6d8db706f1a98a37cfcf3f0.
 * Copyright (C) 2018 Genymobile
 * Copyright (C) 2018-2026 Romain Vimont
 * Licensed under the Apache License, Version 2.0; see server/LICENSE.scrcpy.
 * Tunnel modifications: video-only environment, no audio/provider/input APIs.
 */
package com.tunnel.adbhelper;

import android.app.Application;
import android.app.Instrumentation;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Looper;
import android.os.Process;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/** Minimal shell process context required by vendor display/codec implementations. */
final class ShellEnvironment {
    private static final String SHELL_PACKAGE = "com.android.shell";

    private ShellEnvironment() { }

    static void prepare() throws ReflectiveOperationException {
        // Same quit-capable main looper setup as scrcpy Server.prepareMainLooper().
        Looper.prepare();
        Field mainLooper = Looper.class.getDeclaredField("sMainLooper");
        mainLooper.setAccessible(true);
        synchronized (Looper.class) {
            mainLooper.set(null, Looper.myLooper());
        }

        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Constructor<?> constructor = activityThreadClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object activityThread = constructor.newInstance();
        setField(activityThreadClass, null, "sCurrentActivityThread", activityThread);
        setField(activityThreadClass, activityThread, "mSystemThread", true);

        // Required on some Samsung Android 12+ devices. This is a vendor workaround,
        // not an authorization fallback: any later display/encoder failure stays fatal.
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                Class<?> configurationClass = Class.forName("android.app.ConfigurationController");
                Class<?> internalClass = Class.forName("android.app.ActivityThreadInternal");
                Constructor<?> configurationConstructor = configurationClass.getDeclaredConstructor(internalClass);
                configurationConstructor.setAccessible(true);
                setField(activityThreadClass, activityThread, "mConfigurationController",
                        configurationConstructor.newInstance(activityThread));
            } catch (ReflectiveOperationException ignored) {
                // Upstream also treats this device-specific workaround as optional.
            }
        }

        if (!"ONYX".equalsIgnoreCase(Build.BRAND)) {
            try {
                Class<?> bindClass = Class.forName("android.app.ActivityThread$AppBindData");
                Constructor<?> bindConstructor = bindClass.getDeclaredConstructor();
                bindConstructor.setAccessible(true);
                Object bindData = bindConstructor.newInstance();
                ApplicationInfo appInfo = new ApplicationInfo();
                appInfo.packageName = SHELL_PACKAGE;
                setField(bindClass, bindData, "appInfo", appInfo);
                setField(activityThreadClass, activityThread, "mBoundApplication", bindData);
            } catch (ReflectiveOperationException ignored) {
                // Do not print vendor objects, messages, or identifiers.
            }
        }

        Context systemContext = (Context) activityThreadClass.getDeclaredMethod("getSystemContext").invoke(activityThread);
        if (systemContext == null) {
            throw new IllegalStateException("SHELL_CONTEXT_UNAVAILABLE");
        }
        try {
            Application application = Instrumentation.newApplication(Application.class, new ShellContext(systemContext));
            setField(activityThreadClass, activityThread, "mInitialApplication", application);
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new IllegalStateException("SHELL_CONTEXT_UNAVAILABLE");
        }
    }

    private static void setField(Class<?> type, Object instance, String name, Object value)
            throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    /** This names the actual UID 2000 caller; it never changes process privileges. */
    private static final class ShellContext extends ContextWrapper {
        ShellContext(Context base) { super(base); }

        @Override
        public String getPackageName() { return SHELL_PACKAGE; }

        @Override
        public String getOpPackageName() { return SHELL_PACKAGE; }

        @Override
        public Context getApplicationContext() { return this; }

        @Override
        public Context createPackageContext(String packageName, int flags) { return this; }

        @Override
        public AttributionSource getAttributionSource() {
            return new AttributionSource.Builder(Process.SHELL_UID).setPackageName(SHELL_PACKAGE).build();
        }

        // Public on SDK 34; retained as an ordinary method for runtime API 30-33.
        @Override
        public int getDeviceId() { return 0; }
    }
}
