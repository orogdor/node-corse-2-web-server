package ee.joolinkviewer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
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

/** Read-only camera information over the authenticated Joolink cloud API.
 * Works on mobile data; no LAN RTSP/ONVIF access is required for this screen.
 */
public class CloudCameraInfoActivity extends Activity {
    private String deviceKey,cameraName;
    private TextView status,header;
    private LinearLayout details;
    private Button reload;
    private boolean closed,busy;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        deviceKey=getIntent().getStringExtra("device_key");
        cameraName=getIntent().getStringExtra("camera_name");
        if(deviceKey==null||deviceKey.isEmpty()){finish();return;}
        if(cameraName==null||cameraName.isEmpty())cameraName="Kaamera";
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=Ui.vertical(this);
        root.setPadding(Ui.dp(this,16),Ui.dp(this,22),Ui.dp(this,16),Ui.dp(this,35));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int top=android.os.Build.VERSION.SDK_INT>=30?
                insets.getInsets(android.view.WindowInsets.Type.statusBars()).top:insets.getSystemWindowInsetTop();
            int bottom=android.os.Build.VERSION.SDK_INT>=30?
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom:insets.getSystemWindowInsetBottom();
            v.setPadding(Ui.dp(this,16),Ui.dp(this,22)+top,Ui.dp(this,16),Ui.dp(this,35)+bottom);
            return insets;
        });
        scroll.addView(root);
        setContentView(scroll);root.requestApplyInsets();
        header=Ui.text(this,cameraName,23,true);
        Ui.add(root,header,this,4);
        TextView sub=Ui.text(this,"Pilvekaamera info · töötab ka 4G/5G kaudu",13,false);
        sub.setTextColor(Ui.MUTED);Ui.add(root,sub,this,4);
        status=Ui.text(this,"Avan viimati salvestatud pilveinfot …",13,false);
        status.setTextColor(Ui.MUTED);
        Ui.add(root,status,this,10);
        reload=Ui.button(this,"↻ Värskenda kaamera infot pilvest");
        reload.setOnClickListener(v->loadFromCloud());
        Ui.add(root,reload,this,7);
        details=Ui.vertical(this);
        Ui.add(root,details,this,9);
        TextView limitation=Ui.text(this,
            "See vaade näitab ainult neid andmeid, mida Joolinki pilveserver tagastab. Puuduvad väljad ei tähenda, et kaameral vastav funktsioon puudub. Päris otsevideo ja P2P juhtimine nõuavad eraldi videoprotokolli.",12,false);
        limitation.setTextColor(Ui.MUTED);Ui.add(root,limitation,this,14);
        Button stream=Ui.secondary(this,"▶ Ava videopilt (kodune WiFi/VPN)");
        stream.setOnClickListener(v->{
            JSONObject linked=SecureCloudStore.binding(this,deviceKey);
            if(linked!=null)CloudCameraActivity.openPlayer(this,cameraName,linked);
            else{
                Intent i=new Intent(this,CloudCameraActivity.class);
                i.putExtra("device_key",deviceKey);i.putExtra("camera_name",cameraName);
                startActivity(i);
            }
        });
        Ui.add(root,stream,this,12);
        Button back=Ui.secondary(this,"← Kaamerad");
        back.setOnClickListener(v->finish());
        Ui.add(root,back,this,7);
        renderCached();
        if(SecureCloudStore.session(this)!=null)loadFromCloud();
    }
    private JSONObject findCamera(JSONArray array){
        for(int i=0;i<array.length();i++){
            JSONObject item=array.optJSONObject(i);
            if(item!=null&&deviceKey.equals(SecureCloudStore.cameraKey(item,i)))return item;
        }
        return null;
    }
    private void renderCached(){
        if(closed||details==null)return;
        details.removeAllViews();
        long cached=SecureCloudStore.updatedAt(this);
        JSONObject camera=findCamera(SecureCloudStore.cameras(this));
        if(camera==null){
            addText(details,"Selle kaamera pilveinfot pole veel salvestatud.");
            return;
        }
        String updated=cached>0?
            DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(cached)):
            "teadmata";
        addDetail("Andmete viimase uuenduse aeg",updated);
        addDetail("Ühenduse olek",SecureCloudStore.cameraStatus(camera));
        addDetail("Kaamera mudel",SecureCloudStore.cameraModel(camera));
        JSONObject jooan=camera.optJSONObject("jooan_info");
        JSONObject ali=camera.optJSONObject("alilv_info");
        JSONObject props=jooan==null?null:jooan.optJSONObject("properties");
        JSONObject aliProps=ali==null?null:ali.optJSONObject("aliProperties");
        addFrom(props,"Tarkvaraversioon","device_version");
        addFrom(props,"MCU versioon","mcu_version");
        addFrom(props,"Seadme olek","device_status");
        addFrom(props,"Võrgu tüüp","network_type");
        addFrom(props,"Ühendatud WiFi","current_ssid");
        addFrom(props,"Video kvaliteedirežiim","qualitymode");
        addFrom(props,"Videopildi eraldusvõime","resolution");
        addFrom(props,"Mälukaardi olek","storage_status");
        addFrom(props,"Mälukaardi kogumaht","sdcard_total");
        addFrom(props,"Mälukaardi vaba maht","sdcard_free");
        addFrom(props,"Pilvesalvestuse olek","cs_status");
        addFrom(props,"Öörežiimi IR filter","led");
        addFrom(props,"Liikumistuvastus","md_enable");
        addFrom(props,"Automaatne jälgimine","autotrack");
        addFrom(props,"Laadimise olek","chargingStatus");
        addFrom(ali,"Võrgu tüüp","netType");
        addFrom(ali,"Seadme versioon","version");
        addFrom(aliProps,"Salvestamise olek","record_status");
        JSONObject cloudService=camera.optJSONObject("cs_info");
        addFrom(cloudService,"Pilveteenuse olek","cs_status");
        addFrom(cloudService,"Pilvesalvestuse kehtivusaeg","expire_time");
        String offline=camera.optString("mqtt_offline_time","");
        if(!offline.isEmpty()&&!"null".equalsIgnoreCase(offline))
            addDetail("Viimane MQTT katkestus",offline);
    }
    private void addFrom(JSONObject o,String label,String key){
        if(o==null||o.isNull(key))return;
        Object obj=o.opt(key);
        if(obj instanceof JSONObject||obj instanceof JSONArray)return;
        String value=o.optString(key,"");
        if(value.isEmpty()||"null".equalsIgnoreCase(value))return;
        if(value.length()>75)value=value.substring(0,75)+"…";
        addDetail(label,value);
    }
    private void addDetail(String name,String value){
        if(value==null||value.trim().isEmpty())return;
        LinearLayout row=Ui.card(this);
        TextView label=Ui.text(this,name,12,false);
        label.setTextColor(Ui.MUTED);Ui.add(row,label,this,0);
        Ui.add(row,Ui.text(this,value,16,true),this,4);
        Ui.add(details,row,this,6);
    }
    private void addText(LinearLayout to,String content){
        Ui.add(to,Ui.text(this,content,13,false),this,6);
    }
    private void loadFromCloud(){
        if(busy)return;
        JoolinkCloudClient.Session session=SecureCloudStore.session(this);
        if(session==null){
            status.setText("Pilvekonto pole ühendatud. Ava ☰ Menüü ja logi sisse.");return;
        }
        busy=true;reload.setEnabled(false);
        status.setText("Laadin pilveinfot tootja serverist …");
        worker.execute(()->{
            String error=null;
            try{
                JSONArray fresh=JoolinkCloudClient.devices(session);
                SecureCloudStore.saveCameras(getApplicationContext(),fresh);
            }catch(Exception ex){error=ex.getMessage();}
            final String failure=error;
            if(!closed)runOnUiThread(()->{
                busy=false;reload.setEnabled(true);
                renderCached();
                status.setText(failure==null?
                  "Kaamera pilveinfo on värskendatud.":
                  "Värskendamine ebaõnnestus: "+failure+". Näitan viimast salvestatud infot.");
            });
        });
    }
    @Override protected void onDestroy(){
        closed=true;worker.shutdownNow();super.onDestroy();
    }
}
