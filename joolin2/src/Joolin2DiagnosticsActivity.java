package com.jooan.qiaoanzhilian.ui.activity.play;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Launcher shim: normal start is forwarded to the actual Joolink launcher.
 * After a fatal crash it displays only an on-device diagnostic and offers copy.
 */
public final class Joolin2DiagnosticsActivity extends Activity {
    private static final String STORE="joolin2_fatal_errors";
    private String report="";
    @Override protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        SharedPreferences prefs=getSharedPreferences(STORE,MODE_PRIVATE);
        String crash=prefs.getString("stack","");
        long at=prefs.getLong("time",0);
        if(crash!=null&&!crash.isEmpty()){
            report="Joolin2 Java crash · "+
                DateFormat.getDateTimeInstance().format(new Date(at))+"\n\n"+crash;
        }else{
            report=latestNativeFailure(prefs);
        }
        if(report.isEmpty()) {
            report="Diagnostikaprotsess käivitati edukalt.\n"+
                "Varasemat Java- või Androidi krahhilogi ei leitud.\n\n"+
                "Vajuta PROOVI RAKENDUST UUESTI. Kui kaameraäpp sulgub, "+
                "ava Joolin2 v4 uuesti ja kopeeri siit veateade.";
        }
        showDiagnostics();
    }
    private String latestNativeFailure(SharedPreferences prefs){
        if(Build.VERSION.SDK_INT<30)return "";
        try{
            ActivityManager am=(ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
            if(am==null)return "";
            List<ApplicationExitInfo> exits=am.getHistoricalProcessExitReasons(getPackageName(),0,5);
            long lastShown=prefs.getLong("last_native_shown",0);
            long now=System.currentTimeMillis();
            for(ApplicationExitInfo entry:exits){
                int why=entry.getReason();
                long time=entry.getTimestamp();
                if((why==ApplicationExitInfo.REASON_CRASH||
                    why==ApplicationExitInfo.REASON_CRASH_NATIVE)&&
                    time>lastShown && now-time>=0 && now-time<60L*60*1000){
                    prefs.edit().putLong("last_native_shown",time).apply();
                    return "Joolin2 tõrge: "+(why==ApplicationExitInfo.REASON_CRASH_NATIVE?
                           "native SIGSEGV / protsessi krahh":"Androidi protsessi krahh")+
                           "\nAeg: "+DateFormat.getDateTimeInstance().format(new Date(time))+
                           "\nPõhjus: "+String.valueOf(entry.getDescription())+
                           "\n\nKui see kordub, on vaja Androidi logcat või põhjalikumat native-diagnostikat.";
                }
            }
        }catch(Throwable ignored){}
        return "";
    }
    private void launchOriginal(){
        try {
            ApplicationInfo app=getPackageManager().getApplicationInfo(
                getPackageName(),PackageManager.GET_META_DATA);
            String name=app.metaData==null?null:
                app.metaData.getString("joolin2.original_launcher");
            if(name==null||name.isEmpty())throw new IllegalStateException("Original launcher missing");
            Intent intent=new Intent(Intent.ACTION_MAIN);
            intent.setClassName(getPackageName(),name);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        }catch(Exception error){
            report="Joolin2 ei saanud avada originaalset kaamerarakendust:\n"+
                error.getClass().getName()+": "+error.getMessage();
            showDiagnostics();
        }
    }
    private int dp(int n){
        return Math.round(n*getResources().getDisplayMetrics().density);
    }
    private Button button(String label){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);return b;
    }
    private void showDiagnostics(){
        ScrollView scroller=new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setBackgroundColor(0xff121923);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18),dp(55),dp(18),dp(30));
        scroller.addView(content);
        setContentView(scroller);
        TextView header=new TextView(this);
        header.setText("Joolin2 · Veadiagnostika");
        header.setTextSize(24);header.setTextColor(Color.WHITE);
        content.addView(header);
        TextView instructions=new TextView(this);
        instructions.setText("See diagnostikaaken on eraldi protsessis. Vajuta Proovi rakendust uuesti. Kui Joolink sulgub, ava sama Joolin2 uuesti ning kopeeri veateade. Enne jagamist kontrolli, et tekstis ei ole paroole ega tokeneid.");
        instructions.setTextSize(15);
        instructions.setTextColor(0xffc5d3e1);
        instructions.setPadding(0,dp(12),0,dp(18));
        content.addView(instructions);
        Button copy=button("Kopeeri veateade");
        copy.setOnClickListener(v->{
            ClipboardManager board=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(board!=null)board.setPrimaryClip(ClipData.newPlainText("Joolin2 veateade",report));
        });content.addView(copy);
        Button retry=button("Proovi rakendust uuesti");
        retry.setOnClickListener(v->{
            getSharedPreferences(STORE,MODE_PRIVATE).edit()
                .remove("stack").remove("time").apply();
            launchOriginal();
        });content.addView(retry);
        TextView message=new TextView(this);
        message.setText(report);
        message.setTextIsSelectable(true);
        message.setTextSize(12);message.setTextColor(0xffdddddd);
        message.setPadding(0,dp(18),0,0);
        content.addView(message);
    }
}
