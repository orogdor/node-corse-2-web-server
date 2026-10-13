package ee.joolinkviewer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.InputType;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public class RelayActivity extends Activity {
    private EditText source;
    private TextView status,viewerUrl;
    static String localIp(){
        String fallback="127.0.0.1";
        try{
            for(NetworkInterface net:Collections.list(NetworkInterface.getNetworkInterfaces())){
                if(!net.isUp()||net.isLoopback())continue;
                String name=net.getName().toLowerCase();
                if(name.startsWith("tun")||name.startsWith("ppp"))continue;
                for(InetAddress addr:Collections.list(net.getInetAddresses())){
                    String ip=addr.getHostAddress();
                    if(addr instanceof Inet4Address && RtspNetwork.isPrivate(ip)){
                        if(name.startsWith("wlan")||name.startsWith("eth"))return ip;
                        fallback=ip;
                    }
                }
            }
        }catch(Exception ignored){}
        return fallback;
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=Ui.vertical(this);
        root.setPadding(Ui.dp(this,18),Ui.dp(this,21),Ui.dp(this,18),Ui.dp(this,35));
        scroll.addView(root);setContentView(scroll);
        TextView tag=Ui.text(this,"HOMEJOOLINK / RELAY LAB",12,true);
        tag.setTextColor(Ui.PRIMARY);Ui.add(root,tag,this,0);
        Ui.add(root,Ui.text(this,"Video saatja",29,true),this,4);
        TextView subtitle=Ui.text(this,"LAN RTSP/TCP edastus • prototüüp",14,false);
        subtitle.setTextColor(Ui.MUTED);Ui.add(root,subtitle,this,4);
        LinearLayout card=Ui.card(this);Ui.add(root,card,this,18);
        Ui.add(card,Ui.text(this,"Kaamera lähtevoog",17,true),this,0);
        source=Ui.input(this,"rtsp://192.168.8.202:554/live/ch0");
        source.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        String original=getIntent().getStringExtra("url");
        if(original!=null)source.setText(original);
        Ui.add(card,source,this,9);
        Button start=Ui.button(this,"▶  Käivita LAN saatja");
        start.setOnClickListener(v->startRelay());Ui.add(card,start,this,12);
        Button stop=Ui.secondary(this,"■  Peata saatja");
        stop.setOnClickListener(v->stopRelay());Ui.add(card,stop,this,8);
        status=Ui.text(this,"Saatja on välja lülitatud.",13,false);
        status.setTextColor(Ui.MUTED);Ui.add(card,status,this,8);
        LinearLayout endpoint=Ui.card(this);Ui.add(root,endpoint,this,16);
        Ui.add(endpoint,Ui.text(this,"Teises seadmes avatav aadress",16,true),this,0);
        viewerUrl=Ui.text(this,"",13,false);
        viewerUrl.setTextColor(Ui.PRIMARY);Ui.add(endpoint,viewerUrl,this,7);
        Button update=Ui.secondary(this,"Uuenda ühenduse aadressi");
        update.setOnClickListener(v->updateUrl());Ui.add(endpoint,update,this,9);
        updateUrl();
        TextView caveat=Ui.text(this,"See vahendab TCP RTSP voogu ainult kohalikus võrgus. Vajalik on töötav RTSP allikas ja TCP interleaved RTSP klient. UDP, tootja Joolinki pilve-P2P, kaugühenduse autentimine ja voogude ümberkodeerimine ei ole toetatud. Ära suuna porti 8554 avalikku internetti; võrguväliselt kasuta turvalist VPN-i. Testi kõigepealt enda seadmetega.",13,false);
        caveat.setTextColor(Ui.MUTED);Ui.add(root,caveat,this,18);
        Button back=Ui.secondary(this,"←  Kaamerad");
        back.setOnClickListener(v->finish());Ui.add(root,back,this,14);
    }
    private void updateUrl(){
        String original=source.getText().toString().trim();
        Uri uri=Uri.parse(original);
        String path=uri.getEncodedPath();
        if(path==null||path.isEmpty())path="/";
        if(uri.getEncodedQuery()!=null)path+="?"+uri.getEncodedQuery();
        viewerUrl.setText("rtsp://"+localIp()+":8554"+path);
    }
    private void startRelay(){
        String url=source.getText().toString().trim();
        Uri uri=Uri.parse(url);
        if(!"rtsp".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null){
            status.setText("Sisesta RTSP aadress (rtsp://...).");return;
        }
        Intent i=new Intent(this,RelayService.class);
        i.setAction(RelayService.START);
        i.putExtra("url",url);
        try{
            startForegroundService(i);
            status.setText("Saatja käivitub. Kontrolli teises seadmes RTSP aadressi.");
            updateUrl();
        }catch(Exception e){status.setText("Ei saanud saatjat käivitada: "+e.getClass().getSimpleName());}
    }
    private void stopRelay(){
        Intent i=new Intent(this,RelayService.class);i.setAction(RelayService.STOP);
        startService(i);status.setText("Saatja peatamise käsk saadetud.");
    }
}
