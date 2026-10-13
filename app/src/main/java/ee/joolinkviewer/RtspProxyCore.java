package ee.joolinkviewer;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pure-Java RTSP interleaved-TCP relay. No transcoding, no Joolink cloud P2P.
 * A new upstream TCP session is opened for every downstream RTSP client.
 * Restricted to private LAN or, when opted in, Tailscale-style 100.64/10.
 */
public final class RtspProxyCore implements Closeable {
    public static final int DEFAULT_PORT=8554;
    private final String host, upstreamBase;
    private final int upstreamPort, listenPort;
    private final boolean vpnAllowed;
    private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
    private final AtomicInteger viewers=new AtomicInteger();
    private final ExecutorService pool=Executors.newCachedThreadPool();
    private volatile ServerSocket listener;
    private volatile boolean running;
    private volatile String lastError="";
    public RtspProxyCore(String sourceUrl,int port,boolean vpn){
        URI source=URI.create(sourceUrl);
        if(!"rtsp".equalsIgnoreCase(source.getScheme())||source.getHost()==null)
            throw new IllegalArgumentException("RTSP URL vajalik");
        if(!isLan(source.getHost()))
            throw new IllegalArgumentException("Lahtekaamera peab olema kohalikus LAN vorgus");
        if(port<1024||port>65535)throw new IllegalArgumentException("Vale port");
        host=source.getHost();
        upstreamPort=source.getPort()>0?source.getPort():554;
        upstreamBase="rtsp://"+host+":"+upstreamPort;
        listenPort=port;
        vpnAllowed=vpn;
    }
    public synchronized void start() throws Exception{
        if(running)return;
        listener=new ServerSocket(listenPort,12);
        running=true;
        pool.execute(()->{
            while(running){
                try{
                    Socket client=listener.accept();
                    if(!allowed(client.getInetAddress().getHostAddress(),vpnAllowed)){
                        client.close();continue;
                    }
                    if(viewers.get()>=8){client.close();continue;}
                    viewers.incrementAndGet();
                    sockets.add(client);
                    pool.execute(()->session(client));
                }catch(Exception e){
                    if(running)lastError=e.toString();
                    if(running)break;
                }
            }
        });
    }
    public int getViewers(){return viewers.get();}
    public boolean isRunning(){return running;}
    public String getLastError(){return lastError;}
    public static boolean isLan(String ip){return allowed(ip,false);}
    public static boolean isTailnet(String ip){
        try{
            String[] p=ip.split("\\.");
            return p.length==4&&Integer.parseInt(p[0])==100&&Integer.parseInt(p[1])>=64&&Integer.parseInt(p[1])<=127;
        }catch(Exception e){return false;}
    }
    public static boolean allowed(String ip,boolean vpn){
        try{
            String[] s=ip.split("\\.");
            if(s.length!=4)return false;
            int a=Integer.parseInt(s[0]),b=Integer.parseInt(s[1]);
            for(String v:s)if(Integer.parseInt(v)<0||Integer.parseInt(v)>255)return false;
            return a==127||a==10||(a==172&&b>=16&&b<=31)||(a==192&&b==168)||(vpn&&isTailnet(ip));
        }catch(Exception e){return false;}
    }
    private void session(Socket downstream){
        Socket upstream=new Socket();
        try{
            upstream.connect(new InetSocketAddress(host,upstreamPort),4500);
            upstream.setTcpNoDelay(true);downstream.setTcpNoDelay(true);
            sockets.add(upstream);
            // Two independently framed directions, including binary $ interleaved RTP/RTCP.
            pool.execute(()->pipe(downstream,upstream,true));
            pipe(upstream,downstream,false);
        }catch(Exception e){lastError=e.getClass().getSimpleName()+": "+e.getMessage();}
        finally{
            closeQuietly(upstream);closeQuietly(downstream);
            sockets.remove(upstream);sockets.remove(downstream);
            viewers.decrementAndGet();
        }
    }
    private static boolean readExact(InputStream in,byte[] bytes){
        try{
            for(int pos=0;pos<bytes.length;){
                int n=in.read(bytes,pos,bytes.length-pos);
                if(n<0)return false;
                pos+=n;
            }
            return true;
        }catch(Exception ex){return false;}
    }
    private static int lengthOf(String header){
        for(String line:header.split("\r\n")){
            if(line.toLowerCase(Locale.US).startsWith("content-length:")){
                try{return Integer.parseInt(line.substring(line.indexOf(':')+1).trim());}
                catch(Exception ignored){}
            }
        }
        return 0;
    }
    private void pipe(Socket inSocket,Socket outSocket,boolean request){
        try{
            InputStream in=inSocket.getInputStream();
            OutputStream out=outSocket.getOutputStream();
            while(running&&!inSocket.isClosed()&&!outSocket.isClosed()){
                int start=in.read();
                if(start<0)break;
                if(start==36){ // RTSP interleaved RTP packet
                    byte[] h=new byte[3];
                    if(!readExact(in,h))break;
                    int size=(h[1]&255)*256+(h[2]&255);
                    out.write(36);out.write(h);
                    byte[] buf=new byte[4096];
                    while(size>0){
                        int n=in.read(buf,0,Math.min(size,buf.length));
                        if(n<0)return;
                        out.write(buf,0,n);size-=n;
                    }
                    out.flush();
                    continue;
                }
                ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                buffer.write(start);
                int tail=start,limit=0;
                while(limit++<65536){
                    int ch=in.read();if(ch<0)return;
                    buffer.write(ch);
                    tail=(tail<<8)|(ch&255);
                    if(tail==0x0d0a0d0a)break;
                }
                if(limit>=65536)return;
                String header=new String(buffer.toByteArray(),StandardCharsets.ISO_8859_1);
                int length=lengthOf(header);
                if(length<0||length>2*1024*1024)return;
                byte[] body=new byte[length];
                if(!readExact(in,body))return;
                if(request){
                    // Replace only the RTSP server authority, leave path and RTSP method untouched.
                    header=header.replaceAll("rtsp://[^/\\s]+(?=/|\\s)",upstreamBase);
                }else{
                    String target="rtsp://"+outSocket.getLocalAddress().getHostAddress()+":"+listenPort;
                    header=header.replace(upstreamBase,target);
                    if(length>0){
                        String payload=new String(body,StandardCharsets.ISO_8859_1).replace(upstreamBase,target);
                        body=payload.getBytes(StandardCharsets.ISO_8859_1);
                    }
                }
                header=header.replaceAll("(?im)^Content-Length:\\s*\\d+","Content-Length: "+body.length);
                out.write(header.getBytes(StandardCharsets.ISO_8859_1));
                out.write(body);
                out.flush();
            }
        }catch(Exception ignored){}finally{
            closeQuietly(inSocket);closeQuietly(outSocket);
        }
    }
    private static void closeQuietly(Socket socket){try{socket.close();}catch(Exception ignored){}}
    @Override public synchronized void close(){
        running=false;
        try{if(listener!=null)listener.close();}catch(Exception ignored){}
        for(Socket s:sockets)closeQuietly(s);
        sockets.clear();pool.shutdownNow();
    }
}
