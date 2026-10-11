package ee.joolinkviewer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
    private LinearLayout base,foundList,savedList,profilesList;
    private EditText nameInput,urlInput,userInput,passInput,onvifPortInput;
    private TextView status;
    private Button scanButton,profileButton;
    private volatile boolean destroyed;
    private final Set<String> shown=ConcurrentHashMap.newKeySet();

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setBackgroundColor(Ui.BG);
        base=Ui.vertical(this);
        base.setPadding(Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,36));
        scroll.addView(base);setContentView(scroll);
        base.setOnApplyWindowInsetsListener((v,insets)->{
            int top=android.os.Build.VERSION.SDK_INT>=30?
                insets.getInsets(android.view.WindowInsets.Type.statusBars()).top:insets.getSystemWindowInsetTop();
            int bottom=android.os.Build.VERSION.SDK_INT>=30?
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom:insets.getSystemWindowInsetBottom();
            v.setPadding(Ui.dp(this,18),Ui.dp(this,18)+top,Ui.dp(this,18),Ui.dp(this,36)+bottom);
            return insets;
        });
        base.requestApplyInsets();
        boolean remoteMode="internet".equals(getIntent().getStringExtra("connection_mode"));
        Ui.add(base,Ui.text(this,remoteMode?"INTERNET • MINU KAAMERAD":"KOHTVÕRK • KAAMERAD",24,true),this,0);
        TextView sub=Ui.text(this,"Kodu WiFi või VPN • ONVIF • salvestised",13,false);
        if(remoteMode)sub.setText("Kaugvaade VPN kaudu • või sisesta turvaline RTSP võrguaadress");
        sub.setTextColor(Ui.MUTED);Ui.add(base,sub,this,2);
        Ui.add(base,Ui.text(this,"1. Leia oma kaamera",19,true),this,16);
        scanButton=Ui.button(this,"Otsi automaatselt (2 etappi)");
        scanButton.setOnClickListener(v->scan());
        if(remoteMode){
            scanButton.setEnabled(false);
            scanButton.setText("WiFi otsing saadaval ainult kohalikus režiimis");
        }
        Ui.add(base,scanButton,this,8);
        status=Ui.text(this,remoteMode?
            "Internetirežiim: ava salvestatud kaamera. Vajalik on aktiivne Tailscale / muu VPN kodusesse võrku.":
            "Telefon ja kaamera peavad olema samas WiFi-võrgus.",13,false);
        status.setTextColor(Ui.MUTED);Ui.add(base,status,this,7);
        foundList=Ui.vertical(this);Ui.add(base,foundList,this,4);

        Ui.add(base,Ui.text(this,"2. Kaamera seaded",19,true),this,20);
        nameInput=Ui.input(this,"Kaamera nimi");nameInput.setText("Kaamera 1");Ui.add(base,nameInput,this,8);
        urlInput=Ui.input(this,"RTSP, RTSPs, HTTP, HTTPS või HLS URL");
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        Ui.add(base,urlInput,this,8);
        userInput=Ui.input(this,"Kaamera kasutajanimi (vajadusel)");Ui.add(base,userInput,this,8);
        passInput=Ui.input(this,"Kaamera parool (ei salvestata)");
        passInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Ui.add(base,passInput,this,8);
        onvifPortInput=Ui.input(this,"ONVIF port (tavaliselt 80)");
        onvifPortInput.setInputType(InputType.TYPE_CLASS_NUMBER);onvifPortInput.setText("80");
        Ui.add(base,onvifPortInput,this,8);
        TextView formats=Ui.text(this,"Toetatud lingid: RTSP (H.264/H.265), RTSPs, HTTP MJPEG, HLS (.m3u8), kui kaamera neid pakub.",12,false);
        formats.setTextColor(Ui.MUTED);Ui.add(base,formats,this,5);
        profileButton=Ui.secondary(this,"Leia ONVIF põhivoog ja alamvoog");
        profileButton.setOnClickListener(v->findProfiles());Ui.add(base,profileButton,this,11);
        profilesList=Ui.vertical(this);Ui.add(base,profilesList,this,4);
        Button play=Ui.button(this,"▶ Ava kaamerapilt ja juhtimine");
        play.setOnClickListener(v->play());Ui.add(base,play,this,10);
        Button save=Ui.secondary(this,"Salvesta kaamera loendisse");
        save.setOnClickListener(v->save());Ui.add(base,save,this,7);
        Button history=Ui.secondary(this,"▣ Vaata ajalugu ja salvestisi");
        history.setOnClickListener(v->openHistory());Ui.add(base,history,this,7);
        Ui.add(base,Ui.text(this,"3. Kaugvaade mobiilse internetiga",19,true),this,20);
        TextView remoteHelp=Ui.text(this,"Kodus vajab Tailscale alati töötavat võrguväravat (nt arvuti, ruuter või laadijas varutelefon). Värav jagab võrku 192.168.8.0/24. Telefonis ava Tailscale ja kasuta kaamerale sama RTSP-aadressi nagu kodus. Kaamera porte ei pea internetti avama.",13,false);
        remoteHelp.setTextColor(Ui.MUTED);Ui.add(base,remoteHelp,this,5);
        Button vpnGuide=Ui.secondary(this,"Tailscale'i turvalise kaugühenduse juhend");
        vpnGuide.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://tailscale.com/docs/features/subnet-routers/how-to/setup?tab=android"))));
        Ui.add(base,vpnGuide,this,8);
        Button checkRemote=Ui.button(this,"Kontrolli kaamera ühendust (ka 4G/5G)");
        checkRemote.setOnClickListener(v->checkRemoteReachability());
        Ui.add(base,checkRemote,this,8);
        TextView remoteFoot=Ui.text(this,"Kui mobiilse internetiga kontroll ebaõnnestub, on enamasti puudu koduvõrgu VPN-värav või selle alamvõrgu marsruut. Joolinki pilveühendust see äpp ei kasuta.",12,false);
        remoteFoot.setTextColor(Ui.MUTED);Ui.add(base,remoteFoot,this,4);
        Ui.add(base,Ui.text(this,"Minu kaamerad",19,true),this,20);
        savedList=Ui.vertical(this);Ui.add(base,savedList,this,5);
        redrawSaved();
        TextView footer=Ui.text(this,"Otsing skannib vaid telefoni kohalikku WiFi-võrku. Kui kiire RTSP otsing ei leia midagi, teeb rakendus teise ONVIF- ja laiendatud otsingu automaatselt. " +
                "Kaamera pööramine, öörežiim ning SD-kaardi arhiiv töötavad ainult siis, kui kaamera need ONVIF kaudu välja annab. Joolinki pilve ei ühendata.",12,false);
        footer.setTextColor(Ui.MUTED);Ui.add(base,footer,this,21);
    }
    private void notifyText(String s){if(!destroyed)runOnUiThread(()->{if(!destroyed)status.setText(s);});}
    private int onvifPort(){
        try{int p=Integer.parseInt(onvifPortInput.getText().toString());return p>=1&&p<=65535?p:80;}catch(Exception e){return 80;}
    }
    private void checkRemoteReachability(){
        String url=validatedUrl();
        if(url==null)return;
        Uri parsed=Uri.parse(url);
        String host=parsed.getHost();
        if(host==null){notifyText("Kaamera aadress puudub.");return;}
        int port=parsed.getPort();
        if(port<=0)port="https".equalsIgnoreCase(parsed.getScheme())?443:
                ("http".equalsIgnoreCase(parsed.getScheme())?80:554);
        final int targetPort=port;
        notifyText("Kontrollin TCP ühendust kaameraga "+host+":"+targetPort+" …");
        new Thread(()->{
            boolean connected=RtspNetwork.open(host,targetPort,2500);
            if(!destroyed)notifyText(connected?
                "Ühendus kaameraga "+host+":"+targetPort+" töötab! Ava videopilt. RTSP kasutajanime võib veel vaja minna.":
                "Ühendus "+host+":"+targetPort+" puudub. Kui oled 4G/5G võrgus, kontrolli Tailscale VPN-i ja koduse võrgu 192.168.8.0/24 marsruuti.");
        },"joolink-vpn-connection-test").start();
    }
    private void scan(){
        String base=RtspNetwork.wifiBase(this);
        if(base==null){notifyText("Ühenda kodusesse WiFi-võrku. VPN võib otsingut segada.");return;}
        scanButton.setEnabled(false);foundList.removeAllViews();shown.clear();
        notifyText("1/2: kiire RTSP otsing "+base+"1–254 …");
        new Thread(()->{
            final AtomicInteger first=new AtomicInteger();
            RtspNetwork.ScanCallback cb=new RtspNetwork.ScanCallback(){
                @Override public void found(String ip,int port){
                    String id="rtsp:"+ip+":"+port;
                    if(shown.add(id)){
                        first.incrementAndGet();
                        if(!destroyed)runOnUiThread(()->addCandidate(ip,port,false));
                    }
                }
                @Override public void onvif(String ip,int port,String service){
                    String id="onvif:"+ip+":"+port;
                    if(shown.add(id)){
                        if(!destroyed)runOnUiThread(()->addCandidate(ip,port,true));
                    }
                }
                @Override public void status(String msg){notifyText(msg);}
                @Override public void finish(String msg){notifyText(msg);}
            };
            int quick=RtspNetwork.scanHosts(base,cb);
            if(destroyed)return;
            if(quick==0){
                notifyText("Kiire otsing ei leidnud kaamerat. Alustan automaatselt põhjalikku otsingut …");
                int deep=RtspNetwork.deepScan(getApplicationContext(),base,cb);
                notifyText(deep==0?"Mõlemad otsingud lõppesid. Kaamera võib töötada vaid Joolinki pilve kaudu või olla teises võrgus.":
                        "Mõlemad etapid valmis: leitud "+deep+" täiendavat ühendust (kõik ei pruugi olla kaamerad).");
            }else{
                notifyText("Esimene otsing leidis "+quick+" võimalikku RTSP ühendust. Vajadusel saab otsingut uuesti teha.");
            }
            if(!destroyed)runOnUiThread(()->scanButton.setEnabled(true));
        },"joolink-two-stage-search").start();
    }
    private void addCandidate(String ip,int port,boolean onvif){
        LinearLayout card=Ui.card(this);
        Ui.add(card,Ui.text(this,(onvif?"ONVIF seade: ":"RTSP port: ")+ip+":"+port,15,true),this,0);
        Button use=Ui.secondary(this,onvif?"Kasuta ONVIF seadet":"Leia RTSP rada");
        Ui.add(card,use,this,6);
        use.setOnClickListener(v->{
            use.setEnabled(false);nameInput.setText("Kaamera "+ip.substring(ip.lastIndexOf('.')+1));
            if(onvif){
                onvifPortInput.setText(String.valueOf(port));
                urlInput.setText("rtsp://"+ip+":554/live/ch0");
                use.setEnabled(true);
                findProfiles();
            }else{
                new Thread(()->{
                    String stream=RtspNetwork.findPath(ip,port);
                    if(!destroyed)runOnUiThread(()->{
                        urlInput.setText(stream);use.setEnabled(true);
                        notifyText("Valitud "+stream+". Vajadusel sisesta kaamera parool või proovi ONVIF profiile.");
                    });
                },"rtsp-path-discovery").start();
            }
        });
        Ui.add(foundList,card,this,7);
    }
    private String selectedIp(){
        String url=urlInput.getText().toString().trim();
        try{
            Uri u=Uri.parse(url);
            String host=u.getHost();
            if(host!=null&&RtspNetwork.isPrivate(host))return host;
        }catch(Exception ignored){}
        Toast.makeText(this,"Sisesta esmalt kaamera kohaliku võrgu aadress (nt rtsp://192.168.1.10:554/live/ch0)",Toast.LENGTH_LONG).show();
        return null;
    }
    private void findProfiles(){
        String ip=selectedIp();if(ip==null)return;
        final int port=onvifPort();
        final String user=userInput.getText().toString().trim(),pass=passInput.getText().toString();
        profileButton.setEnabled(false);
        profilesList.removeAllViews();notifyText("Otsin ONVIF teenust: "+ip+" (proovin ka porti 8899) …");
        new Thread(()->{
            try{
                OnvifClient c=OnvifClient.autoConnect(ip,port,user,pass);
                if(!destroyed)runOnUiThread(()->{
                    profileButton.setEnabled(true);
                    onvifPortInput.setText(String.valueOf(c.devicePort()));
                    notifyText(c.streams.isEmpty()?"ONVIF port "+c.devicePort()+" leitud, aga RTSP vooge ei tagastatud.":
                        "ONVIF port "+c.devicePort()+": "+c.streams.size()+" videovoogu; PTZ="+c.ptzAvailable+"; öörežiim="+c.imagingAvailable);
                    for(OnvifClient.Stream s:c.streams){
                        Button choose=Ui.secondary(this,"Vali: "+s.name);
                        choose.setOnClickListener(v->{urlInput.setText(s.url);notifyText("Valitud "+s.name);});
                        Ui.add(profilesList,choose,this,5);
                    }
                });
            }catch(Exception e){if(!destroyed)runOnUiThread(()->{
                profileButton.setEnabled(true);
                notifyText("ONVIF ühendus ebaõnnestus: "+e.getMessage()+". Kontrolli kasutajanime, parooli või porti.");
            });}
        },"onvif-profiles").start();
    }
    private String validatedUrl(){
        String u=urlInput.getText().toString().trim();
        if(!RtspNetwork.validUrl(u)){
            Toast.makeText(this,"Sisesta korrektne RTSP / HTTP / HLS aadress.",Toast.LENGTH_LONG).show();return null;
        }
        return u;
    }
    private Intent extras(Class<?> cls){
        Intent i=new Intent(this,cls);
        String url=urlInput.getText().toString().trim();
        String user=userInput.getText().toString().trim(),pass=passInput.getText().toString();
        i.putExtra("camera",nameInput.getText().toString().trim());
        i.putExtra("url",RtspNetwork.validUrl(url)?RtspNetwork.withCredentials(url,user,pass):"");
        i.putExtra("camera_ip",Uri.parse(url).getHost());
        i.putExtra("onvif_port",onvifPort());i.putExtra("user",user);i.putExtra("pass",pass);
        return i;
    }
    private void play(){if(validatedUrl()!=null)startActivity(extras(PlayerActivity.class));}
    private void openHistory(){startActivity(extras(HistoryActivity.class));}
    private void save(){
        String url=validatedUrl();if(url==null)return;
        String name=nameInput.getText().toString().trim();if(name.isEmpty())name="Kaamera";
        String safe=RtspNetwork.stripCredentials(url);
        List<CameraStore.Camera> list=CameraStore.load(this);
        boolean replaced=false;
        for(int k=0;k<list.size();k++)if(list.get(k).url.equals(safe)){
            list.set(k,new CameraStore.Camera(name,safe,userInput.getText().toString().trim(),onvifPort()));
            replaced=true;break;
        }
        if(!replaced)list.add(new CameraStore.Camera(name,safe,userInput.getText().toString().trim(),onvifPort()));
        CameraStore.save(this,list);redrawSaved();
        Toast.makeText(this,"Kaamera salvestatud, parooli ei salvestatud.",Toast.LENGTH_SHORT).show();
    }
    private void redrawSaved(){
        savedList.removeAllViews();
        List<CameraStore.Camera> list=CameraStore.load(this);
        if(list.isEmpty()){Ui.add(savedList,Ui.text(this,"Salvestatud kaameraid pole.",13,false),this,2);return;}
        for(CameraStore.Camera c:list){
            LinearLayout item=Ui.card(this);
            Ui.add(item,Ui.text(this,c.name,16,true),this,0);
            TextView address=Ui.text(this,c.url,12,false);address.setTextColor(Ui.MUTED);
            Ui.add(item,address,this,2);
            LinearLayout buttons=new LinearLayout(this);buttons.setGravity(Gravity.CENTER_VERTICAL);
            Button choose=Ui.button(this,"Vali");
            choose.setOnClickListener(v->{
                nameInput.setText(c.name);urlInput.setText(c.url);userInput.setText(c.username);
                onvifPortInput.setText(String.valueOf(c.onvifPort));passInput.setText("");
                Toast.makeText(this,"Kaamera valitud; vajadusel sisesta parool.",Toast.LENGTH_SHORT).show();
            });
            buttons.addView(choose,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
            Button del=Ui.secondary(this,"Kustuta");
            del.setOnClickListener(v->{
                List<CameraStore.Camera> cur=CameraStore.load(this);
                cur.removeIf(x->x.url.equals(c.url));CameraStore.save(this,cur);redrawSaved();
            });
            LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(0,Ui.dp(this,46),1);
            dlp.leftMargin=Ui.dp(this,7);buttons.addView(del,dlp);
            Ui.add(item,buttons,this,5);Ui.add(savedList,item,this,8);
        }
    }
    @Override protected void onDestroy(){destroyed=true;super.onDestroy();}
}
