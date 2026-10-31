package com.rmtad.x;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Builds and shows the popup (poster or message layout) as a native Dialog. */
public final class AdUI {

    private static final int C_CARD = Color.parseColor("#1C1D24");
    private static final int C_TITLE = Color.parseColor("#FFFFFF");
    private static final int C_TEXT = Color.parseColor("#B9BDC9");
    private static final int C_ACCENT = Color.parseColor("#4F8CFF");
    private static final int C_ACCENT_DOWN = Color.parseColor("#3A6FD6");
    private static final int C_MUTED = Color.parseColor("#8E93A3");
    private static final int C_SCRIM = Color.parseColor("#99000000");

    private Dialog dialog;
    private Activity activity;
    private AdConfig cfg;
    private volatile boolean done;
    private float density = 1f;

    /**
     * @return false when there is nothing displayable (caller should not retry).
     * @throws RuntimeException on window problems (caller may retry later).
     */
    public boolean show(Activity act, AdConfig c, Bitmap image, Bitmap icon) {
        this.activity = act;
        this.cfg = c;

        boolean poster = c.isPoster() && image != null;   // image failed -> message fallback
        if (!poster && !c.hasText()) return false;

        DisplayMetrics dm = act.getResources().getDisplayMetrics();
        density = dm.density;
        int cardW = Math.min((int) (dm.widthPixels * 0.90f), dp(420));

        final boolean force = c.force;

        Dialog d = new Dialog(act);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout card = new FrameLayout(act);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_CARD);
        bg.setCornerRadius(dp(20));
        setBg(card, bg);

        ScrollView scroll = new ScrollView(act);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (poster) {
            ImageView iv = new ImageView(act);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setMaxHeight((int) (dm.heightPixels * 0.60f));
            iv.setImageBitmap(roundTop(image, dp(20) * image.getWidth() / (float) cardW));
            if (c.title.length() > 0) iv.setContentDescription(c.title);
            col.addView(iv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            addButtons(col, dp(16), dp(16), dp(16), dp(16));
        } else {
            int pad = dp(24);
            LinearLayout box = new LinearLayout(act);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            box.setPadding(pad, pad + dp(4), pad, dp(16));
            col.addView(box, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            if (icon != null) {
                ImageView iv = new ImageView(act);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setImageBitmap(icon);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(56), dp(56));
                lp.bottomMargin = dp(14);
                box.addView(iv, lp);
            }
            if (c.title.length() > 0) {
                TextView t = new TextView(act);
                t.setText(c.title);
                t.setTextColor(C_TITLE);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                t.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(10);
                box.addView(t, lp);
            }
            if (c.message.length() > 0) {
                TextView m = new TextView(act);
                m.setText(c.message);
                m.setTextColor(C_TEXT);
                m.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                m.setLineSpacing(0f, 1.25f);
                m.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(6);
                box.addView(m, lp);
            }
            addButtons(box, 0, dp(14), 0, 0);
        }

        // Close (X) button: only in non-force mode and only when enabled remotely.
        if (!force && c.showClose) {
            TextView x = new TextView(act);
            x.setText("✕");
            x.setTextColor(Color.WHITE);
            x.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            x.setGravity(Gravity.CENTER);
            x.setContentDescription("Close");
            GradientDrawable xb = new GradientDrawable();
            xb.setShape(GradientDrawable.OVAL);
            xb.setColor(C_SCRIM);
            setBg(x, xb);
            x.setClickable(true);
            x.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finishByUser();
                }
            });
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(36), dp(36),
                    Gravity.TOP | Gravity.END);
            lp.topMargin = dp(10);
            lp.rightMargin = dp(10);
            card.addView(x, lp);
        }

        d.setContentView(card, new ViewGroup.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT));

        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(cardW, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.72f);
        }

        // Dismissal rules.
        d.setCancelable(!force);
        d.setCanceledOnTouchOutside(!force);
        if (force) {
            d.setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface di, int keyCode, KeyEvent event) {
                    return keyCode == KeyEvent.KEYCODE_BACK;   // swallow Back
                }
            });
        } else {
            // Back button / outside touch -> counts as a user dismissal for this session.
            d.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface di) {
                    finishByUser();
                }
            });
        }

        dialog = d;
        d.show();
        return true;
    }

    /** Dismiss without marking the ad as shown (used when the host Activity is destroyed). */
    public void dismissSilently() {
        try {
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
        } catch (Throwable ignored) {
        }
        dialog = null;
        activity = null;
        AdSession.endShow();
    }

    // ------------------------------------------------------------- internals

    private void addButtons(LinearLayout parent, int l, int t, int r, int b) {
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        wrap.setPadding(l, t, r, b);

        TextView action = new TextView(activity);
        action.setText(cfg.buttonText);
        action.setTextColor(Color.WHITE);
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        action.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        action.setGravity(Gravity.CENTER);
        action.setClickable(true);
        setBg(action, pill(C_ACCENT, C_ACCENT_DOWN, dp(14)));
        action.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAction();
            }
        });
        wrap.addView(action, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        // Later button: non-force mode only, and only when enabled remotely.
        if (!cfg.force && cfg.showLater) {
            TextView later = new TextView(activity);
            later.setText(cfg.laterText);
            later.setTextColor(C_MUTED);
            later.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            later.setGravity(Gravity.CENTER);
            later.setClickable(true);
            later.setPadding(dp(16), dp(10), dp(16), dp(6));
            later.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finishByUser();
                }
            });
            wrap.addView(later, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        parent.addView(wrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void onAction() {
        if (done) return;
        if (!AdSession.beginRedirect()) return;       // repeated taps
        done = true;
        try {
            if (cfg.hasValidRedirect() && activity != null) {
                RemoteAd.openUrl(activity, cfg.redirectUrl);
            }
            // Invalid redirect: the button just dismisses, so a forced popup can never trap the user.
        } catch (Throwable ignored) {
        }
        closeAndMarkShown();
    }

    private void finishByUser() {
        if (done) return;
        done = true;
        closeAndMarkShown();
    }

    private void closeAndMarkShown() {
        AdSession.markShown();
        try {
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
        } catch (Throwable ignored) {
        }
        dialog = null;
        activity = null;
        AdSession.endShow();
        RemoteAd.onUiClosed();
    }

    private int dp(int v) {
        return Math.round(v * density);
    }

    private static void setBg(View v, Drawable d) {
        if (Build.VERSION.SDK_INT >= 16) v.setBackground(d);
        else v.setBackgroundDrawable(d);
    }

    private static Drawable pill(int normal, int pressed, float radius) {
        GradientDrawable n = new GradientDrawable();
        n.setColor(normal);
        n.setCornerRadius(radius);
        GradientDrawable p = new GradientDrawable();
        p.setColor(pressed);
        p.setCornerRadius(radius);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, p);
        s.addState(new int[]{}, n);
        return s;
    }

    /** Rounds only the top corners so the poster follows the card's corner shape. */
    private static Bitmap roundTop(Bitmap src, float radiusPx) {
        try {
            int w = src.getWidth();
            int h = src.getHeight();
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            Path path = new Path();
            path.addRoundRect(new RectF(0, 0, w, h),
                    new float[]{radiusPx, radiusPx, radiusPx, radiusPx, 0, 0, 0, 0},
                    Path.Direction.CW);
            cv.drawPath(path, p);
            return out;
        } catch (Throwable t) {
            return src;
        }
    }
}
