package ee.joolinkviewer;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * EXPERIMENT ONLY: Test a vendor UID -> KP2P WebSocket -> TURN/relay handshake.
 * No camera passwords/tokens are used here; no direct LAN/P2P attempt is made.
 * This does not decode or display video. Success proves only server relay discovery.
 * Protocol parameters are based on public Jooan/Juanvision interoperability research.
 */
final class JooanRelayProbe {
    private static final int TIMEOUT = 8000;
    private static final byte[] ARQ_OPEN = fromHex("d9ffcc028c38eed2d199ac6026947fae");
    private static final byte[] ARQ_OK = fromHex("96d5390d12fcbe8f4790d932ccd849f3");
    private static final byte[] TRANSPORT = fromHex("cefaeffe");
    private JooanRelayProbe() {}

    static String test(String uid, Consumer<String> progress) throws Exception {
        if (uid == null || !uid.matches("[A-Za-z0-9_-]{5,64}"))
            throw new IllegalArgumentException("Seadme UID peab olema 5–64 tähe/numbriga tunnus.");
        progress.accept("1/3: pärin KP2P serverist UID olemasolu …");
        String url = "http://ngw.dvr163.com/address/client?id=" +
                URLEncoder.encode(uid, "UTF-8") + "&ch_count=1&r=" +
                (1 + new SecureRandom().nextInt(1000000));
        HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(TIMEOUT);
        JSONObject response;
        try {
            if(conn.getResponseCode()!=200)
                throw new IOException("UID server vastas HTTP " + conn.getResponseCode());
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try(InputStream input=conn.getInputStream()) {
                byte[] buf=new byte[1024]; int n;
                while((n=input.read(buf))!=-1) {
                    if(body.size()+n>16384)throw new IOException("Serveri vastus on liiga suur");
                    body.write(buf,0,n);
                }
            }
            response=new JSONObject(body.toString("UTF-8"));
        } finally { conn.disconnect(); }

        String ip=response.optString("ipv4","");
        int port=response.optInt("tcpport",0);
        int p2pKey=response.optInt("apconv",0);
        int sid=response.optInt("sid",0);
        if(!publicIpv4(ip)||port<1||port>65535||p2pKey==0||sid==0)
            throw new IOException("Server ei leidnud ühilduvat KP2P UID-d või saatis puudulikud andmed");
        progress.accept("2/3: kontrollin tootja KP2P ühendusserverit …");
        try(Ws ws=new Ws(ip,port)) {
            ws.send(concat(ARQ_OPEN,u32(p2pKey)));
            boolean accepted=false;
            long deadline=System.currentTimeMillis()+TIMEOUT;
            while(System.currentTimeMillis()<deadline) {
                byte[] b=ws.readBinary();
                if(Arrays.equals(b,ARQ_OK)){accepted=true;break;}
            }
            if(!accepted)throw new IOException("KP2P server ei kinnitanud ühendust");
            progress.accept("3/3: küsin TURN-vahendusserveri aadressi …");
            byte[] uidData=uid.getBytes(StandardCharsets.UTF_8);
            byte[] turnReq=new byte[40];
            System.arraycopy(uidData,0,turnReq,0,Math.min(uidData.length,32));
            System.arraycopy(u32(1),0,turnReq,36,4);
            byte[] header=concat(fromHex("abbccdde"),u32(12),u32(1),u32(0),
                    u32(sid),u32(0),u32(0),u32(turnReq.length),turnReq);
            ws.send(concat(TRANSPORT,u32(header.length)));
            ws.send(header);
            deadline=System.currentTimeMillis()+TIMEOUT;
            while(System.currentTimeMillis()<deadline) {
                byte[] data=ws.readBinary();
                if(data.length<32||readU32(data,0)!=0xdecdbcabL) {
                    // Magic is network order (AB BC CD DE), checked explicitly below.
                    if(data.length<32||data[0]!=(byte)0xab||data[1]!=(byte)0xbc||
                       data[2]!=(byte)0xcd||data[3]!=(byte)0xde)continue;
                }
                if(readU32(data,4)!=16)continue; // TURN_S2A
                int size=(int)readU32(data,28);
                if(size<64||32L+size>data.length)continue;
                int offset=32;
                String relayIp=(data[offset+40]&255)+"."+(data[offset+41]&255)+"."+
                        (data[offset+42]&255)+"."+(data[offset+43]&255);
                long relayPort=readU32(data,offset+60);
                if(!publicIpv4(relayIp)||relayPort<1||relayPort>65535)
                    throw new IOException("TURN serveri aadress ei ole kehtiv avalik IPv4");
                return "TURN relay leitud ("+relayIp+":"+relayPort+"). " +
                       "Serveriühendus on võimalik; videokaadrite autentimine ja dekodeerimine on veel puudu.";
            }
            throw new IOException("TURN relay vastust ei tulnud; KP2P protokoll võib erineda");
        }
    }

    private static long readU32(byte[] bytes,int off) {
        return ((long)bytes[off]&255)|(((long)bytes[off+1]&255)<<8)|
               (((long)bytes[off+2]&255)<<16)|(((long)bytes[off+3]&255)<<24);
    }
    private static boolean publicIpv4(String host) {
        if(!host.matches("(\\d{1,3}\\.){3}\\d{1,3}"))return false;
        try {
            InetAddress a=InetAddress.getByName(host);
            byte[] b=a.getAddress();
            int first=b[0]&255,second=b[1]&255;
            return b.length==4&&first>0&&first<224&&first!=10&&first!=127&&
                   !(first==169&&second==254)&&!(first==172&&second>=16&&second<=31)&&
                   !(first==192&&second==168)&&!(first==100&&second>=64&&second<=127)&&
                   !a.isLoopbackAddress()&&!a.isAnyLocalAddress()&&!a.isSiteLocalAddress();
        }catch(Exception e){return false;}
    }
    private static byte[] u32(long x) {return new byte[]{
            (byte)x,(byte)(x>>>8),(byte)(x>>>16),(byte)(x>>>24)};}
    private static byte[] concat(byte[]... arrays){
        int n=0;for(byte[] a:arrays)n+=a.length;
        byte[] result=new byte[n];int p=0;
        for(byte[] a:arrays){System.arraycopy(a,0,result,p,a.length);p+=a.length;}
        return result;
    }
    private static byte[] fromHex(String s) {
        byte[] b=new byte[s.length()/2];
        for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);
        return b;
    }
    private static final class Ws implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final SecureRandom random=new SecureRandom();
        Ws(String host,int port)throws Exception{
            socket=new Socket();socket.connect(new InetSocketAddress(host,port),TIMEOUT);
            socket.setSoTimeout(TIMEOUT);
            in=socket.getInputStream();out=socket.getOutputStream();
            byte[] nonce=new byte[16];random.nextBytes(nonce);
            String key=Base64.getEncoder().encodeToString(nonce);
            String req="GET / HTTP/1.1\r\nHost: "+host+":"+port+
                "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"+
                "Sec-WebSocket-Version: 13\r\nSec-WebSocket-Key: "+key+"\r\n\r\n";
            out.write(req.getBytes(StandardCharsets.US_ASCII));out.flush();
            ByteArrayOutputStream headers=new ByteArrayOutputStream();
            int tail=0,c;
            while(headers.size()<8192&&(c=in.read())!=-1){
                headers.write(c);tail=((tail<<8)|(c&255));
                if(tail==0x0d0a0d0a)break;
            }
            String h=headers.toString("US-ASCII");
            if(!h.startsWith("HTTP/1.1 101")&&!h.startsWith("HTTP/1.0 101"))
                throw new IOException("KP2P server ei toeta WebSocket ühendust");
            String accept="";
            for(String line:h.split("\r\n"))if(line.toLowerCase(java.util.Locale.ROOT).startsWith("sec-websocket-accept:"))
                accept=line.substring(line.indexOf(':')+1).trim();
            byte[] sha=MessageDigest.getInstance("SHA-1").digest((key+
                "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII));
            if(!Base64.getEncoder().encodeToString(sha).equals(accept))
                throw new IOException("WebSocket vastuse allkiri on vale");
        }
        void send(byte[] payload)throws IOException {frame(2,payload);}
        private void frame(int opcode,byte[] payload)throws IOException{
            if(payload.length>65535)throw new IOException("Kaader liiga suur");
            out.write(0x80|opcode);
            if(payload.length<126)out.write(0x80|payload.length);
            else {out.write(0xfe);out.write(payload.length>>>8);out.write(payload.length);}
            byte[] mask=new byte[4];random.nextBytes(mask);out.write(mask);
            for(int i=0;i<payload.length;i++)out.write(payload[i]^mask[i%4]);
            out.flush();
        }
        byte[] readBinary()throws IOException {
            for(int attempts=0;attempts<100;attempts++){
                int a=in.read(),b=in.read();
                if(a<0||b<0)throw new EOFException("WebSocket ühendus katkes");
                int opcode=a&15;long len=b&127;
                if(len==126)len=(in.read()<<8)|in.read();
                else if(len==127){
                    len=0;for(int i=0;i<8;i++)len=(len<<8)|(in.read()&255);
                }
                if(len>1048576)throw new IOException("Ootamatult suur WebSocket kaader");
                byte[] mask=null;
                if((b&128)!=0){mask=new byte[4];readFully(mask);}
                byte[] data=new byte[(int)len];readFully(data);
                if(mask!=null)for(int i=0;i<data.length;i++)data[i]^=mask[i%4];
                if(opcode==8)throw new IOException("Server sulges WebSocket ühenduse");
                if(opcode==9){frame(10,data);continue;}
                if(opcode==2)return data;
            }
            throw new IOException("KP2P andmekaadrit ei tulnud");
        }
        private void readFully(byte[] a)throws IOException{
            int pos=0;while(pos<a.length){int n=in.read(a,pos,a.length-pos);
                if(n<0)throw new EOFException();pos+=n;}
        }
        @Override public void close()throws IOException{socket.close();}
    }
}
