package com.rmtad.x;

import android.app.Activity;

/**
 * One-line entry point. The Pastebin raw link lives in CONFIG_URL below.
 *
 * Smali call to paste in MainActivity.onCreate (needs no extra register):
 *     invoke-static {p0}, Lcom/rmtad/x/Init;->run(Landroid/app/Activity;)V
 *
 * To change the link later without rebuilding: open this dex in MT Manager
 * (Dex Editor++), go to com/rmtad/x/Init, and edit the CONFIG_URL string.
 */
public final class Init {

    // <<< PUT YOUR PASTEBIN RAW LINK HERE >>>
    private static final String CONFIG_URL = "https://pastebin.com/raw/xZurMEtY";

    private Init() {
    }

    public static void run(Activity activity) {
        try {
            RemoteAd.init(activity, CONFIG_URL);
        } catch (Throwable ignored) {
        }
    }
}
