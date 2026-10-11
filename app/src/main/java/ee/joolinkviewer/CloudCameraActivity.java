package ee.joolinkviewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.util.List;

/** Binds a cloud camera card to an authorized local RTSP/HTTP stream for viewing and ONVIF PTZ. */
public class CloudCameraActivity extends Activity {
    private String key,name;
    private EditText url,username,port;
    private TextView status;
    private LinearLayout discovered;
    private Button scanButton;
    private volatile boolean destroyed;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        key=getIntent().getStringExtra("device_key");
        name=getIntent().getStringExtra("camera_name");
        if(key==null||key.isEmpty()){finish();return;}
        if(name==null)name="Kaamera";
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=Ui.vertical(this);
        root.setPadding(Ui.dp(this,17),Ui.dp(this,18),Ui.dp(this,17),Ui.dp(this,38));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int top=android.os.Build.VERSION.SDK_INT>=30?
                insets.getInsets(android.view.WindowInsets.Type.statusBars()).top:insets.getSystemWindowInsetTop();
            int bottom=android.os.Build.VERSION.SDK_INT>=30?
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom:insets.getSystemWindowInsetBottom();
            v.setPadding(Ui.dp(this,17),Ui.dp(this,18)+top,Ui.dp(this,17),Ui.dp(this,38)+bottom);
            return insets;
        });
        scroll.addView(root);setContentView(scroll);root.requestApplyInsets();
        Ui.add(root,Ui.text(this,name,23,true),this,5);
        Button officialVideo=Ui.button(this,"▶ Ava kaugvideo ametlikus Joolinkis");
        officialVideo.setOnClickListener(v->OfficialJoolinkBridge.open(this,name));
        Ui.add(root,officialVideo,this,12);
        TextView explain=Ui.text(this,
          "Pilvekamera nimekiri tuleb Joolinki kontost. Ülemine nupp avab päris kaugvideo tootja ametlikus rakenduses. Siinne RTSP/HLS vaade töötab ainult siis, kui sul on eraldi toimiv videovoo aadress.",13,false);
        explain.setTextColor(Ui.MUTED);Ui.add(root,explain,this,5);
        Ui.add(root,Ui.text(this,"Eraldi RTSP/HLS videovoo seadistus",18,true),this,16);
        url=Ui.input(this,"rtsp://192.168.x.x:554/live/ch00_0");
        url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        Ui.add(root,url,this,7);
        username=Ui.input(this,"RTSP/ONVIF kasutajanimi (kui vaja)");
        Ui.add(root,username,this,8);
        port=Ui.input(this,"ONVIF port");
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        port.setText("80");Ui.add(root,port,this,8);
        JSONObject linked=SecureCloudStore.binding(this,key);
        if(linked!=null){
            url.setText(linked.optString("url",""));
            username.setText(linked.optString("user",""));
            port.setText(String.valueOf(linked.optInt("onvif_port",80)));
        }
        status=Ui.text(this,"Kaamera ühendust saab muuta ka hiljem.",13,false);
        status.setTextColor(Ui.MUTED);Ui.add(root,status,this,8);
        Button save=Ui.button(this,"Salvesta seos ja ava videopilt");
        save.setOnClickListener(v->saveAndPlay());Ui.add(root,save,this,11);
        Button remove=Ui.secondary(this,"Eemalda selle kaamera RTSP seos");
        remove.setOnClickListener(v->{
            try{SecureCloudStore.removeBinding(this,key);url.setText("");username.setText("");
                status.setText("Ühendus eemaldatud.");}
            catch(Exception e){status.setText("Ei saanud eemaldada: "+e.getMessage());}
        });
        Ui.add(root,remove,this,7);
        Ui.add(root,Ui.text(this,"Vali juba leitud kohalik kaamera",17,true),this,19);
        List<CameraStore.Camera> saved=CameraStore.load(this);
        if(saved.isEmpty()){
            TextView none=Ui.text(this,
              "Kohalikke kaameraid veel pole. Ava ☰ Menüü → Kohalik WiFi ja salvesta videovoog.",13,false);
            none.setTextColor(Ui.MUTED);Ui.add(root,none,this,4);
        }
        for(CameraStore.Camera c:saved){
            Button select=Ui.secondary(this,c.name+"  •  "+RtspNetwork.stripCredentials(c.url));
            select.setOnClickListener(v->{
                url.setText(c.url);username.setText(c.username);
                port.setText(String.valueOf(c.onvifPort));
                status.setText("Valitud "+c.name+". Vajuta „Salvesta seos ja ava videopilt”.");
            });
            Ui.add(root,select,this,7);
        }
        Button local=Ui.secondary(this,"Otsi kohalikke kaameraid");
        local.setOnClickListener(v->{
            Intent i=new Intent(this,MainActivity.class);
            i.putExtra("connection_mode","local");
            startActivity(i);
        });
        Ui.add(root,local,this,15);
        Ui.add(root,Ui.text(this,"Kiire RTSP-otsing selles WiFi-võrgus",17,true),this,17);
        TextView instructions=Ui.text(this,
            "Kui pilvekaamera video pole veel seotud, otsib see nupp võrgus töötavaid RTSP vooge. Vali õigesse kaamerasse kuuluv IP aadress. Pilvekaamerat ei saa ainult nime järgi automaatselt IP-ga samastada.",12,false);
        instructions.setTextColor(Ui.MUTED);Ui.add(root,instructions,this,4);
        scanButton=Ui.secondary(this,"Otsi videovoog ja vali ühendus");
        scanButton.setOnClickListener(v->scanStreams());
        Ui.add(root,scanButton,this,8);
        discovered=Ui.vertical(this);Ui.add(root,discovered,this,7);
        Button back=Ui.secondary(this,"← Kaamerate nimekirja");
        back.setOnClickListener(v->finish());Ui.add(root,back,this,7);
    }
    private void scanStreams(){
        String subnet=RtspNetwork.wifiBase(this);
        if(subnet==null){
            status.setText("RTSP võrguotsing töötab koduses WiFi-võrgus. Mobiilse internetiga kasuta salvestatud RTSP aadressi ja VPN-i.");
            return;
        }
        scanButton.setEnabled(false);
        discovered.removeAllViews();
        status.setText("Otsin RTSP porte võrgus "+subnet+"0/24 …");
        java.util.Set<String> found=java.util.concurrent.ConcurrentHashMap.newKeySet();
        RtspNetwork.ScanCallback callback=new RtspNetwork.ScanCallback(){
            @Override public void found(String ip,int rtspPort){
                String id=ip+":"+rtspPort;
                if(!found.add(id))return;
                if(!destroyed)runOnUiThread(()->{
                    Button pick=Ui.secondary(CloudCameraActivity.this,
                        "Vali RTSP: "+ip+":"+rtspPort);
                    pick.setOnClickListener(v->{
                        status.setText("Kontrollin RTSP aadressi "+id+" …");
                        new Thread(()->{
                            String address=RtspNetwork.findPath(ip,rtspPort);
                            if(!destroyed)runOnUiThread(()->{
                                url.setText(address);
                                status.setText("Leidsin voo "+address+". Kui pilt on õigest kaamerast, vajuta Salvesta seos ja ava videopilt.");
                            });
                        },"joolink-rtsp-candidate").start();
                    });
                    Ui.add(discovered,pick,CloudCameraActivity.this,5);
                });
            }
            @Override public void status(String message){
                if(!destroyed)runOnUiThread(()->status.setText(message));
            }
            @Override public void finish(String message){
                if(!destroyed)runOnUiThread(()->status.setText(message));
            }
        };
        new Thread(()->{
            int quick=RtspNetwork.scanHosts(subnet,callback);
            int total=quick;
            if(quick==0){
                callback.status("Kiire RTSP otsing ei leidnud kaameraid. Käivitan põhjalikuma otsingu …");
                total=RtspNetwork.deepScan(getApplicationContext(),subnet,callback);
            }
            final int matches=total;
            if(!destroyed)runOnUiThread(()->{
                scanButton.setEnabled(true);
                status.setText(matches==0?"Ühtegi vastavat porti ei leitud. Võib-olla toetab kaamera ainult Joolinki P2P pilve.": 
                    "Otsing valmis. Leitud "+matches+" võimalikku ühendust. Vali sobiv kaamera.");
            });
        },"joolink-cloud-local-rtsp-scanner").start();
    }
    @Override protected void onDestroy(){destroyed=true;super.onDestroy();}
    private int selectedPort(){
        try{int p=Integer.parseInt(port.getText().toString());return p>0&&p<65536?p:80;}
        catch(Exception e){return 80;}
    }
    private void saveAndPlay(){
        String address=url.getText().toString().trim();
        if(!RtspNetwork.validUrl(address)){
            status.setText("Sisesta korrektne RTSP/HTTP aadress. Joolinki konto token pole ise RTSP aadress.");
            return;
        }
        try{
            SecureCloudStore.saveBinding(this,key,address,username.getText().toString().trim(),selectedPort());
            JSONObject binding=SecureCloudStore.binding(this,key);
            openPlayer(this,name,binding);
        }catch(Exception e){status.setText("Salvestamise viga: "+e.getMessage());}
    }
    static void openPlayer(Activity context,String cameraName,JSONObject binding){
        if(binding==null)return;
        String stream=binding.optString("url","");
        String user=binding.optString("user","");
        int port=binding.optInt("onvif_port",80);
        if(!RtspNetwork.validUrl(stream)){
            Toast.makeText(context,"Videovoo aadress on vigane.",Toast.LENGTH_LONG).show();return;
        }
        if(!user.isEmpty()){
            EditText pass=Ui.input(context,"RTSP/ONVIF parool");
            pass.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
            new AlertDialog.Builder(context).setTitle("Kaamera parool").setView(pass)
                .setMessage("Parooli ei salvestata. Kui kaamera parooli ei vaja, jäta väli tühjaks.")
                .setNegativeButton("Tühista",null)
                .setPositiveButton("Ava",(d,w)->{
                    launch(context,cameraName,stream,user,pass.getText().toString(),port);
                    pass.setText("");
                }).show();
        }else{
            launch(context,cameraName,stream,"","",port);
        }
    }
    private static void launch(Activity activity,String name,String url,String user,String password,int port){
        Intent play=new Intent(activity,PlayerActivity.class);
        play.putExtra("camera",name);
        play.putExtra("url",RtspNetwork.withCredentials(url,user,password));
        play.putExtra("camera_ip",Uri.parse(url).getHost());
        play.putExtra("onvif_port",port);
        play.putExtra("user",user);
        play.putExtra("pass",password);
        activity.startActivity(play);
    }
}
