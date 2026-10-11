package ee.joolinkviewer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG = Color.rgb(10, 16, 25);
    static final int PANEL = Color.rgb(23, 33, 47);
    static final int PRIMARY = Color.rgb(64, 196, 153);
    static final int TEXT = Color.rgb(232, 240, 247);
    static final int MUTED = Color.rgb(164, 179, 195);

    private Ui() {}
    static int dp(Context c, int n) { return (int) (n * c.getResources().getDisplayMetrics().density + .5f); }

    static GradientDrawable bg(int color, Context ctx, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(ctx, radius));
        return d;
    }
    static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    static TextView text(Context c, String s, int sp, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(c, 5), 0, dp(c, 5));
        return t;
    }
    static Button button(Context c, String s) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(BG);
        b.setBackground(bg(PRIMARY, c, 12));
        b.setPadding(dp(c, 8), dp(c, 5), dp(c, 8), dp(c, 5));
        return b;
    }
    static Button secondary(Context c, String s) {
        Button b = button(c, s);
        b.setBackground(bg(PANEL, c, 12));
        b.setTextColor(TEXT);
        return b;
    }
    static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setSingleLine(true);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setTextSize(15);
        e.setHint(hint);
        e.setBackground(bg(PANEL, c, 10));
        e.setPadding(dp(c, 13), dp(c, 12), dp(c, 13), dp(c, 12));
        return e;
    }
    static void add(LinearLayout parent, View child, Context ctx, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(ctx, top);
        parent.addView(child, p);
    }
    static LinearLayout card(Context ctx) {
        LinearLayout l = vertical(ctx);
        l.setPadding(dp(ctx, 13), dp(ctx, 13), dp(ctx, 13), dp(ctx, 13));
        l.setBackground(bg(PANEL, ctx, 14));
        return l;
    }
    static void singleLine(TextView t) { t.setGravity(Gravity.CENTER_VERTICAL); }
}
