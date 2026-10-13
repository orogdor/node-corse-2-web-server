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
    static final int BG=Color.rgb(8,13,23);
    static final int PANEL=Color.rgb(21,31,46);
    static final int PRIMARY=Color.rgb(75,215,178);
    static final int TEXT=Color.rgb(242,247,252);
    static final int MUTED=Color.rgb(158,174,194);
    private Ui(){}
    static int dp(Context c,int n){return (int)(n*c.getResources().getDisplayMetrics().density+.5f);}
    static GradientDrawable bg(int color,Context c,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c,radius));
        return d;
    }
    static LinearLayout vertical(Context c){
        LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    static TextView text(Context c,String s,int sp,boolean bold){
        TextView t=new TextView(c);t.setText(s);t.setTextColor(TEXT);t.setTextSize(sp);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setPadding(0,dp(c,4),0,dp(c,4));return t;
    }
    static Button button(Context c,String s){
        Button b=new Button(c);
        b.setText(s);b.setAllCaps(false);b.setTextSize(15);
        b.setTextColor(BG);b.setBackground(bg(PRIMARY,c,14));
        b.setPadding(dp(c,12),dp(c,5),dp(c,12),dp(c,5));b.setMinHeight(dp(c,46));return b;
    }
    static Button secondary(Context c,String s){
        Button b=button(c,s);b.setTextColor(TEXT);b.setBackground(bg(PANEL,c,14));return b;
    }
    static EditText input(Context c,String hint){
        EditText e=new EditText(c);e.setSingleLine(true);e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);e.setTextSize(15);e.setHint(hint);
        e.setBackground(bg(PANEL,c,12));
        e.setPadding(dp(c,14),dp(c,12),dp(c,14),dp(c,12));return e;
    }
    static void add(LinearLayout p,View child,Context c,int top){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(c,top);p.addView(child,lp);
    }
    static LinearLayout card(Context c){
        LinearLayout l=vertical(c);
        l.setPadding(dp(c,16),dp(c,14),dp(c,16),dp(c,15));
        GradientDrawable d=bg(PANEL,c,16);
        d.setStroke(dp(c,1),Color.rgb(43,59,80));
        l.setBackground(d);return l;
    }
    static void singleLine(TextView t){t.setGravity(Gravity.CENTER_VERTICAL);}
}
