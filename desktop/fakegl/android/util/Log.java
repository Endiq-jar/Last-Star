package android.util;

/**
 * Desktop stand-in for android.util.Log: the real class is a native stub that
 * throws off-device, which would hide any code path that logs. Warnings and
 * errors are echoed so a degraded start-up is visible in test output.
 */
public final class Log {
    public static int i(String tag, String msg) {
        return 0;
    }

    public static int w(String tag, String msg) {
        System.out.println("  log/w " + tag + ": " + msg);
        return 0;
    }

    public static int w(String tag, String msg, Throwable t) {
        System.out.println("  log/w " + tag + ": " + msg + " (" + t + ")");
        return 0;
    }

    public static int e(String tag, String msg) {
        System.out.println("  log/e " + tag + ": " + msg);
        return 0;
    }

    public static int e(String tag, String msg, Throwable t) {
        System.out.println("  log/e " + tag + ": " + msg + " (" + t + ")");
        return 0;
    }

    public static int d(String tag, String msg) {
        return 0;
    }

    public static int v(String tag, String msg) {
        return 0;
    }
}
