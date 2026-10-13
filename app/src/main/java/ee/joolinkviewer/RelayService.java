package ee.joolinkviewer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;

/**
 * Android / Android TV foreground relay service. Every boot starts it only if user opted in.
 * No port forwarding: remote connections require a separate installed VPN client (e.g. Tailscale).
 */
public final class RelayService extends Service {
    public static final String START="ee.joolinkviewer.relay.START";
    public static final String STOP="ee.joolinkviewer.relay.STOP";
    public static final String PREFS="homejoolink_relay";
    public static final String CHANNEL="rtsp_relay";
    private RtspProxyCore server;
    private static volatile boolean alive;
    public static boolean isLive(){return alive;}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||STOP.equals(intent.getAction())){
            stopSelf();
            return START_NOT_STICKY;
        }
        SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);
        String source=intent.getStringExtra("url");
        if(source==null||source.isEmpty())source=p.getString("url","");
        if(source.isEmpty()){stopSelf();return START_NOT_STICKY;}
        boolean vpn=p.getBoolean("vpn",false);
        try{
            if(server!=null)server.close();
            // On Android 8+ show the foreground notification before starting network traffic.
            NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,"HomeJoolink saatja",NotificationManager.IMPORTANCE_LOW));
            Intent open=new Intent(this,RelayActivity.class);
            PendingIntent pending=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification notice=new Notification.Builder(this,CHANNEL)
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setContentTitle("HomeJoolink • RTSP saatja")
                .setContentText("Kohalik voog :8554 • kaugvaade ainult VPN-iga")
                .setContentIntent(pending).setOngoing(true).build();
            startForeground(8554,notice);
            server=new RtspProxyCore(source,RtspProxyCore.DEFAULT_PORT,vpn);
            server.start();
            alive=true;
            return START_STICKY;
        }catch(Exception e){
            alive=false;
            p.edit().putString("last_error",e.toString()).apply();
            stopSelf();return START_NOT_STICKY;
        }
    }
    @Override public void onDestroy(){
        alive=false;
        if(server!=null){server.close();server=null;}
        super.onDestroy();
    }
}
