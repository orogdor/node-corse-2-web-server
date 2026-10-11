package ee.joolinkviewer;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-initiated, direct HTTPS account sign-in. Does not save passwords or session tokens. */
public class JoolinkCloudLoginActivity extends Activity {
    private TextView status;
    private EditText account,password;
    private Button login,logout,refresh;
    private LinearLayout deviceList;
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private JoolinkCloudClient.Session session;
    private volatile boolean destroyed;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=Ui.vertical(this);
        root.setPadding(Ui.dp(this,17),Ui.dp(this,20),Ui.dp(this,17),Ui.dp(this,32));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int top=android.os.Build.VERSION.SDK_INT>=30?
                insets.getInsets(android.view.WindowInsets.Type.statusBars()).top:insets.getSystemWindowInsetTop();
            int bottom=android.os.Build.VERSION.SDK_INT>=30?
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom:insets.getSystemWindowInsetBottom();
            v.setPadding(Ui.dp(this,17),Ui.dp(this,20)+top,Ui.dp(this,17),Ui.dp(this,32)+bottom);
            return insets;
        });
        scroll.addView(root);setContentView(scroll);root.requestApplyInsets();
        Ui.add(root,Ui.text(this,"Joolinki pilvekonto",23,true),this,3);
        TextView lead=Ui.text(this,
            "Otsene Joolinki kontoga sisselogimine tootja HTTPS-serverisse. Parooli ei salvestata; edukas seansitoken salvestatakse krüpteeritult Android Keystore'i abil, et saaksid kaamerate nimekirja avada ilma uuesti sisse logimata. Pilvevideo vajab veel eraldi tuge.",13,false);
        lead.setTextColor(Ui.MUTED);Ui.add(root,lead,this,10);

        account=Ui.input(this,"Joolinki konto: e-post või telefon");
        account.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        Ui.add(root,account,this,17);
        password=Ui.input(this,"Joolinki konto parool");
        password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Ui.add(root,password,this,8);

        login=Ui.button(this,"Proovi otse Joolinki pilve sisse logida");
        login.setOnClickListener(v->doLogin());Ui.add(root,login,this,13);
        logout=Ui.secondary(this,"Tühjenda seanss");
        logout.setEnabled(false);
        logout.setOnClickListener(v->{
            session=null;SecureCloudStore.clear(this);account.setText("");password.setText("");
            login.setEnabled(true);logout.setEnabled(false);refresh.setEnabled(false);
            deviceList.removeAllViews();
            status.setText("Seanss on kustutatud.");
        });
        Ui.add(root,logout,this,6);
        refresh=Ui.secondary(this,"↻ Laadi kaamerad uuesti");
        refresh.setEnabled(false);
        refresh.setOnClickListener(v->reloadDevices());
        Ui.add(root,refresh,this,6);
        status=Ui.text(this,"Sisselogimist pole veel proovitud.",13,false);
        status.setTextColor(Ui.MUTED);status.setTextIsSelectable(true);
        Ui.add(root,status,this,14);
        Ui.add(root,Ui.text(this,"Pilvekontoga seotud kaamerad",18,true),this,18);
        deviceList=Ui.vertical(this);Ui.add(root,deviceList,this,5);
        TextView caveat=Ui.text(this,
            "Kaameraloendi kuvamine ei tähenda veel pilve otsevideo või mälukaardi salvestuste tuge. Jooani P2P/LinkVisual voog vajab eraldi integratsiooni. Kui server ei aktsepteeri alternatiivset klienti, näitab see test veateadet ega kasuta ebaturvalisi ümbersuunamisi.",12,false);
        caveat.setTextColor(Ui.MUTED);Ui.add(root,caveat,this,16);
        Button back=Ui.secondary(this,"← Tagasi");
        back.setOnClickListener(v->finish());Ui.add(root,back,this,11);
        restoreSavedSession();
    }
    private void restoreSavedSession(){
        session=SecureCloudStore.session(this);
        if(session==null)return;
        account.setText(session.accountLabel);
        password.setText("");
        login.setEnabled(false);logout.setEnabled(true);refresh.setEnabled(true);
        JSONArray remembered=SecureCloudStore.cameras(this);
        status.setText("Pilvekonto on turvaliselt meeles. Salvestatud kaameraid: "+
            remembered.length()+". Soovi korral värskenda nimekirja.");
        showCameras(remembered);
    }
    private void doLogin(){
        if(session!=null){status.setText("Seanss juba avatud. Tühjenda see enne uuesti proovimist.");return;}
        String user=account.getText().toString().trim();
        String secret=password.getText().toString();
        if(user.isEmpty()||secret.isEmpty()){status.setText("Sisesta konto ja parool.");return;}
        login.setEnabled(false);
        status.setText("Ühendan tootja HTTPS-serveriga …");
        work.execute(()->{
            JoolinkCloudClient.Session created=null;
            JSONArray cameras=null;String error=null;
            try{
                created=JoolinkCloudClient.login(user,secret);
                SecureCloudStore.saveSession(getApplicationContext(),created);
                try{
                    cameras=JoolinkCloudClient.devices(created);
                    SecureCloudStore.saveCameras(getApplicationContext(),cameras);
                }catch(Exception cameraError){
                    error="Sisselogimine õnnestus ja jäi meelde, kuid kaameraloendit ei õnnestunud värskendada: "+
                        cameraError.getMessage();
                    cameras=SecureCloudStore.cameras(getApplicationContext());
                }
            }catch(Exception failure){
                error="Joolinki pilve autentimine ei õnnestunud: "+failure.getMessage();
            }
            final JoolinkCloudClient.Session finalSession=created;
            final JSONArray finalCameras=cameras;
            final String finalError=error;
            if(!destroyed)runOnUiThread(()->{
                password.setText("");
                if(finalSession==null){
                    session=null;login.setEnabled(true);logout.setEnabled(false);refresh.setEnabled(false);
                    status.setText(finalError==null?"Server ei vastanud.":finalError);
                    return;
                }
                session=finalSession;logout.setEnabled(true);login.setEnabled(false);refresh.setEnabled(true);
                status.setText(finalError==null?
                   "Joolinki server väljastas tokeni. Leidsin "+finalCameras.length()+
                   " kaamerat. Seanss on Androidi turvalises salvestuses.":
                   finalError);
                showCameras(finalCameras);
            });
        });
    }
    private void reloadDevices(){
        final JoolinkCloudClient.Session active=session;
        if(active==null){status.setText("Kõigepealt logi sisse.");return;}
        refresh.setEnabled(false);
        status.setText("Laadin Joolinki serverist kaamerate nimekirja …");
        work.execute(()->{
            JSONArray loaded=null;
            String error=null;
            try{
                loaded=JoolinkCloudClient.devices(active);
                SecureCloudStore.saveCameras(getApplicationContext(),loaded);
            }catch(Exception exception){error=exception.getMessage();}
            final JSONArray finalLoaded=loaded;
            final String finalError=error;
            if(!destroyed)runOnUiThread(()->{
                refresh.setEnabled(session!=null);
                if(finalError!=null){
                    status.setText("Kaamerate värskendamine: "+finalError);
                }else{
                    status.setText("Kaameraloend uuendatud: "+finalLoaded.length()+" seadet.");
                    showCameras(finalLoaded);
                }
            });
        });
    }
    private void showCameras(JSONArray cameras){
        deviceList.removeAllViews();
        if(cameras==null){Ui.add(deviceList,Ui.text(this,"Kaameraloendit ei õnnestunud laadida.",13,false),this,6);return;}
        if(cameras.length()==0){
            Ui.add(deviceList,Ui.text(this,"Konto vastuses ei olnud kaameraid.",13,false),this,6);
            return;
        }
        int count=Math.min(100,cameras.length());
        for(int i=0;i<count;i++){
            JSONObject item=cameras.optJSONObject(i);
            if(item==null)continue;
            JSONObject jooan=item.optJSONObject("jooan_info");
            JSONObject ali=item.optJSONObject("alilv_info");
            String nickname=jooan==null?"":jooan.optString("nick_name","");
            if(nickname.isEmpty()&&ali!=null)nickname=ali.optString("nickName","");
            if(nickname.isEmpty())nickname=item.optString("device_name",item.optString("nickName",""));
            if(nickname.isEmpty())nickname="Kaamera "+(i+1);
            String model=jooan==null?"":jooan.optString("device_model","");
            if(model.isEmpty()&&ali!=null)model=ali.optString("productModel","");
            LinearLayout card=Ui.card(this);
            Ui.add(card,Ui.text(this,nickname,16,true),this,1);
            String state="";
            if(jooan!=null)state="Võrgustaatus: "+jooan.optString("is_online","teadmata");
            if(ali!=null&&!ali.isNull("status"))state="Võrgustaatus: "+ali.optString("status","teadmata");
            Ui.add(card,Ui.text(this,(model.isEmpty()?"Kaamera":model)+(state.isEmpty()?"":" • "+state),12,false),this,3);
            TextView details=Ui.text(this,
                "Tootja pilvevideo avaneb ametlikus Joolinki rakenduses. "+
                "HomeJoolinki sees töötab eraldi RTSP/HLS mängija.",12,false);
            details.setTextColor(Ui.MUTED);Ui.add(card,details,this,6);
            final String cameraLabel=nickname;
            Button remote=Ui.button(this,"▶ Ava kaugvaade (Joolinki äpis)");
            remote.setOnClickListener(v->OfficialJoolinkBridge.open(this,cameraLabel));
            Ui.add(card,remote,this,6);
            Ui.add(deviceList,card,this,8);
        }
    }
    @Override protected void onDestroy(){
        destroyed=true;session=null;password.setText("");
        work.shutdownNow();super.onDestroy();
    }
}
