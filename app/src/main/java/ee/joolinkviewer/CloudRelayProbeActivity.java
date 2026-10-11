package ee.joolinkviewer;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Cloud camera -> server relay diagnostic. Never saves UID, password or probe responses. */
public class CloudRelayProbeActivity extends Activity {
    private EditText uid;
    private TextView result;
    private Button test;
    private volatile boolean destroyed;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=Ui.vertical(this);
        root.setPadding(Ui.dp(this,17),Ui.dp(this,22),Ui.dp(this,17),Ui.dp(this,36));
        scroll.addView(root);setContentView(scroll);

        Ui.add(root,Ui.text(this,"Joolinki serverivideo katse",22,true),this,5);
        TextView help=Ui.text(this,
            "See katse EI vaja kodust WiFi-t, VPN-i ega RTSP-aadressi. " +
            "Kontrollin Jooani KP2P seadmetunnuse alusel esmalt serveri ja " +
            "seejärel TURN-vahenduse olemasolu. Kui kaamera kasutab teist " +
            "videoprotokolli, teatab test vea. Video dekodeerimist selles etapis veel pole.",
            13,false);
        help.setTextColor(Ui.MUTED);Ui.add(root,help,this,10);

        String title=getIntent().getStringExtra("camera_name");
        if(title!=null&&!title.isEmpty())
            Ui.add(root,Ui.text(this,"Kaamera: "+title,17,true),this,8);
        uid=Ui.input(this,"Seadme UID / device_id");
        uid.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        Ui.add(root,uid,this,10);

        int index=getIntent().getIntExtra("camera_index",-1);
        JSONArray cameras=SecureCloudStore.cameras(this);
        if(index>=0&&index<cameras.length()){
            JSONObject cam=cameras.optJSONObject(index);
            if(cam!=null){
                JSONObject j=cam.optJSONObject("jooan_info");
                String found=candidate(j);
                if(found.isEmpty())found=candidate(cam);
                if(!found.isEmpty())uid.setText(found);
            }
        }
        TextView note=Ui.text(this,
            "Seadme UID võib erineda Joolinki pilvekonto device_id-st. " +
            "Katse saadab ainult UID tootja tuvastusserverile; kontoparooli ega tokenit ei kasutata. " +
            "KP2P tuvastusserver kasutab HTTP-protokolli ja ühendus pole krüpteeritud.",
            12,false);
        note.setTextColor(Ui.MUTED);Ui.add(root,note,this,6);

        test=Ui.button(this,"Kontrolli serverivahendusega videoühendust");
        Ui.add(root,test,this,14);
        result=Ui.text(this,"Testi pole käivitatud.",14,false);
        result.setTextIsSelectable(true);Ui.add(root,result,this,10);
        test.setOnClickListener(v->probe());

        Button back=Ui.secondary(this,"← Tagasi kaamerate juurde");
        back.setOnClickListener(v->finish());Ui.add(root,back,this,18);
    }
    private static String candidate(JSONObject data) {
        if(data==null)return "";
        // Prefer transport-specific fields; do not mistake account user_id for a camera UID.
        for(String field:new String[]{"p2p_uid","p2p_id","device_uid","uid",
               "device_id","dev_id","device_sn"}){
            String value=data.optString(field,"").trim();
            if(value.matches("[A-Za-z0-9_-]{5,64}"))return value;
        }
        return "";
    }
    private void probe() {
        final String identifier=uid.getText().toString().trim();
        if(!identifier.matches("[A-Za-z0-9_-]{5,64}")){
            result.setText("Seadme UID ei sobi KP2P testiks. Kontrolli pilvekaamera tunnust.");
            return;
        }
        test.setEnabled(false);
        result.setText("Alustan serveriühenduse katset …");
        new Thread(()->{
            try {
                String summary=JooanRelayProbe.test(identifier,message->{
                    if(!destroyed)runOnUiThread(()->result.setText(message));
                });
                if(!destroyed)runOnUiThread(()->result.setText("ÜHENDUS: "+summary));
            }catch(Exception exception){
                String failure=exception.getClass().getSimpleName()+": "+
                    (exception.getMessage()==null?"ühendus ebaõnnestus":exception.getMessage());
                if(!destroyed)runOnUiThread(()->result.setText(
                    "Serverivahenduse katse ebaõnnestus: "+failure+
                    "\nSee ei tõesta, et kaamerat ei saa Joolinki serveri kaudu vaadata. " +
                    "Kaamera võib kasutada teistsugust TUTK videoprotokolli."));
            }finally {
                if(!destroyed)runOnUiThread(()->test.setEnabled(true));
            }
        },"joolink-kp2p-turn-probe").start();
    }
    @Override protected void onDestroy(){destroyed=true;super.onDestroy();}
}
