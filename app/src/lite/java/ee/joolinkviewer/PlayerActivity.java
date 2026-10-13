package ee.joolinkviewer;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.ui.PlayerView;

public class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView video;
    private TextView status;
    private String stream;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        stream=getIntent().getStringExtra("url");
        if(stream==null||stream.isEmpty()){finish();return;}
        LinearLayout root=Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this,14),Ui.dp(this,15),Ui.dp(this,14),Ui.dp(this,15));
        setContentView(root);
        String name=getIntent().getStringExtra("camera");
        Ui.add(root,Ui.text(this,name==null?"Otsepilt":name,22,true),this,0);
        TextView engine=Ui.text(this,"LITE • Androidi videodekooder",12,false);
        engine.setTextColor(Ui.PRIMARY);
        Ui.add(root,engine,this,3);
        video=new PlayerView(this);
        video.setKeepScreenOn(true);
        root.addView(video,new LinearLayout.LayoutParams(-1,0,1));
        status=Ui.text(this,"Ühendan kaameraga...",13,false);
        status.setTextColor(Ui.MUTED);
        Ui.add(root,status,this,8);
        Button retry=Ui.button(this,"↻ Ühenda uuesti");
        retry.setOnClickListener(v->open());
        Ui.add(root,retry,this,9);
        Button landscape=Ui.secondary(this,"⛶ Rõhtvaade");
        landscape.setOnClickListener(v->{
            boolean isLandscape=getRequestedOrientation()==ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
            setRequestedOrientation(isLandscape?ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED:ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        });
        Ui.add(root,landscape,this,8);
        Button back=Ui.secondary(this,"← Tagasi");
        back.setOnClickListener(v->finish());
        Ui.add(root,back,this,8);
        TextView notice=Ui.text(this,"Kui Lite ei kuva otsepilti, kasuta VLC versiooni. ONVIF PTZ ja salvestamine on VLC variandis.",12,false);
        notice.setTextColor(Ui.MUTED);
        Ui.add(root,notice,this,9);
    }
    @Override protected void onStart(){super.onStart();open();}
    @Override protected void onStop(){releasePlayer();super.onStop();}
    private void releasePlayer(){
        if(video!=null)video.setPlayer(null);
        if(player!=null){player.release();player=null;}
    }
    private void open(){
        releasePlayer();
        try{
            player=new ExoPlayer.Builder(this).build();
            video.setPlayer(player);
            player.addListener(new Player.Listener(){
                @Override public void onPlaybackStateChanged(int state){
                    if(state==Player.STATE_READY)status.setText("● Videopilt töötab");
                    else if(state==Player.STATE_BUFFERING)status.setText("Laen videovoogu...");
                    else if(state==Player.STATE_ENDED)status.setText("Video lõppes");
                }
                @Override public void onPlayerError(PlaybackException e){
                    status.setText("Ei avanud videot. Proovi VLC varianti või kontrolli RTSP URL-i.");
                }
            });
            MediaItem item=MediaItem.fromUri(Uri.parse(stream));
            if(stream.toLowerCase().startsWith("rtsp://"))
                player.setMediaSource(new RtspMediaSource.Factory().setForceUseRtpTcp(true).createMediaSource(item));
            else
                player.setMediaSource(new DefaultMediaSourceFactory(this).createMediaSource(item));
            player.prepare();
            player.play();
        }catch(Exception e){status.setText("Video viga: "+e.getClass().getSimpleName());}
    }
}
