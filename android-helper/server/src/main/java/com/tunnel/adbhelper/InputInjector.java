package com.tunnel.adbhelper;

import android.app.UiAutomation;
import android.view.InputEvent;
import java.lang.reflect.Method;

/** Keep input dispatch independent of ongoing window/overlay animations. */
final class InputInjector {
    private final Method withoutAnimationWait;

    InputInjector() {
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
        if (withoutAnimationWait == null) return automation.injectInputEvent(event, sync);
        try {
            return Boolean.TRUE.equals(withoutAnimationWait.invoke(automation, event, sync, false));
        } catch (ReflectiveOperationException | RuntimeException rejected) {
            // Do not inject twice after an uncertain Binder result.
            return false;
        }
    }
}
