package ee.joolinkviewer;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.InputType;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Collections;

public class RelayActivity extends Activity {
    private EditText source;
    private CheckBox boot,vpn;
    private TextView status,localLink,vpnLink;
    private SharedPreferences prefs;
    static String ip(boolean tailnet){
        String fallback=tailnet?"":"127.0.0.1";
        try{
            for(NetworkInterface network:Collections.list(NetworkInterface.getNetworkInterfaces())){
                if(!network.isUp()||network.isLoopback())continue;
                String name=network.getName().toLowerCase();
                for(InetAddress address:Collections.list(network.getInetAddresses())){
                    if(!(address instanceof Inet4Address))continue;
                    String found=address.getHostAddress();
                    if(tailnet){
                        if(RtspProxyCore.isTailnet(found))return found;
                    }else if(!name.startsWith("tun")&&RtspProxyCore.isLan(found)){
                        if(name.startsWith("wlan")||name.startsWith("eth"))return found;
                        fallback=found;
                    }
                }
            }
        }catch(Exception ignored){}
        return fallback;
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        prefs=getSharedPreferences(RelayService.PREFS,MODE_PRIVATE);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=Ui.vertical(this);
        root.setPadding(Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,32));
        scroll.addView(root);setContentView(scroll);
        TextView tag=Ui.text(this,"HOMEJOOLINK / SERVER 2.2",12,true);
        tag.setTextColor(Ui.PRIMARY);Ui.add(root,tag,this,0);
        Ui.add(root,Ui.text(this,"Kaamerasaatja",28,true),this,5);
        TextView desc=Ui.text(this,"Android TV või telefon • RTSP TCP • 24/7 server",14,false);
        desc.setTextColor(Ui.MUTED);Ui.add(root,desc,this,4);
        LinearLayout card=Ui.card(this);Ui.add(root,card,this,17);
        Ui.add(card,Ui.text(this,"Kaamera RTSP lähteaadress",17,true),this,0);
        source=Ui.input(this,"rtsp://192.168.8.108:5540/live/ch0");
        source.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        String previous=getIntent().getStringExtra("url");
        if(previous==null||previous.isEmpty())previous=prefs.getString("url","");
        if(previous!=null)source.setText(previous);
        Ui.add(card,source,this,7);
        boot=new CheckBox(this);boot.setText("Käivita automaatselt pärast boksi taaskäivitust");
        boot.setTextColor(Ui.TEXT);boot.setChecked(prefs.getBoolean("autostart",false));
        Ui.add(card,boot,this,8);
        vpn=new CheckBox(this);vpn.setText("Luba Tailscale VPN-vaatajad (100.64/10)");
        vpn.setTextColor(Ui.TEXT);vpn.setChecked(prefs.getBoolean("vpn",false));
        Ui.add(card,vpn,this,6);
        Button start=Ui.button(this,"▶ Käivita / taaskäivita saatja");
        start.setOnClickListener(v->startRelay());Ui.add(card,start,this,11);
        Button stop=Ui.secondary(this,"■ Peata saatja");
        stop.setOnClickListener(v->stopRelay());Ui.add(card,stop,this,7);
        status=Ui.text(this,"Olek: kontrollin...",13,false);
        status.setTextColor(Ui.MUTED);Ui.add(card,status,this,8);
        Button refresh=Ui.secondary(this,"↻ Värskenda serveri olekut");
        refresh.setOnClickListener(v->refresh());Ui.add(card,refresh,this,7);
        LinearLayout urls=Ui.card(this);Ui.add(root,urls,this,16);
        Ui.add(urls,Ui.text(this,"Ava teises seadmes",17,true),this,0);
        localLink=Ui.text(this,"",14,false);
        localLink.setTextColor(Ui.PRIMARY);Ui.add(urls,localLink,this,5);
        Button copyLocal=Ui.secondary(this,"Kopeeri koduvõrgu URL");
        copyLocal.setOnClickListener(v->copy(localLink.getText().toString()));
        Ui.add(urls,copyLocal,this,7);
        vpnLink=Ui.text(this,"",14,false);vpnLink.setTextColor(Ui.PRIMARY);
        Ui.add(urls,vpnLink,this,11);
        Button copyVpn=Ui.secondary(this,"Kopeeri Tailscale URL");
        copyVpn.setOnClickListener(v->{
            String ip=ip(true);
            if(ip.isEmpty())status.setText("Tailscale VPN IP pole saadaval.");
            else copy(vpnLink.getText().toString());
        });
        Ui.add(urls,copyVpn,this,7);
        Button check=Ui.secondary(this,"Kontrolli, kas kohalik port vastab");
        check.setOnClickListener(v->new Thread(()->{
            String result;
            try(Socket socket=new Socket("127.0.0.1",8554)){
                result="Port 8554 vastab • server kuulab";
            }catch(Exception e){result="Port 8554 ei vasta • "+e.getClass().getSimpleName();}
            final String text=result;runOnUiThread(()->status.setText(text));
        },"relay-port-check").start());
        Ui.add(urls,check,this,9);
        TextView safety=Ui.text(this,"Kaugvaatamiseks paigalda Tailscale nii boksi kui telefoni, ühenda samasse tailneti ning kasuta VPN URL-i. Ära suuna ruuteris porti 8554 internetti. Saatjal ei ole oma autentimist: ligipääsu piiravad privaatvõrk / Tailscale ja kaamera enda RTSP õigused. Kaamera parooli ei salvestata.",13,false);
        safety.setTextColor(Ui.MUTED);Ui.add(root,safety,this,18);
        Button back=Ui.secondary(this,"← Tagasi");
        back.setOnClickListener(v->finish());Ui.add(root,back,this,12);
        refresh();
    }
    private String path(){
        Uri uri=Uri.parse(source.getText().toString().trim());
        String p=uri.getEncodedPath();
        if(p==null||p.isEmpty())p="/";
        if(uri.getEncodedQuery()!=null)p+="?"+uri.getEncodedQuery();
        return p;
    }
    private void refresh(){
        localLink.setText("rtsp://"+ip(false)+":8554"+path());
        String tail=ip(true);
        vpnLink.setText(tail.isEmpty()?"Tailscale ühendus puudub":"rtsp://"+tail+":8554"+path());
        status.setText((RelayService.isLive()?"● Saatja töötab":"○ Saatja pole käivitatud")+
                " • "+prefs.getString("last_error",""));
    }
    private void copy(String content){
        ClipboardManager mgr=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        mgr.setPrimaryClip(ClipData.newPlainText("HomeJoolink RTSP",content));
        status.setText("RTSP URL kopeeritud");
    }
    private void startRelay(){
        String address=source.getText().toString().trim();
        try{
            Uri parsed=Uri.parse(address);
            if(!"rtsp".equalsIgnoreCase(parsed.getScheme())||!RtspProxyCore.isLan(parsed.getHost())){
                status.setText("Lähtekaamera peab olema kohalik RTSP IPv4 aadress.");return;
            }
            String withoutPassword=RtspNetwork.stripCredentials(address);
            prefs.edit().putString("url",withoutPassword).putBoolean("autostart",boot.isChecked())
                .putBoolean("vpn",vpn.isChecked()).putString("last_error","").apply();
            Intent command=new Intent(this,RelayService.class);
            command.setAction(RelayService.START);
            command.putExtra("url",address);
            startForegroundService(command);
            status.setText("Käivitan saatja...");
            refresh();
        }catch(Exception e){status.setText("Viga: "+e.getClass().getSimpleName()+": "+e.getMessage());}
    }
    private void stopRelay(){
        prefs.edit().putBoolean("autostart",false).apply();
        boot.setChecked(false);
        Intent command=new Intent(this,RelayService.class);
        command.setAction(RelayService.STOP);
        startService(command);status.setText("Peatan saatja...");
    }
}
