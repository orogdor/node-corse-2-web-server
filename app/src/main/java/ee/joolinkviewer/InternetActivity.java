package ee.joolinkviewer;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Remote access methods. The cloud-account flow opens the vendor app for genuine authentication. */
public class InternetActivity extends Activity {
    private static final String JOOLINK_APP="com.jooan.qiaoanzhilian.gp";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=Ui.vertical(this);
        root.setPadding(Ui.dp(this,17),Ui.dp(this,18),Ui.dp(this,17),Ui.dp(this,32));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int top=android.os.Build.VERSION.SDK_INT>=30?
                insets.getInsets(android.view.WindowInsets.Type.statusBars()).top:insets.getSystemWindowInsetTop();
            int bottom=android.os.Build.VERSION.SDK_INT>=30?
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom:insets.getSystemWindowInsetBottom();
            v.setPadding(Ui.dp(this,17),Ui.dp(this,18)+top,Ui.dp(this,17),Ui.dp(this,32)+bottom);
            return insets;
        });
        scroll.addView(root);
        setContentView(scroll);
        root.requestApplyInsets();
        Ui.add(root,Ui.text(this,"Internetiühendused",24,true),this,7);
        TextView sub=Ui.text(this,"Kaugvaatamiseks ei pea telefon olema koduses WiFi-võrgus.",14,false);
        sub.setTextColor(Ui.MUTED);
        Ui.add(root,sub,this,7);

        LinearLayout joolink=Ui.card(this);
        Ui.add(joolink,Ui.text(this,"Joolinki pilv ja konto",19,true),this,0);
        TextView official=Ui.text(this,
           "Sinu Joolinki kasutajakontole saab praegu turvaliselt sisse logida tootja enda rakenduses. Meie äpp ei toeta veel selle konto autentimist ega konto kaamerate kuvamist. Seetõttu ei küsi me siin Joolinki parooli.",14,false);
        official.setTextColor(Ui.MUTED);
        official.setText("APK-st tuvastatud Joolinki kontoliidese põhjal saab nüüd proovida otse tootja serverisse sisse logida. See on esialgu test: server võib nõuda tootja rakenduse lisatõendust. Parool saadetakse ainult tootja HTTPS-aadressile sinu nupuvajutusega.");
        Ui.add(joolink,official,this,6);
        Button nativeLogin=Ui.button(this,"Proovi meie äpis Joolinki sisselogimist");
        nativeLogin.setOnClickListener(v->startActivity(new Intent(this,JoolinkCloudLoginActivity.class)));
        Ui.add(joolink,nativeLogin,this,10);
        Button login=Ui.button(this,"Ava Joolink ja logi sisse");
        login.setOnClickListener(v->openOfficialApp());
        Ui.add(joolink,login,this,12);
        Button findBackend=Ui.secondary(this,"Uuri Joolinki servereid – ilma paroolita");
        findBackend.setOnClickListener(v->startActivity(new Intent(this,JoolinkApiInspector.class)));
        Ui.add(joolink,findBackend,this,8);
        TextView scanExplain=Ui.text(this,"Loe telefonis oleva Joolinki APK serveri- ja API-viiteid. Midagi ei saadeta internetti. See ei ole veel konto sisselogimine.",12,false);
        scanExplain.setTextColor(Ui.MUTED);
        Ui.add(joolink,scanExplain,this,5);
        Ui.add(root,joolink,this,17);

        LinearLayout vpn=Ui.card(this);
        Ui.add(vpn,Ui.text(this,"Minu kaamerad interneti kaudu",19,true),this,0);
        TextView vpnText=Ui.text(this,
           "Kui kodus on Tailscale'i või muu privaatse VPN-i võrguvärav, saad siin avada samad salvestatud RTSP-kaamerad nagu WiFi-ga. Video ja juhtimine avanevad meie rakenduses.",14,false);
        vpnText.setTextColor(Ui.MUTED);
        Ui.add(vpn,vpnText,this,7);
        Button camera=Ui.button(this,"Ava kaamerad VPN-i kaudu");
        camera.setOnClickListener(v->{
            Intent i=new Intent(this,MainActivity.class);
            i.putExtra("connection_mode","internet");
            startActivity(i);
        });
        Ui.add(vpn,camera,this,10);
        Button guide=Ui.secondary(this,"VPN-i seadistusjuhend");
        guide.setOnClickListener(v->openPage("https://tailscale.com/docs/features/subnet-routers/how-to/setup"));
        Ui.add(vpn,guide,this,7);
        Ui.add(root,vpn,this,12);

        LinearLayout tech=Ui.card(this);
        Ui.add(tech,Ui.text(this,"Jooani P2P / UID ühendus",18,true),this,0);
        TextView experimental=Ui.text(this,
            "Uurimisel: mõne Jooani kaamera UID kaudu on võimalik ühendus KP2P võrguga, kuid see nõuab kaamera autentimist ega ole sama mis Joolinki konto. See funktsioon pole selles versioonis sisse lülitatud.",13,false);
        experimental.setTextColor(Ui.MUTED);
        Ui.add(tech,experimental,this,5);
        Button info=Ui.secondary(this,"Vaata KP2P protokolli projekti");
        info.setOnClickListener(v->openPage("https://github.com/TomasReiff/jooan-kp2p-rtsp-bridge"));
        Ui.add(tech,info,this,7);
        Ui.add(root,tech,this,13);

        Button back=Ui.secondary(this,"← Tagasi");
        back.setOnClickListener(v->finish());
        Ui.add(root,back,this,15);
    }
    private void openOfficialApp() {
        Intent launch=getPackageManager().getLaunchIntentForPackage(JOOLINK_APP);
        if(launch!=null){
            startActivity(launch);
        }else{
            new android.app.AlertDialog.Builder(this)
                .setTitle("Joolink ei ole paigaldatud")
                .setMessage("Joolinki ametlik Androidi äpp ei ole sellel telefonil tuvastatav. Pilvekonto sisselogimist ei saa meie rakenduses veel teha. Kas soovid avada tootja äpi poe lehe?")
                .setNegativeButton("Tagasi",null)
                .setPositiveButton("Ava poe leht",(dialog,which)->openPage(
                    "https://play.google.com/store/apps/details?id="+JOOLINK_APP))
                .show();
        }
    }
    private void openPage(String url) {
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(ActivityNotFoundException ex){
            android.widget.Toast.makeText(this,"Brauserit ei õnnestunud avada.",android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
