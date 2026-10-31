package com.rmtad.x;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-process state only. Everything here is static memory, so it resets
 * automatically when the app process dies. Nothing is written to disk.
 */
public final class AdSession {

    private static volatile boolean shown;          // popup finished (action/close/later/cancel)
    private static volatile boolean fetchDone;      // a usable config was obtained this process
    private static final AtomicBoolean fetching = new AtomicBoolean(false);
    private static final AtomicBoolean showing = new AtomicBoolean(false);
    private static final AtomicBoolean redirecting = new AtomicBoolean(false);

    private AdSession() {
    }

    public static boolean isShown() {
        return shown;
    }

    public static void markShown() {
        shown = true;
    }

    public static boolean isShowing() {
        return showing.get();
    }

    /** True if the caller may start a network request (none running, none succeeded yet). */
    public static boolean beginFetch() {
        if (fetchDone) return false;
        return fetching.compareAndSet(false, true);
    }

    public static void endFetch(boolean gotConfig) {
        if (gotConfig) fetchDone = true;
        fetching.set(false);
    }

    /** True if the caller may display a popup right now (none visible). */
    public static boolean beginShow() {
        return !shown && showing.compareAndSet(false, true);
    }

    public static void endShow() {
        showing.set(false);
    }

    /** Only the first caller gets true: blocks duplicate redirects from repeated taps. */
    public static boolean beginRedirect() {
        return redirecting.compareAndSet(false, true);
    }
}
