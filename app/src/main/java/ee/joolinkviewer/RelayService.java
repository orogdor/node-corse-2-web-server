package ee.joolinkviewer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RelayService extends Service {
    static final String START="ee.joolinkviewer.relay.START";
    static final String STOP="ee.joolinkviewer.relay.STOP";
    private static final String CHANNEL="rtsp_relay";
    private volatile boolean running;
    private volatile ServerSocket listener;
    private ExecutorService workers;
    private Uri source;
    private String upstreamBase;
    private String relayBase;
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent i,int flags,int startId){
        if(i==null||STOP.equals(i.getAction())){stopSelf();return START_NOT_STICKY;}
        if(!START.equals(i.getAction()))return START_NOT_STICKY;
        String url=i.getStringExtra("url");
        Uri uri=Uri.parse(url==null?"":url);
        if(!"rtsp".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null){
            stopSelf();return START_NOT_STICKY;
        }
        if(running){stopListening();}
        source=uri;
        int port=uri.getPort()>0?uri.getPort():554;
        upstreamBase="rtsp://"+uri.getHost()+":"+port;
        relayBase="rtsp://"+RelayActivity.localIp()+":8554";
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"HomeJoolink RTSP saatja",NotificationManager.IMPORTANCE_LOW));
        Notification notification=new Notification.Builder(this,CHANNEL)
            .setContentTitle("HomeJoolink LAN RTSP saatja")
            .setContentText("Vahendan ühendusi pordil 8554")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true).build();
        startForeground(8554,notification);
        running=true;workers=Executors.newCachedThreadPool();
        workers.execute(()->serve());
        return START_NOT_STICKY;
    }
    private void serve(){
        try(ServerSocket server=new ServerSocket(8554,4)){
            listener=server;
            while(running){
                Socket client=server.accept();
                String ip=client.getInetAddress().getHostAddress();
                if(!RtspNetwork.isPrivate(ip)&&!client.getInetAddress().isLoopbackAddress()){
                    client.close();continue;
                }
                workers.execute(()->proxy(client));
            }
        }catch(Exception ignored){}finally{if(running)stopSelf();}
    }
    private void proxy(Socket client){
        Socket upstream=new Socket();
        try{
            int port=source.getPort()>0?source.getPort():554;
            upstream.connect(new InetSocketAddress(source.getHost(),port),5000);
            upstream.setTcpNoDelay(true);client.setTcpNoDelay(true);
            String host=relayBase;
            Thread forward=new Thread(()->pipe(client,upstream,true,host),"rtsp-client-to-camera");
            forward.setDaemon(true);forward.start();
            pipe(upstream,client,false,host);
        }catch(Exception ignored){}finally{
            try{client.close();}catch(Exception ignored){}
            try{upstream.close();}catch(Exception ignored){}
        }
    }
    // RTSP over interleaved TCP: $ packets are copied verbatim; RTSP messages have headers and optional body.
    // Rewrites absolute RTSP URIs / SDP control URI references so clients keep connecting to the relay.
    private void pipe(Socket from,Socket to,boolean outbound,String host){
        try{
            InputStream input=from.getInputStream();OutputStream output=to.getOutputStream();
            while(running&&!from.isClosed()&&!to.isClosed()){
                int first=input.read();if(first<0)break;
                if(first==36){
                    byte[] hdr=new byte[3];if(!readAll(input,hdr))break;
                    int length=((hdr[1]&255)<<8)|(hdr[2]&255);
                    output.write(36);output.write(hdr);
                    byte[] buf=new byte[8192];
                    while(length>0){
                        int n=input.read(buf,0,Math.min(length,buf.length));
                        if(n<0)return;
                        output.write(buf,0,n);length-=n;
                    }
                    output.flush();continue;
                }
                ByteArrayOutputStream header=new ByteArrayOutputStream();
                header.write(first);
                int last3=0,limit=0;
                while(limit++<65536){
                    int b=input.read();if(b<0)return;
                    header.write(b);
                    last3=((last3<<8)|(b&255));
                    if(last3==0x0d0a0d0a)break;
                }
                if(limit>=65536)return;
                String headers=new String(header.toByteArray(),StandardCharsets.ISO_8859_1);
                int bodyLength=0;
                for(String line:headers.split("\r\n")){
                    if(line.toLowerCase(Locale.US).startsWith("content-length:")){
                        try{bodyLength=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());}catch(Exception ignored){}
                    }
                }
                if(bodyLength<0||bodyLength>2*1024*1024)return;
                byte[] body=new byte[bodyLength];
                if(bodyLength>0&&!readAll(input,body))return;
                String sourceBase=upstreamBase;
                String destBase=host;
                if(outbound){
                    headers=headers.replace(destBase,sourceBase)
                                   .replace("rtsp://127.0.0.1:8554",sourceBase);
                }else{
                    headers=headers.replace(sourceBase,destBase)
                                   .replace(sourceBase.replace(":554",""),destBase);
                    if(bodyLength>0){
                        String sdp=new String(body,StandardCharsets.ISO_8859_1);
                        sdp=sdp.replace(sourceBase,destBase);
                        body=sdp.getBytes(StandardCharsets.ISO_8859_1);
                        bodyLength=body.length;
                        headers=headers.replaceAll("(?im)^Content-Length:\\s*\\d+","Content-Length: "+bodyLength);
                    }
                }
                output.write(headers.getBytes(StandardCharsets.ISO_8859_1));
                if(bodyLength>0)output.write(body);
                output.flush();
            }
        }catch(Exception ignored){}
    }
    private static boolean readAll(InputStream input,byte[] b){
        try{int p=0;while(p<b.length){int n=input.read(b,p,b.length-p);if(n<0)return false;p+=n;}return true;}
        catch(Exception e){return false;}
    }
    private void stopListening(){
        running=false;
        try{if(listener!=null)listener.close();}catch(Exception ignored){}
        if(workers!=null)workers.shutdownNow();
    }
    @Override public void onDestroy(){stopListening();super.onDestroy();}
}
