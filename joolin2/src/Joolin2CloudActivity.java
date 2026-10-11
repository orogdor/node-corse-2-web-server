package com.jooan.qiaoanzhilian.ui.activity.play;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Separate Joolink HTTPS account path used when vendor Alibaba SDK is unavailable.
 * No vendor SDK, no credentials/logs uploaded anywhere except authenticated requests
 * to Joolink's official HTTPS endpoint. Password is not persisted.
 * This currently lists cameras; remote live video is NOT implemented.
 */
public final class Joolin2CloudActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean destroyed=false;
    private EditText account, password;
    private TextView status;
    private LinearLayout cameras;
    private Button login, refresh;
    private JoolinkCloudClient.Session session;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff101923);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(38),dp(18),dp(30));
        scroll.addView(root);
        setContentView(scroll);

        addText(root,"Joolin2 v6 · Joolinki pilvekonto",23,Color.WHITE,14);
        addText(root,"See on eraldi HTTPS-sisselogimine Joolinki serverisse. See ei käivita Alibaba SDK-d. Kontoparooli ei salvestata. Kaameraloend võib töötada, kuid pilve otsevideo ei ole veel toetatud.",14,0xffc5d3e1,18);
        account=new EditText(this);
        account.setSingleLine(true);
        account.setTextColor(Color.WHITE);
        account.setHintTextColor(0xff9babb8);
        account.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        account.setHint("Joolinki e-post või telefon");
        root.addView(account,new LinearLayout.LayoutParams(-1,dp(55)));
        password=new EditText(this);
        password.setSingleLine(true);
        password.setTextColor(Color.WHITE);
        password.setHintTextColor(0xff9babb8);
        password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setHint("Joolinki parool");
        root.addView(password,new LinearLayout.LayoutParams(-1,dp(55)));
        login=new Button(this);
        login.setText("Logi Joolinki pilve");
        login.setAllCaps(false);
        root.addView(login,new LinearLayout.LayoutParams(-1,dp(54)));
        login.setOnClickListener(v->loginNow());
        refresh=new Button(this);
        refresh.setText("Laadi kaamerad uuesti");
        refresh.setAllCaps(false);
        refresh.setEnabled(false);
        root.addView(refresh,new LinearLayout.LayoutParams(-1,dp(52)));
        refresh.setOnClickListener(v->refreshCameras());
        Button close=new Button(this);
        close.setText("Tagasi diagnostikasse");
        close.setAllCaps(false);
        close.setOnClickListener(v->finish());
        root.addView(close,new LinearLayout.LayoutParams(-1,dp(50)));
        status=addText(root,"Valmis ühenduma Joolinki HTTPS-serveriga.",14,0xffc5d3e1,14);
        addText(root,"Konto kaamerad",19,Color.WHITE,14);
        cameras=new LinearLayout(this);
        cameras.setOrientation(LinearLayout.VERTICAL);
        root.addView(cameras);
    }
    private int dp(int value){
        return Math.round(value*getResources().getDisplayMetrics().density);
    }
    private TextView addText(LinearLayout root,String value,int size,int color,int bottom){
        TextView view=new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setTextIsSelectable(true);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.bottomMargin=dp(bottom);
        root.addView(view,params);
        return view;
    }
    private void loginNow(){
        String user=account.getText().toString().trim();
        String secret=password.getText().toString();
        if(user.isEmpty()||secret.isEmpty()){
            status.setText("Sisesta konto ja parool.");
            return;
        }
        login.setEnabled(false);
        refresh.setEnabled(false);
        status.setText("Ühendan Joolinki HTTPS-serveriga. See võib võtta kuni 30 sekundit...");
        worker.execute(()->{
            JoolinkCloudClient.Session newSession=null;
            JSONArray deviceList=null;
            String error=null;
            try{
                newSession=JoolinkCloudClient.login(user,secret);
                try{
                    deviceList=JoolinkCloudClient.devices(newSession);
                }catch(Exception deviceError){
                    error="Sisselogimine õnnestus, kuid kaameraloend ei laadinud: "+
                        safeError(deviceError);
                }
            }catch(Exception e){
                error="Sisselogimine ei õnnestunud: "+safeError(e);
            }
            final JoolinkCloudClient.Session result=newSession;
            final JSONArray resultDevices=deviceList;
            final String errorText=error;
            if(!destroyed)runOnUiThread(()->{
                password.setText("");
                session=result;
                login.setEnabled(result==null);
                refresh.setEnabled(result!=null);
                if(result==null){
                    status.setText(errorText==null?"Server ei vastanud.":errorText);
                    return;
                }
                status.setText(errorText==null?
                    "Sisselogimine õnnestus. Leidsin "+resultDevices.length()+" kaamerat.":
                    errorText);
                if(resultDevices!=null)renderDevices(resultDevices);
            });
        });
    }
    private void refreshCameras(){
        JoolinkCloudClient.Session saved=session;
        if(saved==null)return;
        refresh.setEnabled(false);
        status.setText("Laadin kaameraloendit...");
        worker.execute(()->{
            JSONArray list=null;
            String error=null;
            try{list=JoolinkCloudClient.devices(saved);}
            catch(Exception ex){error=safeError(ex);}
            final JSONArray result=list;
            final String problem=error;
            if(!destroyed)runOnUiThread(()->{
                refresh.setEnabled(true);
                if(problem!=null){status.setText("Kaameraloendi viga: "+problem);return;}
                status.setText("Kaameraid: "+result.length());
                renderDevices(result);
            });
        });
    }
    private String safeError(Exception error){
        String name=error.getClass().getSimpleName();
        String text=error.getMessage();
        if(text==null)return name;
        text=text.replaceAll("(?i)(token|password|authorization)[=:]\\S+","[peidetud]");
        text=text.replaceAll("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}","[e-post peidetud]");
        return name+": "+text.substring(0,Math.min(180,text.length()));
    }
    private void renderDevices(JSONArray list){
        cameras.removeAllViews();
        if(list.length()==0){
            addText(cameras,"Kontoga seotud kaameraid ei leitud.",14,0xffbdc9d5,14);
            return;
        }
        for(int i=0;i<Math.min(list.length(),60);i++){
            JSONObject item=list.optJSONObject(i);
            if(item==null)continue;
            JSONObject jooan=item.optJSONObject("jooan_info");
            JSONObject ali=item.optJSONObject("alilv_info");
            String title=jooan==null?"":jooan.optString("nick_name","");
            if(title.isEmpty()&&ali!=null)title=ali.optString("nickName","");
            if(title.isEmpty())title=item.optString("device_name","");
            if(title.isEmpty())title="Kaamera "+(i+1);
            String model=jooan==null?"":jooan.optString("device_model","");
            if(model.isEmpty()&&ali!=null)model=ali.optString("productModel","");
            String state=jooan==null?"":jooan.optString("is_online","");
            if(state.isEmpty()&&ali!=null)state=ali.optString("status","");
            addText(cameras,title+" · "+model+" · "+state,16,0xffdde7f0,8);
        }
        addText(cameras,"Videopilti ei saa selles testis veel avada. Dekoodrivalik on originaalse Joolinki videovaates.",13,0xffb1bdca,12);
    }
    @Override protected void onDestroy(){
        destroyed=true;session=null;
        if(password!=null)password.setText("");
        worker.shutdownNow();
        super.onDestroy();
    }
}
