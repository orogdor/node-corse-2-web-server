package ee.joolinkviewer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class RtspNetwork {
    static final int[] PORTS={554,5540,8554,10554};
    static final int[] DEEP_RTSP={554,5540,8554,10554,1054,1554,10555};
    static final int[] ONVIF_PORTS={80,8080,8000,8899,2020,5000,5001,6000};
    static final String[] PATHS={
        "/live/ch0","/live/ch00_0","/onvif1","/stream1",
        "/h264Preview_01_main","/live/0","/Streaming/Channels/101",
        "/live/ch1","/live/ch00_1","/stream2","/h264","/videoMain"
    };
    interface ScanCallback{
        void found(String ip,int port);
        default void onvif(String ip,int port,String service){}
        default void status(String message){}
        void finish(String message);
    }
    static String wifiBase(Context c){
        ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm==null)return null;
        Network network=cm.getActiveNetwork();
        if(network==null)return null;
        NetworkCapabilities nc=cm.getNetworkCapabilities(network);
        if(nc==null||!nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return null;
        LinkProperties lp=cm.getLinkProperties(network);
        if(lp==null)return null;
        for(LinkAddress a:lp.getLinkAddresses()){
            InetAddress ip=a.getAddress();
            if(ip instanceof Inet4Address&&isPrivate(ip.getHostAddress())){
                String[] parts=ip.getHostAddress().split("\\.");
                return parts[0]+"."+parts[1]+"."+parts[2]+".";
            }
        }
        return null;
    }
    static boolean isPrivate(String ip){
        try{
            String[] p=ip.split("\\.");if(p.length!=4)return false;
            int a=Integer.parseInt(p[0]),b=Integer.parseInt(p[1]);
            return a==10||(a==172&&b>=16&&b<=31)||(a==192&&b==168);
        }catch(Exception e){return false;}
    }
    static int scanHosts(String base,ScanCallback cb){
        return scanPorts(base,PORTS,false,cb);
    }
    static int deepScan(Context ctx,String base,ScanCallback cb){
        cb.status("2/2: ONVIF-multiedastus + laiendatud võrgukontroll …");
        int byOnvif=discoverOnvif(ctx,cb);
        cb.status("2/2: kontrollin täiendavaid RTSP-porte …");
        int additional=scanPorts(base,DEEP_RTSP,true,cb);
        cb.status("2/2: kontrollin ONVIF veebiteenuseid …");
        int http=scanOnvifPorts(base,cb);
        return byOnvif+additional+http;
    }
    private static int scanPorts(String base,int[] ports,boolean deep,ScanCallback cb){
        ExecutorService pool=Executors.newFixedThreadPool(deep?36:32);
        ExecutorCompletionService<HostPorts> tasks=new ExecutorCompletionService<>(pool);
        try{
            for(int n=1;n<=254;n++){
                String ip=base+n;
                tasks.submit(()->{
                    HostPorts found=new HostPorts(ip);
                    for(int port:ports)if(open(ip,port,deep?350:220))found.ports.add(port);
                    return found;
                });
            }
            int count=0;
            for(int i=0;i<254;i++){
                HostPorts result=tasks.take().get();
                for(int p:result.ports){cb.found(result.ip,p);count++;}
                if(deep&&(i+1)%64==0)cb.status("2/2: "+(i+1)+"/254 võrgu aadressi kontrollitud");
            }
            return count;
        }catch(Exception err){cb.status("Võrgukontroll: "+err.getMessage());return 0;}
        finally{pool.shutdownNow();}
    }
    static int scanOnvifPorts(String base,ScanCallback cb){
        ExecutorService pool=Executors.newFixedThreadPool(32);
        ExecutorCompletionService<HostPorts> tasks=new ExecutorCompletionService<>(pool);
        try{
            for(int n=1;n<=254;n++){
                String ip=base+n;
                tasks.submit(()->{
                    HostPorts found=new HostPorts(ip);
                    for(int port:ONVIF_PORTS)if(looksLikeOnvif(ip,port))found.ports.add(port);
                    return found;
                });
            }
            int count=0;
            for(int i=0;i<254;i++){
                HostPorts hp=tasks.take().get();
                for(int p:hp.ports){cb.onvif(hp.ip,p,"http://"+hp.ip+":"+p+"/onvif/device_service");count++;}
            }
            return count;
        }catch(Exception err){cb.status("ONVIF otsing: "+err.getMessage());return 0;}
        finally{pool.shutdownNow();}
    }
    private static boolean looksLikeOnvif(String ip,int port){
        try(Socket socket=new Socket()){
            socket.connect(new InetSocketAddress(ip,port),350);socket.setSoTimeout(500);
            byte[] req=("POST /onvif/device_service HTTP/1.1\r\nHost: "+ip+"\r\nContent-Type: application/soap+xml\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
            socket.getOutputStream().write(req);
            BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));
            String first=reader.readLine();
            if(first==null||!first.contains("HTTP/"))return false;
            String line;
            for(int n=0;n<12&&(line=reader.readLine())!=null;n++){
                if(line.toLowerCase().contains("onvif")||line.toLowerCase().contains("soap"))return true;
            }
            return first.contains(" 401 ")||first.contains(" 405 ")||first.contains(" 500 ");
        }catch(Exception ignored){return false;}
    }
    static int discoverOnvif(Context ctx,ScanCallback cb){
        WifiManager.MulticastLock lock=null;
        Set<String> seen=new HashSet<>();
        try{
            WifiManager wifi=(WifiManager)ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if(wifi!=null){lock=wifi.createMulticastLock("JoolinkONVIF");lock.setReferenceCounted(false);lock.acquire();}
            try(DatagramSocket socket=new DatagramSocket()){
                socket.setSoTimeout(1300);
                String payload="<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
                    "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:a=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" "+
                    "xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" xmlns:dn=\"http://www.onvif.org/ver10/network/wsdl\"><s:Header>"+
                    "<a:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</a:Action>"+
                    "<a:MessageID>urn:uuid:"+UUID.randomUUID()+"</a:MessageID><a:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</a:To>"+
                    "</s:Header><s:Body><d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe></s:Body></s:Envelope>";
                byte[] out=payload.getBytes(StandardCharsets.UTF_8);
                DatagramPacket probe=new DatagramPacket(out,out.length,InetAddress.getByName("239.255.255.250"),3702);
                socket.send(probe);socket.send(probe);
                long until=System.currentTimeMillis()+2600;
                while(System.currentTimeMillis()<until){
                    try{
                        byte[] buf=new byte[9000];DatagramPacket pkt=new DatagramPacket(buf,buf.length);
                        socket.receive(pkt);
                        String ip=pkt.getAddress().getHostAddress();
                        if(!isPrivate(ip))continue;
                        String xml=new String(pkt.getData(),pkt.getOffset(),pkt.getLength(),StandardCharsets.UTF_8);
                        if(!xml.contains("ProbeMatch"))continue;
                        String address="http://"+ip+"/onvif/device_service";
                        java.util.regex.Matcher m=java.util.regex.Pattern.compile("<(?:[\\w-]+:)?XAddrs[^>]*>([^<]+)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xml);
                        if(m.find()){
                            for(String candidate:m.group(1).trim().split("\\s+")){
                                try{
                                    Uri u=Uri.parse(candidate);
                                    if(ip.equals(u.getHost())&&u.getPort()>0){address=candidate;break;}
                                }catch(Exception ignored){}
                            }
                        }
                        int p=Uri.parse(address).getPort();if(p<=0)p=80;
                        if(seen.add(ip+":"+p))cb.onvif(ip,p,address);
                    }catch(java.net.SocketTimeoutException timeout){break;}
                }
            }
        }catch(Exception err){cb.status("ONVIF multicast ei vastanud; jätkan aadresside kontrolliga.");}
        finally{if(lock!=null&&lock.isHeld())lock.release();}
        return seen.size();
    }
    private static class HostPorts{
        final String ip;final List<Integer> ports=new ArrayList<>();
        HostPorts(String s){ip=s;}
    }
    static boolean open(String ip,int port,int ms){
        try(Socket s=new Socket()){s.connect(new InetSocketAddress(ip,port),ms);return true;}
        catch(Exception e){return false;}
    }
    static String findPath(String ip,int port){
        String auth=null;
        for(String path:PATHS){
            String url="rtsp://"+ip+":"+port+path;
            int status=describe(ip,port,url);
            if(status==200)return url;
            if(status==401&&auth==null)auth=url;
        }
        return auth!=null?auth:"rtsp://"+ip+":"+port+PATHS[0];
    }
    private static int describe(String ip,int port,String url){
        try(Socket s=new Socket()){
            s.connect(new InetSocketAddress(ip,port),550);s.setSoTimeout(800);
            OutputStream stream=s.getOutputStream();
            stream.write(("DESCRIBE "+url+" RTSP/1.0\r\nCSeq: 1\r\nAccept: application/sdp\r\nUser-Agent: JoolinkViewer/1.1\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            stream.flush();
            String line=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.US_ASCII)).readLine();
            if(line!=null&&line.startsWith("RTSP/")){
                String[] words=line.split(" ");if(words.length>1)return Integer.parseInt(words[1]);
            }
        }catch(Exception ignored){}
        return -1;
    }
    static String stripCredentials(String url){
        return url==null?"":url.replaceFirst("(?i)^((?:rtsp|rtsps|https?|rtmp)://)[^/@]+@","$1");
    }
    static String withCredentials(String url,String user,String pass){
        String safe=stripCredentials(url.trim());
        if(user==null||user.isEmpty())return url.trim();
        int sep=safe.indexOf("://");if(sep<0)return safe;
        String a=Uri.encode(user,"")+(pass==null||pass.isEmpty()?"":":"+Uri.encode(pass,""));
        return safe.substring(0,sep+3)+a+"@"+safe.substring(sep+3);
    }
    static boolean validUrl(String url){
        try{
            Uri u=Uri.parse(url);String scheme=u.getScheme();
            return u.getHost()!=null&&!u.getHost().isEmpty()&&
                ("rtsp".equalsIgnoreCase(scheme)||"rtsps".equalsIgnoreCase(scheme)||
                "http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme)||
                "rtmp".equalsIgnoreCase(scheme));
        }catch(Exception e){return false;}
    }
}
