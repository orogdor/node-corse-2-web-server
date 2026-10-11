package ee.joolinkviewer;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayerActivity extends Activity {
    private final String[] renderModes={"Tarkvara + TextureView","Tarkvara + SurfaceView","Riistvara + TextureView","Riistvara + SurfaceView"};
    private final int[] buffers={350,800,1800};
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService background=Executors.newSingleThreadExecutor();
    private LibVLC engine;
    private MediaPlayer player;
    private VLCVideoLayout video;
    private Spinner renderSelector,bufferSelector,streamSelector;
    private TextView status,onvifStatus;
    private String streamUrl,cameraIp,username,password;
    private int onvifPort;
    private OnvifClient onvif;
    private boolean closed,recording,localHistory;
    private long lastMove;
    private final List<String> urls=new ArrayList<>();
    private final List<String> labels=new ArrayList<>();
    private boolean settingStream;
    private SeekBar timeline;
    private final Runnable timeUpdater=new Runnable(){
        @Override public void run(){
            if(!closed&&localHistory&&player!=null&&timeline!=null){
                try{
                    long duration=player.getLength(),pos=player.getTime();
                    if(duration>0){timeline.setMax(1000);timeline.setProgress((int)(1000*Math.max(0,pos)/duration));}
                }catch(Exception ignored){}
            }
            if(!closed)handler.postDelayed(this,950);
        }
    };

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        streamUrl=getIntent().getStringExtra("url");
        if(streamUrl==null||(!RtspNetwork.validUrl(streamUrl)&&!streamUrl.startsWith("file://"))){finish();return;}
        cameraIp=getIntent().getStringExtra("camera_ip");
        if(cameraIp==null||!RtspNetwork.isPrivate(cameraIp)){
            try{cameraIp=Uri.parse(streamUrl).getHost();}catch(Exception ignored){}
        }
        username=getIntent().getStringExtra("user");
        password=getIntent().getStringExtra("pass");
        onvifPort=getIntent().getIntExtra("onvif_port",80);
        localHistory=streamUrl.startsWith("file://")||getIntent().getBooleanExtra("recording",false);
        getWindow().setStatusBarColor(Ui.BG);getWindow().setNavigationBarColor(Ui.BG);
        LinearLayout root=Ui.vertical(this);
        root.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,10));
        root.setBackgroundColor(Ui.BG);setContentView(root);
        String camera=getIntent().getStringExtra("camera");
        Ui.add(root,Ui.text(this,camera==null?"Kaamerapilt":camera,20,true),this,0);
        FrameLayout frame=new FrameLayout(this);
        video=new VLCVideoLayout(this);
        frame.addView(video,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,0,1);
        fp.topMargin=Ui.dp(this,7);fp.bottomMargin=Ui.dp(this,5);
        root.addView(frame,fp);
        status=Ui.text(this,"Ühendan videovooga …",13,false);status.setTextColor(Ui.MUTED);
        Ui.add(root,status,this,2);
        ScrollView scroll=new ScrollView(this);
        LinearLayout controls=Ui.vertical(this);
        controls.setPadding(0,Ui.dp(this,3),0,Ui.dp(this,22));
        scroll.addView(controls);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,Ui.dp(this,405)));

        if(!localHistory){
            Ui.add(controls,Ui.text(this,"Videovoog / formaat",17,true),this,2);
            streamSelector=spinner(controls,new String[]{"Hetkel valitud voog (RTSP/HTTP/HLS)"});
            streamSelector.setEnabled(false);
            streamSelector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
                private boolean first=true;
                @Override public void onItemSelected(android.widget.AdapterView<?> a,View v,int pos,long id){
                    if(first){first=false;return;}
                    if(settingStream)return;
                    if(pos>0&&pos<urls.size()){
                        String next=urls.get(pos);
                        if(!next.equals(streamUrl)){
                            streamUrl=RtspNetwork.withCredentials(next,username,password);
                            startStream();
                        }
                    }
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
            });
        }
        Ui.add(controls,Ui.text(this,"Videodekooder ja puhver",16,true),this,9);
        renderSelector=spinner(controls,renderModes);
        renderSelector.setSelection(getPreferences(MODE_PRIVATE).getInt("render-mode",0));
        bufferSelector=spinner(controls,new String[]{"Madal viivitus 350 ms","Tavaline 800 ms","Stabiilne 1800 ms"});
        bufferSelector.setSelection(1);
        renderSelector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            private boolean first=true;
            @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                getPreferences(MODE_PRIVATE).edit().putInt("render-mode",pos).apply();
                if(first){first=false;return;}startStream();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        bufferSelector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            private boolean first=true;
            @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                if(first){first=false;return;}startStream();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        LinearLayout tools=new LinearLayout(this);tools.setGravity(Gravity.CENTER_VERTICAL);
        Button restart=Ui.button(this,"↻ Värskenda");restart.setOnClickListener(v->startStream());
        tools.addView(restart,new LinearLayout.LayoutParams(0,Ui.dp(this,47),1));
        Button rotate=Ui.secondary(this,"⛶ Rõhtvaade");
        rotate.setOnClickListener(v->{
            boolean rotated=getRequestedOrientation()==ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
            setRequestedOrientation(rotated?ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED:ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        });
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,47),1);
        p.leftMargin=Ui.dp(this,7);tools.addView(rotate,p);
        Ui.add(controls,tools,this,8);

        if(localHistory){
            Ui.add(controls,Ui.text(this,"Salvestise ajajoon",16,true),this,12);
            timeline=new SeekBar(this);timeline.setMax(1000);
            timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                @Override public void onStartTrackingTouch(SeekBar bar){}
                @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){}
                @Override public void onStopTrackingTouch(SeekBar bar){
                    if(player!=null){long length=player.getLength();if(length>0)player.setTime(length*bar.getProgress()/1000);}
                }
            });
            Ui.add(controls,timeline,this,4);
        }else{
            Ui.add(controls,Ui.text(this,"ONVIF kaamera pööramine / suum",17,true),this,12);
            onvifStatus=Ui.text(this,"Otsin ONVIF võimalusi …",12,false);
            onvifStatus.setTextColor(Ui.MUTED);Ui.add(controls,onvifStatus,this,1);
            addPTZControls(controls);
            Ui.add(controls,Ui.text(this,"Päeva- ja öörežiim",16,true),this,10);
            LinearLayout nightRow=new LinearLayout(this);nightRow.setGravity(Gravity.CENTER);
            nightButton(nightRow,"Auto","AUTO");
            nightButton(nightRow,"Päev","ON");
            nightButton(nightRow,"Öö","OFF");
            Ui.add(controls,nightRow,this,4);
            TextView hint=Ui.text(this,"ONVIF muudab IR lõikefiltrit. Öövalgustuse LED-id võivad olla tootjapõhised.",12,false);
            hint.setTextColor(Ui.MUTED);Ui.add(controls,hint,this,3);
            Button save=Ui.secondary(this,"● Alusta / lõpeta salvestus telefoni");
            save.setOnClickListener(v->toggleRecord(save));Ui.add(controls,save,this,10);
            Button history=Ui.secondary(this,"▣ Ajalugu: kohalikud / ONVIF salvestised");
            history.setOnClickListener(v->{
                Intent i=new Intent(this,HistoryActivity.class);
                i.putExtras(getIntent().getExtras());
                startActivity(i);
            });
            Ui.add(controls,history,this,6);
        }
        Button back=Ui.secondary(this,"← Tagasi");
        back.setOnClickListener(v->finish());Ui.add(controls,back,this,11);
        if(!localHistory)connectOnvif();
        handler.postDelayed(timeUpdater,1000);
    }
    private Spinner spinner(LinearLayout parent,String[] options){
        Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));
        Ui.add(parent,s,this,5);return s;
    }
    private void addPTZControls(LinearLayout root){
        LinearLayout up=new LinearLayout(this);up.setGravity(Gravity.CENTER);
        ptzButton(up,"↖",-0.45,+0.45,0);ptzButton(up,"↑",0,+0.6,0);ptzButton(up,"↗",+0.45,+0.45,0);
        Ui.add(root,up,this,6);
        LinearLayout middle=new LinearLayout(this);middle.setGravity(Gravity.CENTER);
        ptzButton(middle,"←",-0.65,0,0);
        Button home=Ui.secondary(this,"⌂");home.setOnClickListener(v->command("Algpositsioon",()->onvif.home()));
        middle.addView(home,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));
        ptzButton(middle,"→",+0.65,0,0);Ui.add(root,middle,this,3);
        LinearLayout down=new LinearLayout(this);down.setGravity(Gravity.CENTER);
        ptzButton(down,"↙",-0.45,-0.45,0);ptzButton(down,"↓",0,-0.6,0);ptzButton(down,"↘",+0.45,-0.45,0);
        Ui.add(root,down,this,3);
        LinearLayout zoom=new LinearLayout(this);zoom.setGravity(Gravity.CENTER);
        ptzButton(zoom,"− Suum",0,0,-0.6);ptzButton(zoom,"+ Suum",0,0,+0.6);
        Ui.add(root,zoom,this,7);
    }
    private void ptzButton(LinearLayout row,String title,double x,double y,double z){
        Button button=Ui.secondary(this,title);
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(0,Ui.dp(this,44),1);
        layout.leftMargin=Ui.dp(this,2);layout.rightMargin=Ui.dp(this,2);
        row.addView(button,layout);
        final Runnable[] refresh={null};
        button.setOnTouchListener((v,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    if(onvif==null||!onvif.supportsPtz()){report("ONVIF PTZ pole saadaval. Kontrolli mudelit ja autentimist.");return true;}
                    lastMove=System.currentTimeMillis();
                    sendMove(x,y,z);
                    refresh[0]=new Runnable(){
                        @Override public void run(){
                            if(v.isPressed()&&onvif!=null){
                                sendMove(x,y,z);handler.postDelayed(this,650);
                            }
                        }
                    };
                    handler.postDelayed(refresh[0],650);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if(refresh[0]!=null)handler.removeCallbacks(refresh[0]);
                    long delay=Math.max(0,250-(System.currentTimeMillis()-lastMove));
                    handler.postDelayed(()->command("Seiska",()->onvif.stop()),delay);
                    return true;
            }
            return true;
        });
    }
    private void sendMove(double x,double y,double z){
        command("Liigutan",()->onvif.move(x,y,z));
    }
    private interface OnvifOperation{void run()throws Exception;}
    private void command(String verb,OnvifOperation task){
        if(onvif==null){report("ONVIF pole ühendatud.");return;}
        background.execute(()->{
            try{task.run();}catch(Exception e){report(verb+" ei õnnestunud: "+e.getMessage());}
        });
    }
    private void nightButton(LinearLayout row,String title,String mode){
        Button b=Ui.secondary(this,title);
        b.setOnClickListener(v->{
            if(onvif==null||!onvif.supportsNight()){report("Öörežiimi ONVIF tugi puudub.");return;}
            command("IR filter",()->{
                onvif.night(mode);
                report("Pildirežiim: "+title+" (IR filter)");
            });
        });
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,43),1);
        p.leftMargin=Ui.dp(this,2);p.rightMargin=Ui.dp(this,2);row.addView(b,p);
    }
    private void connectOnvif(){
        if(cameraIp==null||!RtspNetwork.isPrivate(cameraIp)){
            onvifStatus.setText("ONVIF: IP puudub või aadress pole eravõrgus.");return;
        }
        background.execute(()->{
            try{
                OnvifClient c=new OnvifClient(cameraIp,onvifPort,username,password);
                c.connect();onvif=c;
                runOnUiThread(()->{
                    if(closed)return;
                    onvifStatus.setText("PTZ: "+(c.supportsPtz()?"võimalik":"pole tuvastatud")+
                        " • Öö: "+(c.supportsNight()?"võimalik":"pole tuvastatud"));
                    if(!c.streams.isEmpty()){
                        labels.clear();urls.clear();labels.add("Praegune voog");urls.add(streamUrl);
                        for(OnvifClient.Stream s:c.streams){labels.add(s.name);urls.add(s.url);}
                        settingStream=true;
                        streamSelector.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
                        streamSelector.setEnabled(true);streamSelector.setSelection(0);settingStream=false;
                    }
                });
            }catch(Exception e){runOnUiThread(()->{
                if(!closed)onvifStatus.setText("ONVIF ei vastanud: "+e.getMessage()+". Otsevideo võib siiski töötada.");
            });}
        });
    }
    @Override protected void onStart(){super.onStart();if(streamUrl!=null)startStream();}
    @Override protected void onStop(){stopStream();super.onStop();}
    private void startStream(){
        if(closed||streamUrl==null||video==null||renderSelector==null||bufferSelector==null)return;
        stopStream();int mode=renderSelector.getSelectedItemPosition();
        boolean hw=mode>=2,texture=mode==0||mode==2;
        int idx=bufferSelector.getSelectedItemPosition();
        int caching=buffers[Math.max(0,Math.min(idx,buffers.length-1))];
        status.setText("Ühendan ("+(hw?"riistvara":"tarkvara")+", "+caching+" ms) …");
        try{
            ArrayList<String> args=new ArrayList<>();
            args.add("--rtsp-tcp");
            args.add("--network-caching="+caching);
            args.add("--no-audio");
            engine=new LibVLC(this,args);player=new MediaPlayer(engine);
            player.attachViews(video,null,false,texture);
            player.setEventListener(e->{
                if(closed)return;
                if(e.type==MediaPlayer.Event.Playing)runOnUiThread(()->status.setText("● Esitus käib"));
                if(e.type==MediaPlayer.Event.EncounteredError)runOnUiThread(()->status.setText("Viga: videovoog ei avanenud. Kontrolli URL-i, õiguseid või vali teine dekooder."));
            });
            Media media=new Media(engine,Uri.parse(streamUrl));
            media.setHWDecoderEnabled(hw,hw);
            media.addOption(":rtsp-tcp");media.addOption(":network-caching="+caching);
            player.setMedia(media);media.release();player.play();
        }catch(Exception e){status.setText("Videomängija viga: "+e.getClass().getSimpleName());stopStream();}
    }
    private File recordingDir(){
        File base=getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if(base==null)base=getFilesDir();
        File dir=new File(base,"JoolinkViewer");
        if(!dir.exists())dir.mkdirs();
        return dir;
    }
    private void toggleRecord(Button button){
        if(player==null){report("Videovoog pole avatud.");return;}
        try{
            Method record=player.getClass().getMethod("record",String.class);
            Object result=record.invoke(player,recording?null:recordingDir().getAbsolutePath());
            if(result instanceof Boolean&&!((Boolean)result)){
                report("Salvestamise käivitamine ebaõnnestus.");return;
            }
            recording=!recording;
            button.setText(recording?"■ Lõpeta salvestamine":"● Alusta salvestust telefoni");
            report(recording?"Salvestan telefoni rakenduse filmikausta …":"Salvestus lõpetatud. Ava ajalugu.");
        }catch(Exception err){report("See VLC videomängija ei toeta salvestamist: "+err.getClass().getSimpleName());}
    }
    private void report(String text){if(!closed)runOnUiThread(()->{
        status.setText(text);
        Toast.makeText(this,text,Toast.LENGTH_SHORT).show();
    });}
    private void stopStream(){
        recording=false;
        try{
            if(player!=null){
                player.setEventListener(null);player.stop();player.detachViews();player.release();player=null;
            }
            if(engine!=null){engine.release();engine=null;}
        }catch(Exception ignored){}
    }
    @Override protected void onDestroy(){
        closed=true;
        handler.removeCallbacksAndMessages(null);
        if(onvif!=null&&onvif.supportsPtz())background.execute(()->{try{onvif.stop();}catch(Exception ignored){}});
        background.shutdown();
        stopStream();super.onDestroy();
    }
}
