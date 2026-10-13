package ee.joolinkviewer;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** ONVIF PTZ controller for Android's lightweight Media3 player. */
final class PtzPanel {
    private final Activity activity;
    private final String ip,user,password;
    private final int port;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private volatile OnvifClient client;
    private volatile boolean closed;
    private TextView state;
    PtzPanel(Activity context,String host,int onvifPort,String name,String pass){
        activity=context;ip=host;port=onvifPort;
        user=name==null?"":name;password=pass==null?"":pass;
    }
    void attach(LinearLayout parent){
        LinearLayout card=Ui.card(activity);
        Ui.add(card,Ui.text(activity,"PTZ • Kaamera pööramine",18,true),activity,0);
        state=Ui.text(activity,"Kontrollin "+ip+":"+port+" ...",12,false);
        state.setTextColor(Ui.MUTED);Ui.add(card,state,activity,6);
        LinearLayout top=row();
        arrow(top,"↖",-0.4,0.4,0);
        arrow(top,"↑",0,0.5,0);
        arrow(top,"↗",0.4,0.4,0);
        Ui.add(card,top,activity,8);
        LinearLayout middle=row();
        arrow(middle,"←",-0.5,0,0);
        Button home=Ui.secondary(activity,"⌂");
        home.setOnClickListener(v->command("Algasend",()->client.home()));
        add(middle,home);
        arrow(middle,"→",0.5,0,0);
        Ui.add(card,middle,activity,4);
        LinearLayout bottom=row();
        arrow(bottom,"↙",-0.4,-0.4,0);
        arrow(bottom,"↓",0,-0.5,0);
        arrow(bottom,"↘",0.4,-0.4,0);
        Ui.add(card,bottom,activity,4);
        LinearLayout zoom=row();
        arrow(zoom,"− Suum",0,0,-0.5);
        arrow(zoom,"+ Suum",0,0,0.5);
        Ui.add(card,zoom,activity,4);
        Button test=Ui.secondary(activity,"↻ Kontrolli ONVIF ühendust");
        test.setOnClickListener(v->connect());Ui.add(card,test,activity,8);
        TextView note=Ui.text(activity,"Hoia noolenuppu all. Kui ONVIF IP erineb RTSP IP-st, veendu, et see on õige kaamera.",12,false);
        note.setTextColor(Ui.MUTED);Ui.add(card,note,activity,5);
        Ui.add(parent,card,activity,8);
        connect();
    }
    private LinearLayout row(){
        LinearLayout layout=new LinearLayout(activity);
        layout.setGravity(Gravity.CENTER);
        return layout;
    }
    private void add(LinearLayout row,Button b){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(activity,46),1);
        p.leftMargin=Ui.dp(activity,2);p.rightMargin=Ui.dp(activity,2);
        row.addView(b,p);
    }
    private void arrow(LinearLayout row,String title,double x,double y,double z){
        Button btn=Ui.secondary(activity,title);
        final Runnable[] again={null};
        btn.setOnTouchListener((v,event)->{
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                if(client==null||!client.supportsPtz()){
                    show("ONVIF PTZ ei ole ühendatud või kaamera ei toeta seda.");
                    return true;
                }
                Runnable command=new Runnable(){
                    @Override public void run(){
                        if(closed||!v.isPressed())return;
                        PtzPanel.this.command("Liikumine",()->client.move(x,y,z));
                        ui.postDelayed(this,850);
                    }
                };
                again[0]=command;
                v.setPressed(true);
                command.run();
                return true;
            }
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                v.setPressed(false);
                if(again[0]!=null)ui.removeCallbacks(again[0]);
                again[0]=null;
                command("Seiska",()->client.stop());
                return true;
            }
            return true;
        });
        add(row,btn);
    }
    private interface Operation {void run()throws Exception;}
    private void command(String caption,Operation op){
        OnvifClient c=client;
        if(c==null||!c.supportsPtz()){show("PTZ puudub või pole ühendatud.");return;}
        if(closed)return;
        worker.execute(()->{
            try{op.run();}
            catch(Exception e){show(caption+" ebaõnnestus: "+e.getMessage());}
        });
    }
    private void show(String message){
        if(!closed)activity.runOnUiThread(()->{
            if(!closed&&state!=null)state.setText(message);
        });
    }
    private void connect(){
        if(ip==null||!RtspNetwork.isPrivate(ip)){
            show("Vigane ONVIF IP. Sisesta kohalik IPv4 aadress.");return;
        }
        show("Ühendan ONVIF "+ip+":"+port+" ...");
        worker.execute(()->{
            try{
                OnvifClient found=new OnvifClient(ip,port,user,password);
                found.connect();
                client=found;
                show("ONVIF "+ip+":"+port+" • PTZ "+(found.supportsPtz()?"olemas, hoia noolt":"ei ole tuvastatud")+" • vooge "+found.streams.size());
            }catch(Exception e){
                client=null;show("ONVIF: "+e.getMessage());
            }
        });
    }
    void close(){
        closed=true;ui.removeCallbacksAndMessages(null);
        if(client!=null){try{client.stop();}catch(Exception ignored){}}
        worker.shutdownNow();
    }
}
