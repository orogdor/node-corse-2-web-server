package ee.joolinkviewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Main dashboard: cloud cameras first, local WiFi and advanced controls in a menu. */
public class HomeActivity extends Activity {
    private LinearLayout page,cameraList;
    private TextView cloudStatus,heading;
    private Button refresh;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean destroyed,busy;
    private long lastNetworkRefresh=0;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        page=Ui.vertical(this);
        page.setPadding(Ui.dp(this,16),Ui.dp(this,18),Ui.dp(this,16),Ui.dp(this,34));
        page.setOnApplyWindowInsetsListener((v,insets)->{
            int top=android.os.Build.VERSION.SDK_INT>=30?
                insets.getInsets(android.view.WindowInsets.Type.statusBars()).top:insets.getSystemWindowInsetTop();
            int bottom=android.os.Build.VERSION.SDK_INT>=30?
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom:insets.getSystemWindowInsetBottom();
            v.setPadding(Ui.dp(this,16),Ui.dp(this,18)+top,Ui.dp(this,16),Ui.dp(this,34)+bottom);
            return insets;
        });
        scroll.addView(page);setContentView(scroll);page.requestApplyInsets();
        LinearLayout bar=new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        heading=Ui.text(this,"Joolink Viewer",23,true);
        bar.addView(heading,new LinearLayout.LayoutParams(0,Ui.dp(this,56),1));
        Button menu=Ui.secondary(this,"☰ Menüü");
        menu.setOnClickListener(v->openMenu());
        bar.addView(menu,new LinearLayout.LayoutParams(Ui.dp(this,125),Ui.dp(this,49)));
        Ui.add(page,bar,this,0);
        cloudStatus=Ui.text(this,"Pilvekonto …",13,false);
        cloudStatus.setTextColor(Ui.MUTED);Ui.add(page,cloudStatus,this,4);
        refresh=Ui.button(this,"↻ Värskenda pilvekaameraid");
        refresh.setOnClickListener(v->refreshCameras(true));
        Ui.add(page,refresh,this,12);
        Ui.add(page,Ui.text(this,"Minu kaamerad",19,true),this,16);
        cameraList=Ui.vertical(this);
        Ui.add(page,cameraList,this,7);
        TextView info=Ui.text(this,
            "Pilvekonto ja nimekiri jäävad telefoni krüpteeritult meelde. Kaamerapildi avamiseks tuleb esimesel korral valida selle kaamera RTSP-videovoog; üle mobiilse interneti on praegu vaja koduvõrgu VPN-i. Tootja P2P-videoprotokoll on järgmine arendusetapp.",12,false);
        info.setTextColor(Ui.MUTED);Ui.add(page,info,this,15);
        drawCached();
    }
    @Override protected void onResume(){
        super.onResume();
        drawCached();
        if(SecureCloudStore.session(this)!=null&&
           android.os.SystemClock.elapsedRealtime()-lastNetworkRefresh>20000){
            refreshCameras(false);
        }
    }
    private void openMenu(){
        final String[] options={
            "Pilvekonto ja sisselogimine",
            "Kohalik WiFi / RTSP / ONVIF",
            "Internet / VPN kaugvaade",
            "Joolinki pilve diagnostika",
            "Värskenda kaamerad",
            "Logi pilvekontost välja"
        };
        new AlertDialog.Builder(this)
            .setTitle("Joolink Vieweri menüü")
            .setItems(options,(dialog,which)->{
                switch(which){
                    case 0: startActivity(new Intent(this,JoolinkCloudLoginActivity.class));break;
                    case 1:
                        Intent local=new Intent(this,MainActivity.class);
                        local.putExtra("connection_mode","local");startActivity(local);break;
                    case 2:startActivity(new Intent(this,InternetActivity.class));break;
                    case 3:startActivity(new Intent(this,JoolinkApiInspector.class));break;
                    case 4:refreshCameras(true);break;
                    case 5:new AlertDialog.Builder(this).setTitle("Logi välja?")
                        .setMessage("Joolinki pilveseanss, salvestatud kaameraloend ja ühenduste seosed kustutatakse. Kohaliku RTSP loend jääb alles.")
                        .setNegativeButton("Tühista",null)
                        .setPositiveButton("Logi välja",(d,w)->{
                            SecureCloudStore.clear(this);drawCached();
                        }).show();break;
                }
            }).show();
    }
    private void drawCached(){
        if(cameraList==null||destroyed)return;
        cameraList.removeAllViews();
        JoolinkCloudClient.Session session=SecureCloudStore.session(this);
        if(session==null){
            cloudStatus.setText("Pilvekonto pole sisse logitud.");
            refresh.setEnabled(false);
            LinearLayout loginCard=Ui.card(this);
            Ui.add(loginCard,Ui.text(this,"Ühenda Joolinki pilvekonto",18,true),this,0);
            TextView description=Ui.text(this,
               "Logi üks kord sisse. Pärast seda avaneb rakendus kohe sinu kaamerate nimekirjaga.",13,false);
            description.setTextColor(Ui.MUTED);Ui.add(loginCard,description,this,4);
            Button login=Ui.button(this,"Logi sisse");
            login.setOnClickListener(v->startActivity(new Intent(this,JoolinkCloudLoginActivity.class)));
            Ui.add(loginCard,login,this,9);Ui.add(cameraList,loginCard,this,5);
            Button local=Ui.secondary(this,"Ava kohalik WiFi kaameravaade");
            local.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));
            Ui.add(cameraList,local,this,10);
            return;
        }
        refresh.setEnabled(!busy);
        JSONArray cached=SecureCloudStore.cameras(this);
        long updated=SecureCloudStore.updatedAt(this);
        cloudStatus.setText("Ühendatud: "+session.accountLabel+(updated>0?
            " · Uuendatud "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(updated)):
            " · Ootan kaamerate päringut"));
        if(cached.length()==0){
            Ui.add(cameraList,Ui.text(this,"Pilvekaamerate nimekiri puudub. Vajuta Värskenda.",14,false),this,6);
            return;
        }
        for(int index=0;index<Math.min(cached.length(),100);index++){
            JSONObject item=cached.optJSONObject(index);
            if(item==null)continue;
            String title=SecureCloudStore.cameraName(item,index);
            String model=SecureCloudStore.cameraModel(item);
            String availability=SecureCloudStore.cameraStatus(item);
            String key=SecureCloudStore.cameraKey(item,index);
            boolean mapped=SecureCloudStore.binding(this,key)!=null;
            LinearLayout card=Ui.card(this);
            Ui.add(card,Ui.text(this,title,18,true),this,0);
            TextView desc=Ui.text(this,model+"  •  "+availability+
                (mapped?"  •  Videovoog seotud":"  •  Videovoog pole veel seotud"),12,false);
            desc.setTextColor(Ui.MUTED);Ui.add(card,desc,this,3);
            Button open=Ui.button(this,mapped?"▶ Vaata ja juhi":"Seadista video ja juhtimine");
            open.setOnClickListener(v->openCamera(key,title));
            Ui.add(card,open,this,11);
            Button settings=Ui.secondary(this,"Kaamera ühenduse seaded");
            settings.setOnClickListener(v->openBinding(key,title));
            Ui.add(card,settings,this,6);
            Ui.add(cameraList,card,this,9);
        }
    }
    private void openCamera(String key,String title){
        JSONObject binding=SecureCloudStore.binding(this,key);
        if(binding==null){openBinding(key,title);return;}
        CloudCameraActivity.openPlayer(this,title,binding);
    }
    private void openBinding(String key,String title){
        Intent intent=new Intent(this,CloudCameraActivity.class);
        intent.putExtra("device_key",key);intent.putExtra("camera_name",title);
        startActivity(intent);
    }
    private void refreshCameras(boolean manual){
        if(busy)return;
        final JoolinkCloudClient.Session session=SecureCloudStore.session(this);
        if(session==null){drawCached();return;}
        busy=true;lastNetworkRefresh=android.os.SystemClock.elapsedRealtime();
        refresh.setEnabled(false);
        cloudStatus.setText("Laadin kaameraid tootja serverist …");
        worker.execute(()->{
            JSONArray devices=null;String error=null;
            try{
                devices=JoolinkCloudClient.devices(session);
                SecureCloudStore.saveCameras(getApplicationContext(),devices);
            }catch(Exception e){error=e.getMessage();}
            final String failure=error;
            if(!destroyed)runOnUiThread(()->{
                busy=false;drawCached();
                if(failure!=null){
                    cloudStatus.setText("Pilve värskendamine ebaõnnestus: "+failure+
                        ". Näitan viimati salvestatud kaameraid. Vajadusel logi uuesti sisse.");
                }
            });
        });
    }
    @Override protected void onDestroy(){
        destroyed=true;worker.shutdownNow();super.onDestroy();
    }
}
