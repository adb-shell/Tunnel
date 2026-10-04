package com.tunnel.adbhelper;

import android.app.UiAutomation;
import android.view.InputEvent;
import java.lang.reflect.Method;

/** Keep shell input independent of hierarchy queries and target-app draw completion. */
final class InputInjector {
    // AOSP InputManager modes: acknowledge dispatch, not completion of the
    // application's UI callback (which may be busy serving a hierarchy request).
    private static final int ASYNC = 0, WAIT_FOR_RESULT = 1;
    private final Object inputManager;
    private final Method directInjection;
    private final Method withoutAnimationWait;

    InputInjector() {
        Object manager = null;
        Method direct = null;
        for (String name : new String[]{"android.hardware.input.InputManagerGlobal",
                "android.hardware.input.InputManager"}) {
            try {
                Class<?> type = Class.forName(name);
                Method candidate = type.getMethod("injectInputEvent", InputEvent.class, int.class);
                Object instance = type.getMethod("getInstance").invoke(null);
                if (instance != null) { manager = instance; direct = candidate; break; }
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                // Older Android uses InputManager; some ROMs expose neither.
            }
        }
        inputManager = manager;
        directInjection = direct;
        Method method = null;
        try {
            // AOSP recent Android: the public two-argument API always waits
            // for animations. This overload retains input transaction sync
            // while allowing a streaming controller to skip animation waits.
            method = UiAutomation.class.getMethod("injectInputEvent", InputEvent.class,
                    boolean.class, boolean.class);
        } catch (NoSuchMethodException | SecurityException olderFramework) {
            // Android 11 exposes only the two-argument API.
        }
        withoutAnimationWait = method;
    }

    boolean inject(UiAutomation automation, InputEvent event, boolean sync) {
        if (directInjection != null) {
            try {
                // Same shell UID/INJECT_EVENTS enforcement as UiAutomation.
                // No node hit-test, accessibility ACTION_CLICK or extra grant.
                return Boolean.TRUE.equals(directInjection.invoke(inputManager, event,
                        sync ? WAIT_FOR_RESULT : ASYNC));
            } catch (ReflectiveOperationException | RuntimeException rejected) {
                // Do not resend an event that may already have been dispatched.
                return false;
            }
        }
        if (withoutAnimationWait == null) return automation.injectInputEvent(event, sync);
        try {
            return Boolean.TRUE.equals(withoutAnimationWait.invoke(automation, event, sync, false));
        } catch (ReflectiveOperationException | RuntimeException rejected) {
            // Do not inject twice after an uncertain Binder result.
            return false;
        }
    }
}
