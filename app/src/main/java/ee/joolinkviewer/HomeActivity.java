package ee.joolinkviewer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;

/** Starts with an explicit connection-mode choice. */
public class HomeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = Ui.vertical(this);
        root.setPadding(Ui.dp(this,18),Ui.dp(this,20),Ui.dp(this,18),Ui.dp(this,30));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int top=android.os.Build.VERSION.SDK_INT>=30?
                insets.getInsets(android.view.WindowInsets.Type.statusBars()).top:insets.getSystemWindowInsetTop();
            int bottom=android.os.Build.VERSION.SDK_INT>=30?
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom:insets.getSystemWindowInsetBottom();
            v.setPadding(Ui.dp(this,18),Ui.dp(this,20)+top,Ui.dp(this,18),Ui.dp(this,30)+bottom);
            return insets;
        });
        scroll.addView(root);
        setContentView(scroll);
        root.requestApplyInsets();
        Ui.add(root,Ui.text(this,"JOOLINK VIEWER",27,true),this,15);
        TextView subtitle=Ui.text(this,"Vali, kuidas soovid kaameraga ühenduda",15,false);
        subtitle.setTextColor(Ui.MUTED);
        Ui.add(root,subtitle,this,10);

        LinearLayout local=Ui.card(this);
        Ui.add(local,Ui.text(this,"⌂  Kohalik ühendus",22,true),this,0);
        TextView localText=Ui.text(this,
          "Telefon ja kaamera on samas WiFi-võrgus. Otsi kaameraid, ava RTSP-videopilt, ONVIF juhtimine ja kohalikud salvestised.",14,false);
        localText.setTextColor(Ui.MUTED);
        Ui.add(local,localText,this,6);
        Button openLocal=Ui.button(this,"Ava kohalikud kaamerad");
        openLocal.setOnClickListener(v->{
            Intent i=new Intent(this,MainActivity.class);
            i.putExtra("connection_mode","local");
            startActivity(i);
        });
        Ui.add(local,openLocal,this,12);
        Ui.add(root,local,this,24);

        LinearLayout cloud=Ui.card(this);
        Ui.add(cloud,Ui.text(this,"◎  Internet",22,true),this,0);
        TextView cloudText=Ui.text(this,
          "Vaatamine mobiilse internetiga. Vali turvaline VPN-kaugühendus või Joolinki ametlik pilvekonto.",14,false);
        cloudText.setTextColor(Ui.MUTED);
        Ui.add(cloud,cloudText,this,6);
        Button openInternet=Ui.button(this,"Ava internetiühendused");
        openInternet.setOnClickListener(v->startActivity(new Intent(this,InternetActivity.class)));
        Ui.add(cloud,openInternet,this,12);
        Ui.add(root,cloud,this,14);

        TextView foot=Ui.text(this,
          "Kohalik RTSP ja VPN-kaugvaade kasutavad sinu kaamera enda videovoogu. Joolinki konto ühendamine meie rakendusse nõuab tootja autentimise liidest, mida pole kinnitatud.",12,false);
        foot.setTextColor(Ui.MUTED);
        Ui.add(root,foot,this,20);
    }
}
