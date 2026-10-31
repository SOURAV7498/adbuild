package com.rmtad.x;

import android.app.Activity;
import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Remote ad manager. One call from the launcher Activity:
 *
 *     RemoteAd.init(activity, "https://pastebin.com/raw/XXXXXXXX");
 *
 * Fetches the JSON on a background thread, waits the configured delay, then shows
 * the popup on the main thread. All failures are swallowed (the app never crashes).
 */
public final class RemoteAd {

    private static final String PREFS = "rmtad_cache_v1";
    private static final long CACHE_TTL_MS = 24L * 60L * 60L * 1000L;
    private static final int TIMEOUT_MS = 10000;
    private static final int MAX_TEXT_BYTES = 64 * 1024;
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_POSTER_DIM = 1280;
    private static final int MAX_ICON_DIM = 256;
    private static final int MAX_ATTACH_RETRIES = 20;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // All fields below are touched on the main thread only.
    private static boolean lifecycleRegistered;
    private static String hostClass;
    private static WeakReference<Activity> hostRef;
    private static AdConfig readyCfg;
    private static Bitmap readyImage;
    private static Bitmap readyIcon;
    private static long dueAt;
    private static Runnable pending;
    private static AdUI ui;

    private RemoteAd() {
    }

    // ------------------------------------------------------------------ API

    /** Call from the launcher Activity's onCreate (or later). Safe to call repeatedly. */
    public static void init(final Activity activity, final String configUrl) {
        try {
            if (activity == null || !AdConfig.isValidUrl(configUrl)) return;
            if (Looper.myLooper() != Looper.getMainLooper()) {
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        init(activity, configUrl);
                    }
                });
                return;
            }
            if (AdSession.isShown()) return;

            hostClass = activity.getClass().getName();
            hostRef = new WeakReference<Activity>(activity);
            registerLifecycle(activity);

            if (readyCfg != null) {
                // Config already loaded this process (e.g. launcher re-created).
                dueAt = SystemClock.elapsedRealtime() + readyCfg.delay * 1000L;
                schedule();
                return;
            }
            if (!AdSession.beginFetch()) return;       // request already running / finished

            final long start = SystemClock.elapsedRealtime();
            final Context app = activity.getApplicationContext();
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    boolean got = false;
                    try {
                        got = load(app, configUrl, start);
                    } catch (Throwable ignored) {
                    } finally {
                        AdSession.endFetch(got);
                    }
                }
            }, "rmtad-fetch");
            t.setDaemon(true);
            t.start();
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------- background loading

    /** @return true if a usable configuration (fresh or cached) was obtained. */
    private static boolean load(Context app, String url, long start) {
        String raw = null;
        try {
            raw = httpGetText(url);
        } catch (Throwable ignored) {
        }

        AdConfig cfg = AdConfig.parse(raw);
        if (cfg != null) {
            saveCache(app, raw);                       // also stores show=false, overriding old caches
        } else {
            cfg = loadCache(app);                      // only when the fresh fetch failed
        }
        if (cfg == null) return false;
        if (!cfg.show || !cfg.isDisplayable()) return true;   // master switch / nothing to show

        Bitmap img = null;
        Bitmap icon = null;
        if (cfg.isPoster()) {
            img = fetchBitmap(cfg.image, MAX_POSTER_DIM);
        } else if (AdConfig.isValidUrl(cfg.icon)) {
            icon = fetchBitmap(cfg.icon, MAX_ICON_DIM);
        }

        final AdConfig fc = cfg;
        final Bitmap fi = img;
        final Bitmap fic = icon;
        final long due = start + cfg.delay * 1000L;
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                readyCfg = fc;
                readyImage = fi;
                readyIcon = fic;
                dueAt = due;
                schedule();
            }
        });
        return true;
    }

    private static String httpGetText(String u) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestMethod("GET");
            c.setUseCaches(false);
            c.setRequestProperty("Cache-Control", "no-cache");
            c.setRequestProperty("Accept", "application/json, text/plain, */*");
            c.setRequestProperty("User-Agent", "RemoteAd/1.0");
            if (c.getResponseCode() != 200) return null;
            byte[] b = readAll(c.getInputStream(), MAX_TEXT_BYTES);
            if (b == null) return null;
            String s = new String(b, "UTF-8").trim();
            return s.length() == 0 ? null : s;
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static Bitmap fetchBitmap(String u, int maxDim) {
        HttpURLConnection c = null;
        try {
            if (!AdConfig.isValidUrl(u)) return null;
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("User-Agent", "RemoteAd/1.0");
            if (c.getResponseCode() != 200) return null;
            byte[] data = readAll(c.getInputStream(), MAX_IMAGE_BYTES);
            if (data == null || data.length == 0) return null;

            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int ss = 1;
            while (o.outWidth / ss > maxDim || o.outHeight / ss > maxDim) ss *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = ss;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Throwable t) {                         // includes OutOfMemoryError
            return null;
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Reads the stream fully; returns null if it exceeds {@code limit} bytes. */
    private static byte[] readAll(InputStream in, int limit) throws Exception {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > limit) return null;
            }
            return out.toByteArray();
        } finally {
            try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
    }

    // ---------------------------------------------------------------- cache

    private static void saveCache(Context app, String raw) {
        try {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("raw", raw)
                    .putLong("ts", System.currentTimeMillis())
                    .commit();
        } catch (Throwable ignored) {
        }
    }

    private static AdConfig loadCache(Context app) {
        try {
            SharedPreferences sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long ts = sp.getLong("ts", 0L);
            long age = System.currentTimeMillis() - ts;
            if (ts <= 0 || age < 0 || age > CACHE_TTL_MS) return null;   // expired / clock jump
            return AdConfig.parse(sp.getString("raw", null));
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------- scheduling (main thread)

    private static void schedule() {
        try {
            if (readyCfg == null || AdSession.isShown() || AdSession.isShowing()) return;
            Activity a = host();
            if (a == null || isDead(a)) return;
            cancelPending();
            long wait = Math.max(0L, dueAt - SystemClock.elapsedRealtime());
            postShow(wait, 0);
        } catch (Throwable ignored) {
        }
    }

    private static void postShow(long wait, final int attempt) {
        pending = new Runnable() {
            @Override
            public void run() {
                pending = null;
                showNow(attempt);
            }
        };
        MAIN.postDelayed(pending, wait);
    }

    private static void showNow(int attempt) {
        try {
            if (readyCfg == null || AdSession.isShown() || AdSession.isShowing()) return;
            Activity a = host();
            if (a == null || isDead(a)) return;

            // Window not attached yet (very early) -> retry shortly.
            if (a.getWindow() == null || a.getWindow().getDecorView().getWindowToken() == null) {
                if (attempt < MAX_ATTACH_RETRIES) postShow(500L, attempt + 1);
                return;
            }
            if (!AdSession.beginShow()) return;

            AdUI u = new AdUI();
            boolean displayed;
            try {
                displayed = u.show(a, readyCfg, readyImage, readyIcon);
            } catch (Throwable t) {                    // window error: allow a later retry
                AdSession.endShow();
                return;
            }
            if (displayed) {
                ui = u;
            } else {                                   // nothing displayable: don't loop
                AdSession.endShow();
                AdSession.markShown();
                clearReady();
            }
        } catch (Throwable ignored) {
            AdSession.endShow();
        }
    }

    private static void cancelPending() {
        if (pending != null) {
            MAIN.removeCallbacks(pending);
            pending = null;
        }
    }

    private static Activity host() {
        return hostRef == null ? null : hostRef.get();
    }

    private static boolean isDead(Activity a) {
        if (a.isFinishing()) return true;
        return Build.VERSION.SDK_INT >= 17 && a.isDestroyed();
    }

    private static void clearReady() {
        readyCfg = null;
        readyImage = null;
        readyIcon = null;
        ui = null;
    }

    /** Called by AdUI (main thread) after the user finished with the popup. */
    static void onUiClosed() {
        cancelPending();
        clearReady();
    }

    // ------------------------------------------------------------- redirect

    /** Opens an http/https URL that came from the remote config. Never throws. */
    static boolean openUrl(Activity a, String url) {
        try {
            if (!AdConfig.isValidUrl(url)) return false;
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            a.startActivity(i);
            return true;
        } catch (ActivityNotFoundException e) {
            try {
                Toast.makeText(a, "No app found to open this link", Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------ lifecycle

    private static void registerLifecycle(Activity a) {
        if (lifecycleRegistered) return;
        try {
            Application app = a.getApplication();
            if (app != null) {
                app.registerActivityLifecycleCallbacks(new Life());
                lifecycleRegistered = true;
            }
        } catch (Throwable ignored) {
        }
    }

    private static final class Life implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityCreated(Activity a, Bundle b) {
        }

        @Override
        public void onActivityStarted(Activity a) {
        }

        @Override
        public void onActivityResumed(Activity a) {
            try {
                if (hostClass != null && hostClass.equals(a.getClass().getName())) {
                    hostRef = new WeakReference<Activity>(a);
                    if (readyCfg != null) schedule();
                }
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void onActivityPaused(Activity a) {
        }

        @Override
        public void onActivityStopped(Activity a) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity a, Bundle b) {
        }

        @Override
        public void onActivityDestroyed(Activity a) {
            try {
                if (host() == a) {
                    cancelPending();                    // no callbacks left on a dead Activity
                    if (ui != null) {
                        ui.dismissSilently();           // not marked as shown: can reappear
                        ui = null;
                    }
                    hostRef = null;
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
