package com.tunnel.adbhelper;

import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.system.ErrnoException;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shell-only physical touchscreen grab. Framework-injected remote events do not
 * traverse these evdev devices. No device permissions or persistent settings change.
 * Closing every owned fd (including kernel process-exit cleanup) releases the grab.
 */
final class PhysicalTouchBlock implements AutoCloseable {
    private static final int EVIOCGRAB = 0x40044590;
    private static final int MAX_DEVICES = 128;
    private static final int MAX_ATTRIBUTE = 4096;
    private final List<FileDescriptor> held = new ArrayList<>();
    private List<String> identities = new ArrayList<>();
    private boolean requested;
    private boolean enabled;
    private String failure = "";
    private Method ioctl;
    private Constructor<?> ioctlArgument;

    /** Read-only discovery; capability is never proved by briefly blocking touch. */
    synchronized boolean probe() {
        try {
            resolveIoctl();
            boolean supported = !discover().isEmpty();
            failure = supported ? "" : "PHYSICAL_TOUCH_BLOCK_UNSUPPORTED";
            return supported;
        } catch (Exception unsupported) {
            failure = classify(unsupported);
            return false;
        }
    }

    synchronized boolean set(boolean value) {
        requested = value;
        if (!value) { release(); failure = ""; return true; }
        return refresh();
    }

    /** Revalidate after input topology changes; partial success is never enabled. */
    synchronized boolean refresh() {
        if (!requested) { release(); failure = ""; return true; }
        try {
            resolveIoctl();
            List<Target> targets = discover();
            List<String> current = new ArrayList<>();
            for (Target target : targets) current.add(target.identity);
            if (enabled && current.equals(identities)) { failure = ""; return true; }
            release();
            for (Target target : targets) {
                FileDescriptor fd = Os.open(target.path,
                        OsConstants.O_RDONLY | OsConstants.O_NONBLOCK
                                | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW, 0);
                held.add(fd); // Own it before any operation which may fail.
                StructStat actual = Os.fstat(fd);
                if (!OsConstants.S_ISCHR(actual.st_mode)
                        || actual.st_ino != target.inode || actual.st_rdev != target.device) {
                    throw new IOException("INPUT_DEVICE_CHANGED");
                }
                // Android 12+ ioctlInt(fd, cmd) passes &arg, not the integer arg.
                // Android 11's Int32Ref overload does the same. EVIOCGRAB checks
                // only whether this pointer is non-null, so both acquire a grab.
                // Never use ioctlInt(..., 0) to release: its pointer is non-null too.
                if (ioctlArgument == null) ioctl.invoke(null, fd, EVIOCGRAB);
                else ioctl.invoke(null, fd, EVIOCGRAB, ioctlArgument.newInstance(1));
            }
            // Reject a concurrent removal/replacement rather than claiming a
            // complete block from only a subset of the physical touch devices.
            List<String> verified = new ArrayList<>();
            for (Target target : discover()) verified.add(target.identity);
            if (!current.equals(verified)) throw new IOException("INPUT_DEVICE_CHANGED");
            identities = current;
            enabled = !held.isEmpty();
            failure = enabled ? "" : "PHYSICAL_TOUCH_BLOCK_UNSUPPORTED";
            return enabled;
        } catch (Exception unsupportedOrDenied) {
            release();
            failure = classify(unsupportedOrDenied);
            return false;
        }
    }

    synchronized boolean isEnabled() { return enabled; }
    synchronized String failureCode() { return failure; }

    @Override public synchronized void close() {
        requested = false;
        release();
        failure = "";
    }

    private static String classify(Throwable failure) {
        // Reflection and FileInputStream wrap errno failures. Traverse only a
        // bounded cause chain; never return paths, device names or exception text.
        for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) {
            if (failure instanceof ErrnoException) {
                int errno = ((ErrnoException) failure).errno;
                if (errno == OsConstants.EACCES || errno == OsConstants.EPERM) return "INPUT_PERMISSION_DENIED";
                if (errno == OsConstants.EBUSY) return "INPUT_DEVICE_BUSY";
                if (errno == OsConstants.ENODEV || errno == OsConstants.ENOENT) return "INPUT_DEVICE_CHANGED";
                if (errno == OsConstants.ENOTTY) return "INPUT_IOCTL_UNAVAILABLE";
            }
            if (failure instanceof NoSuchMethodException || failure instanceof IllegalAccessException
                    || failure instanceof ClassNotFoundException || failure instanceof InstantiationException
                    || failure instanceof SecurityException) return "INPUT_IOCTL_UNAVAILABLE";
            if (failure instanceof IOException) {
                String code = failure.getMessage();
                if (code != null) switch (code) {
                    case "SHELL_UID_REQUIRED": return "INPUT_PERMISSION_DENIED";
                    case "INPUT_DEVICE_CHANGED":
                    case "INPUT_ENUMERATION_UNAVAILABLE":
                    case "INPUT_IDENTITY_UNAVAILABLE":
                    case "INPUT_TOUCH_CLASSIFICATION_UNAVAILABLE":
                    case "INPUT_DEVICE_NOT_EXCLUSIVELY_TOUCH":
                    case "INPUT_BITMAP_FORMAT_UNSUPPORTED":
                    case "INPUT_BITMAP_INVALID":
                    case "INPUT_ATTRIBUTE_INVALID":
                    case "PHYSICAL_TOUCH_BLOCK_UNSUPPORTED": return code;
                    default: break;
                }
            }
        }
        return "PHYSICAL_TOUCH_BLOCK_FAILED";
    }

    private void release() {
        enabled = false;
        identities.clear();
        for (FileDescriptor fd : held) {
            try { Os.close(fd); } catch (Exception ignored) { }
        }
        held.clear();
    }

    private void resolveIoctl() throws Exception {
        if (ioctl != null) return;
        if (android.os.Process.myUid() != 2000) throw new IOException("SHELL_UID_REQUIRED");
        try {
            ioctl = Os.class.getDeclaredMethod("ioctlInt", FileDescriptor.class, int.class);
        } catch (NoSuchMethodException oldPlatform) {
            Class<?> reference = Class.forName("android.system.Int32Ref");
            Constructor<?> constructor = reference.getDeclaredConstructor(int.class);
            constructor.setAccessible(true);
            Method method = Os.class.getDeclaredMethod("ioctlInt", FileDescriptor.class, int.class, reference);
            method.setAccessible(true);
            ioctlArgument = constructor;
            ioctl = method;
        }
        ioctl.setAccessible(true);
    }

    private static final class Target {
        final String path;
        final String identity;
        final long inode;
        final long device;
        Target(String path, String identity, StructStat stat) {
            this.path = path; this.identity = identity;
            inode = stat.st_ino; device = stat.st_rdev;
        }
    }

    private static List<Target> discover() throws Exception {
        String[] names = new File("/dev/input").list();
        if (names == null || names.length > MAX_DEVICES * 2) throw new IOException("INPUT_ENUMERATION_UNAVAILABLE");
        Arrays.sort(names);
        int words = kernelWordBits();
        int count = 0;
        List<Target> targets = new ArrayList<>();
        for (String name : names) {
            if (!name.startsWith("event")) continue;
            if (!name.matches("event[0-9]{1,5}") || ++count > MAX_DEVICES) throw new IOException("INPUT_ENUMERATION_UNAVAILABLE");
            String path = "/dev/input/" + name;
            String sys = "/sys/class/input/" + name + "/device";
            String canonical = new File(sys).getCanonicalPath();
            if (!canonical.startsWith("/sys/devices/")) throw new IOException("INPUT_IDENTITY_UNAVAILABLE");
            // Read every device's capabilities. A permission/read failure must
            // fail the complete feature, never silently omit another touchscreen.
            BigInteger properties = bitmap(read(sys + "/properties"), words);
            BigInteger abs = bitmap(read(sys + "/capabilities/abs"), words);
            BigInteger keys = bitmap(read(sys + "/capabilities/key"), words);
            String busText = read(sys + "/id/bustype");
            if (!busText.matches("[0-9a-fA-F]{1,4}")) throw new IOException("INPUT_IDENTITY_UNAVAILABLE");
            int bus = Integer.parseInt(busText, 16);
            if (bus == 6) continue; // BUS_VIRTUAL: never grab remote virtual devices.
            boolean direct = properties.testBit(1); // INPUT_PROP_DIRECT
            boolean multiTouch = abs.testBit(0x35) && abs.testBit(0x36);
            boolean singleTouch = abs.testBit(0) && abs.testBit(1)
                    && (keys.testBit(0x14a) || keys.testBit(0x140)); // TOUCH / TOOL_PEN
            if (!(multiTouch || singleTouch)) continue;
            if (!direct) {
                if (properties.testBit(0)) continue; // Identified pointer/touchpad, not a screen.
                throw new IOException("INPUT_TOUCH_CLASSIFICATION_UNAVAILABLE");
            }
            if (bus == 0 || canonical.contains("/virtual/")
                    || keys.clearBit(143).and(BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE)).signum() != 0) {
                // Unknown/virtual origin, keyboard/power/volume combined with a digitizer:
                // do not disable an emergency hardware control to block touch.
                // KEY_WAKEUP alone is allowed for touchscreen double-tap wake;
                // KEY_POWER, KEY_SLEEP and volume/keyboard keys remain forbidden.
                throw new IOException("INPUT_DEVICE_NOT_EXCLUSIVELY_TOUCH");
            }
            StructStat stat = Os.lstat(path);
            if (!OsConstants.S_ISCHR(stat.st_mode)) throw new IOException("INPUT_IDENTITY_UNAVAILABLE");
            String identity = path + ':' + canonical + ':' + stat.st_ino + ':' + stat.st_rdev
                    + ':' + properties.toString(16) + ':' + abs.toString(16) + ':' + keys.toString(16) + ':' + bus;
            targets.add(new Target(path, identity, stat));
        }
        if (targets.isEmpty()) throw new IOException("PHYSICAL_TOUCH_BLOCK_UNSUPPORTED");
        return targets;
    }

    private static int kernelWordBits() throws Exception {
        // input_print_bitmap prints kernel unsigned longs, not process-size longs.
        String machine = Os.uname().machine;
        if (Arrays.asList("aarch64", "arm64", "x86_64", "riscv64").contains(machine)) return 64;
        // armv8l can be a compat personality on a 64-bit kernel: reject ambiguity.
        if (Arrays.asList("armv7l", "i386", "i686").contains(machine)) return 32;
        throw new IOException("INPUT_BITMAP_FORMAT_UNSUPPORTED");
    }

    private static BigInteger bitmap(String value, int wordBits) throws IOException {
        String[] words = value.split(" +");
        if (words.length == 0 || words.length > 32) throw new IOException("INPUT_BITMAP_INVALID");
        BigInteger result = BigInteger.ZERO;
        for (String word : words) {
            if (word.length() > wordBits / 4 || !word.matches("[0-9a-fA-F]+")) throw new IOException("INPUT_BITMAP_INVALID");
            result = result.shiftLeft(wordBits).or(new BigInteger(word, 16));
        }
        return result;
    }

    private static String read(String path) throws IOException {
        byte[] bytes = new byte[MAX_ATTRIBUTE + 1];
        int count = 0;
        try (FileInputStream stream = new FileInputStream(path)) {
            while (count < bytes.length) {
                int size = stream.read(bytes, count, bytes.length - count);
                if (size < 0) break;
                if (size == 0) throw new IOException("INPUT_ATTRIBUTE_INVALID");
                count += size;
            }
        }
        if (count == 0 || count > MAX_ATTRIBUTE) throw new IOException("INPUT_ATTRIBUTE_INVALID");
        return new String(bytes, 0, count, StandardCharsets.US_ASCII).trim();
    }
}
