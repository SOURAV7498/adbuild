package com.rmtad.x;

import android.net.Uri;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Immutable, validated remote configuration.
 * Missing / invalid fields fall back to safe defaults. "show" defaults to FALSE,
 * so a broken or incomplete config can never pop an ad up by accident.
 */
public final class AdConfig {

    public static final int DEFAULT_DELAY = 10;
    public static final int MAX_DELAY = 3600;

    public final boolean show;
    public final boolean force;
    public final boolean showLater;
    public final boolean showClose;
    public final int delay;
    public final String type;        // "poster" or "message"
    public final String image;       // poster image URL
    public final String icon;        // optional icon URL (message popup)
    public final String title;
    public final String message;
    public final String buttonText;
    public final String laterText;
    public final String redirectUrl;

    private AdConfig(JSONObject o) {
        show = optBool(o, "show", false);
        force = optBool(o, "force", false);
        showLater = optBool(o, "show_later", false);
        showClose = optBool(o, "show_close", false);
        delay = optDelay(o);

        String t = optStr(o, "type", 16).toLowerCase(Locale.US);
        type = "poster".equals(t) ? "poster" : "message";

        image = optStr(o, "image", 2048);
        icon = optStr(o, "icon", 2048);
        title = optStr(o, "title", 200);
        message = optStr(o, "message", 2000);

        String bt = optStr(o, "button_text", 40);
        buttonText = bt.length() == 0 ? "OPEN" : bt;
        String lt = optStr(o, "later_text", 40);
        laterText = lt.length() == 0 ? "Later" : lt;

        redirectUrl = optStr(o, "redirect_url", 2048);
    }

    /** Returns null when the text is not a JSON object. Never throws. */
    public static AdConfig parse(String raw) {
        try {
            if (raw == null) return null;
            String s = raw.trim();
            if (s.length() > 0 && s.charAt(0) == '﻿') s = s.substring(1).trim();
            if (s.length() == 0 || s.charAt(0) != '{') return null;
            return new AdConfig(new JSONObject(s));
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean isPoster() {
        return "poster".equals(type) && isValidUrl(image);
    }

    public boolean hasText() {
        return title.length() > 0 || message.length() > 0;
    }

    public boolean hasValidRedirect() {
        return isValidUrl(redirectUrl);
    }

    /** True if there is anything meaningful to display. */
    public boolean isDisplayable() {
        return isPoster() || hasText();
    }

    /** Only absolute http/https URLs with a host are accepted. */
    public static boolean isValidUrl(String u) {
        try {
            if (u == null) return false;
            u = u.trim();
            if (u.length() == 0 || u.length() > 2048) return false;
            Uri uri = Uri.parse(u);
            String s = uri.getScheme();
            String h = uri.getHost();
            return s != null
                    && (s.equalsIgnoreCase("http") || s.equalsIgnoreCase("https"))
                    && h != null && h.length() > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static boolean optBool(JSONObject o, String k, boolean def) {
        try {
            if (!o.has(k) || o.isNull(k)) return def;
            Object v = o.get(k);
            if (v instanceof Boolean) return ((Boolean) v).booleanValue();
            if (v instanceof Number) return ((Number) v).intValue() != 0;
            if (v instanceof String) {
                String s = ((String) v).trim().toLowerCase(Locale.US);
                if (s.equals("true") || s.equals("1") || s.equals("yes")) return true;
                if (s.equals("false") || s.equals("0") || s.equals("no")) return false;
            }
        } catch (Throwable ignored) {
        }
        return def;
    }

    private static int optDelay(JSONObject o) {
        try {
            if (!o.has("delay") || o.isNull("delay")) return DEFAULT_DELAY;
            Object v = o.get("delay");
            double d = -1;
            if (v instanceof Number) d = ((Number) v).doubleValue();
            else if (v instanceof String) d = Double.parseDouble(((String) v).trim());
            if (!(d > 0)) return DEFAULT_DELAY;      // zero, negative, NaN -> default
            if (d > MAX_DELAY) return MAX_DELAY;
            return (int) Math.round(d);
        } catch (Throwable t) {
            return DEFAULT_DELAY;
        }
    }

    private static String optStr(JSONObject o, String k, int max) {
        try {
            if (!o.has(k) || o.isNull(k)) return "";
            Object v = o.get(k);
            if (!(v instanceof String) && !(v instanceof Number) && !(v instanceof Boolean)) return "";
            String s = String.valueOf(v).trim();
            return s.length() > max ? s.substring(0, max) : s;
        } catch (Throwable t) {
            return "";
        }
    }
}
