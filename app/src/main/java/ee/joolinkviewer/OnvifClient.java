package ee.joolinkviewer;

import android.net.Uri;
import android.util.Base64;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilderFactory;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.LinkedHashSet;
import java.util.Set;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local-network ONVIF services, only for devices the user owns. */
final class OnvifClient {
    private static final String TDS = "http://www.onvif.org/ver10/device/wsdl";
    private static final String TRT = "http://www.onvif.org/ver10/media/wsdl";
    private static final String TPTZ = "http://www.onvif.org/ver20/ptz/wsdl";
    private static final String TIMG = "http://www.onvif.org/ver20/imaging/wsdl";
    private static final String TRC = "http://www.onvif.org/ver10/recording/wsdl";
    private static final String TRP = "http://www.onvif.org/ver10/replay/wsdl";
    static final class Stream {
        final String name, url, profile;
        Stream(String name, String url, String profile) {this.name=name;this.url=url;this.profile=profile;}
        @Override public String toString(){return name;}
    }
    static final class RemoteRecording {
        final String name, uri;
        RemoteRecording(String name, String uri){this.name=name;this.uri=uri;}
    }
    final String ip, user, pass;
    String deviceUrl, mediaUrl, ptzUrl, imagingUrl, recordingUrl, replayUrl;
    String profileToken, videoSourceToken;
    final List<Stream> streams = new ArrayList<>();
    boolean ptzAvailable, imagingAvailable, recordingAvailable;
    private String lastFault = "";

    OnvifClient(String address, int port, String username, String password) {
        ip=address;
        user=username==null?"":username;
        pass=password==null?"":password;
        deviceUrl="http://"+ip+":"+(port>0?port:80)+"/onvif/device_service";
    }
    /** Auto-detect ONVIF port independently of working RTSP port. */
    static OnvifClient autoConnect(String ip,int preferred,String user,String password) throws Exception {
        if(!RtspNetwork.isPrivate(ip))throw new Exception("Vaja on kaamera kohaliku võrgu IP-aadressi");
        Set<Integer> candidates=new LinkedHashSet<>();
        if(preferred>0&&preferred<65536)candidates.add(preferred);
        for(int p:new int[]{8899,80,8080,5000,8000,2020,6000,5001})candidates.add(p);
        String last="ONVIF teenust ei leitud";
        for(int port:candidates){
            try(Socket socket=new Socket()){
                socket.connect(new InetSocketAddress(ip,port),350);
            }catch(Exception ignored){continue;}
            try{
                OnvifClient result=new OnvifClient(ip,port,user,password);
                if(result.connect())return result;
                last="ONVIF port "+port+" ei tagastanud profiile";
            }catch(Exception e){
                last="Port "+port+": "+e.getMessage();
                if(last.contains("HTTP 401")||last.contains("NotAuthorized"))throw new Exception(last);
            }
        }
        throw new Exception(last+". Kontrolli ONVIF porti (sageli 8899) ja kaamera ONVIF kontot.");
    }
    int devicePort(){try{return new URL(deviceUrl).getPort();}catch(Exception e){return 80;}}
    boolean connect() throws Exception {
        // Some cameras expose ONVIF on a nonstandard port. Do not send credentials to off-host URLs.
        String body="<tds:GetCapabilities><tds:Category>All</tds:Category></tds:GetCapabilities>";
        Document d=call(deviceUrl,TDS,"GetCapabilities",body);
        Element capabilities=first(d.getDocumentElement(),"Capabilities");
        if(capabilities==null)throw new Exception("ONVIF capabilities puuduvad");
        mediaUrl=service(capabilities,"Media");
        ptzUrl=service(capabilities,"PTZ");
        imagingUrl=service(capabilities,"Imaging");
        recordingUrl=service(capabilities,"Recording");
        replayUrl=service(capabilities,"Replay");
        ptzAvailable=ptzUrl!=null;
        imagingAvailable=imagingUrl!=null;
        try{
            Document services=call(deviceUrl,TDS,"GetServices","<tds:GetServices><tds:IncludeCapability>true</tds:IncludeCapability></tds:GetServices>");
            for(Element serviceNode:all(services.getDocumentElement(),"Service")){
                String ns=value(serviceNode,"Namespace"),addr=value(serviceNode,"XAddr");
                if(ns==null||addr==null)continue;
                String checked=checkedServiceUrl(addr);
                if(checked==null)continue;
                if(ns.contains("/recording/wsdl"))recordingUrl=checked;
                else if(ns.contains("/replay/wsdl"))replayUrl=checked;
                else if(ns.contains("/media/wsdl")&&mediaUrl==null)mediaUrl=checked;
                else if(ns.contains("/ptz/wsdl")&&ptzUrl==null)ptzUrl=checked;
                else if(ns.contains("/imaging/wsdl")&&imagingUrl==null)imagingUrl=checked;
            }
        }catch(Exception ignored){} // Older ONVIF cameras do not implement GetServices.
        ptzAvailable=ptzUrl!=null;
        imagingAvailable=imagingUrl!=null;
        recordingAvailable=recordingUrl!=null&&replayUrl!=null;
        if(mediaUrl==null)mediaUrl="http://"+ip+":"+devicePort()+"/onvif/media_service";
        Document profiles=call(mediaUrl,TRT,"GetProfiles","<trt:GetProfiles/>");
        for(Element p:all(profiles.getDocumentElement(),"Profiles")){
            String token=p.getAttribute("token");
            if(token.isEmpty())continue;
            if(profileToken==null)profileToken=token;
            Element src=first(p,"VideoSourceConfiguration");
            if(videoSourceToken==null&&src!=null)videoSourceToken=value(src,"SourceToken");
            String display=value(p,"Name");
            Element cfg=first(p,"VideoEncoderConfiguration");
            String encoding=cfg==null?"":value(cfg,"Encoding");
            Element resolution=cfg==null?null:first(cfg,"Resolution");
            String dims=resolution==null?"":value(resolution,"Width")+"x"+value(resolution,"Height");
            if(display==null||display.isEmpty())display="Voog "+(streams.size()+1);
            String streamUri="";
            try {
                String req="<trt:GetStreamUri><trt:StreamSetup><tt:Stream>RTP-Unicast</tt:Stream><tt:Transport><tt:Protocol>RTSP</tt:Protocol></tt:Transport></trt:StreamSetup><trt:ProfileToken>"+
                    esc(token)+"</trt:ProfileToken></trt:GetStreamUri>";
                streamUri=value(call(mediaUrl,TRT,"GetStreamUri",req).getDocumentElement(),"Uri");
            } catch(Exception ignored){}
            if(streamUri!=null&&!streamUri.isEmpty()&&allowedStream(streamUri)){
                streams.add(new Stream(display+"  "+encoding+" "+dims,streamUri,token));
            }
        }
        return !streams.isEmpty()||profileToken!=null;
    }
    boolean supportsPtz(){return ptzAvailable&&profileToken!=null;}
    boolean supportsNight(){return imagingAvailable&&videoSourceToken!=null;}
    private static boolean allowedStream(String uri){
        try{String s=Uri.parse(uri).getScheme();return "rtsp".equalsIgnoreCase(s)||"rtsps".equalsIgnoreCase(s)||"http".equalsIgnoreCase(s)||"https".equalsIgnoreCase(s);}catch(Exception e){return false;}
    }
    void move(double x,double y,double zoom) throws Exception {
        if(!supportsPtz())throw new Exception("ONVIF PTZ puudub");
        String velocity="<tptz:Velocity><tt:PanTilt x=\""+x+"\" y=\""+y+"\"/><tt:Zoom x=\""+zoom+"\"/></tptz:Velocity>";
        String b="<tptz:ContinuousMove><tptz:ProfileToken>"+esc(profileToken)+"</tptz:ProfileToken>"+velocity+
                "<tptz:Timeout>PT1S</tptz:Timeout></tptz:ContinuousMove>";
        call(ptzUrl,TPTZ,"ContinuousMove",b);
    }
    void stop() throws Exception{
        if(!supportsPtz())return;
        call(ptzUrl,TPTZ,"Stop","<tptz:Stop><tptz:ProfileToken>"+esc(profileToken)+
             "</tptz:ProfileToken><tptz:PanTilt>true</tptz:PanTilt><tptz:Zoom>true</tptz:Zoom></tptz:Stop>");
    }
    void home() throws Exception{
        if(!supportsPtz())throw new Exception("PTZ puudub");
        call(ptzUrl,TPTZ,"GotoHomePosition","<tptz:GotoHomePosition><tptz:ProfileToken>"+esc(profileToken)+"</tptz:ProfileToken></tptz:GotoHomePosition>");
    }
    void night(String mode) throws Exception{
        if(!supportsNight())throw new Exception("ONVIF Imaging puudub");
        if(!"AUTO".equals(mode)&&!"ON".equals(mode)&&!"OFF".equals(mode))throw new Exception("Tundmatu IR režiim");
        String b="<timg:SetImagingSettings><timg:VideoSourceToken>"+esc(videoSourceToken)+
          "</timg:VideoSourceToken><timg:ImagingSettings><tt:IrCutFilter>"+mode+
          "</tt:IrCutFilter></timg:ImagingSettings><timg:ForcePersistence>true</timg:ForcePersistence></timg:SetImagingSettings>";
        call(imagingUrl,TIMG,"SetImagingSettings",b);
    }
    List<RemoteRecording> recordings() throws Exception{
        List<RemoteRecording> list=new ArrayList<>();
        if(!recordingAvailable)return list;
        Document d=call(recordingUrl,TRC,"GetRecordings","<trc:GetRecordings/>");
        for(Element rec:all(d.getDocumentElement(),"RecordingItem")){
            String token=value(rec,"RecordingToken");
            if(token==null||token.isEmpty())continue;
            String name=value(rec,"SourceId");
            if(name==null||name.isEmpty())name="Salvestus "+(list.size()+1);
            try{
                String req="<trp:GetReplayUri><trp:StreamSetup><tt:Stream>RTP-Unicast</tt:Stream><tt:Transport><tt:Protocol>RTSP</tt:Protocol></tt:Transport></trp:StreamSetup><trp:RecordingToken>"+
                    esc(token)+"</trp:RecordingToken></trp:GetReplayUri>";
                String uri=value(call(replayUrl,TRP,"GetReplayUri",req).getDocumentElement(),"Uri");
                if(uri!=null&&allowedStream(uri))list.add(new RemoteRecording(name,uri));
            }catch(Exception ignored){}
        }
        return list;
    }
    private String checkedServiceUrl(String url){
        try{
            URL u=new URL(url);
            if(!"http".equalsIgnoreCase(u.getProtocol())&&!"https".equalsIgnoreCase(u.getProtocol()))return null;
            String host=u.getHost();
            if("0.0.0.0".equals(host)||"127.0.0.1".equals(host)||"localhost".equalsIgnoreCase(host))
                return new URL(u.getProtocol(),ip,u.getPort(),u.getFile()).toExternalForm();
            // Reject off-camera endpoints to avoid sending camera credentials to another host.
            if(!host.equalsIgnoreCase(ip))return null;
            return url;
        }catch(Exception ex){return null;}
    }
    private String service(Element capabilities,String kind){
        Element e=first(capabilities,kind);
        String url=e==null?null:value(e,"XAddr");
        if(url==null||url.isEmpty())return null;
        try{
            URL test=new URL(url);
            String host=test.getHost();
            // Prevent a malicious ONVIF response from redirecting camera login credentials.
            if("0.0.0.0".equals(host)||"127.0.0.1".equals(host)||"localhost".equalsIgnoreCase(host))
                url=new URL(test.getProtocol(),ip,test.getPort(),test.getFile()).toExternalForm();
            else if(!host.equalsIgnoreCase(ip)&&!InetAddress.getByName(host).equals(InetAddress.getByName(ip)))return null;
            if(!"http".equalsIgnoreCase(test.getProtocol())&&!"https".equalsIgnoreCase(test.getProtocol()))return null;
            return url;
        }catch(Exception ex){return null;}
    }
    private Document call(String endpoint,String namespace,String action,String body) throws Exception{
        String soap="<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tds=\""+TDS+"\" xmlns:trt=\""+TRT+
                "\" xmlns:tt=\"http://www.onvif.org/ver10/schema\" xmlns:tptz=\""+TPTZ+"\" xmlns:timg=\""+TIMG+
                "\" xmlns:trc=\""+TRC+"\" xmlns:trp=\""+TRP+"\"><s:Header>"+security()+"</s:Header><s:Body>"+body+"</s:Body></s:Envelope>";
        byte[] request=soap.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection conn=null;
        try {
            conn=(HttpURLConnection)new URL(endpoint).openConnection();
            boolean quick="ContinuousMove".equals(action)||"Stop".equals(action);
            conn.setConnectTimeout(quick?900:1800);conn.setReadTimeout(quick?1400:3500);conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type","application/soap+xml; charset=utf-8; action=\""+namespace+"/"+action+"\"");
            conn.setRequestProperty("SOAPAction", "\""+namespace+"/"+action+"\"");
            if(!user.isEmpty())conn.setRequestProperty("Authorization","Basic "+
                    Base64.encodeToString((user+":"+pass).getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP));
            write(conn,request);
            int status=conn.getResponseCode();
            if(status==401 && !user.isEmpty()){
                String header=conn.getHeaderField("WWW-Authenticate");
                if(header!=null&&header.toLowerCase(Locale.US).startsWith("digest")){
                    String auth=digestHeader(header,"POST",new URL(endpoint).getFile());
                    conn.disconnect();
                    conn=(HttpURLConnection)new URL(endpoint).openConnection();
                    conn.setConnectTimeout(quick?900:1800);conn.setReadTimeout(quick?1400:3500);conn.setDoOutput(true);
                    conn.setRequestMethod("POST");conn.setRequestProperty("Content-Type","application/soap+xml; charset=utf-8");
                    conn.setRequestProperty("Authorization",auth);write(conn,request);
                    status=conn.getResponseCode();
                }
            }
            InputStream in=status<400?conn.getInputStream():conn.getErrorStream();
            if(status==401)throw new Exception("ONVIF HTTP 401 - kaamera kasutajanimi/parool on vajalik");
            if(status==404)throw new Exception("ONVIF HTTP 404 - teenus sellel pordil puudub");
            if(in==null)throw new Exception("ONVIF HTTP "+status);
            try(InputStream stream=in){
                Document doc=parse(stream);
                Element fault=first(doc.getDocumentElement(),"Fault");
                if(fault!=null)throw new Exception("ONVIF "+action+": "+text(fault));
                if(status>=400)throw new Exception("ONVIF HTTP "+status);
                return doc;
            }
        }finally{if(conn!=null)conn.disconnect();}
    }
    private static void write(HttpURLConnection conn,byte[] request)throws Exception{
        try(OutputStream out=conn.getOutputStream()){out.write(request);}
    }
    private String security()throws Exception{
        if(user.isEmpty())return "";
        byte[] nonce=new byte[20];new SecureRandom().nextBytes(nonce);
        SimpleDateFormat iso=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));String created=iso.format(new Date());
        MessageDigest sha=MessageDigest.getInstance("SHA-1");
        sha.update(nonce);sha.update(created.getBytes(StandardCharsets.UTF_8));sha.update(pass.getBytes(StandardCharsets.UTF_8));
        String digest=Base64.encodeToString(sha.digest(),Base64.NO_WRAP);
        return "<wsse:Security s:mustUnderstand=\"1\" xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" "+
            "xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">"+
            "<wsse:UsernameToken><wsse:Username>"+esc(user)+"</wsse:Username>"+
            "<wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">"+digest+"</wsse:Password>"+
            "<wsse:Nonce EncodingType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary\">"+Base64.encodeToString(nonce,Base64.NO_WRAP)+"</wsse:Nonce>"+
            "<wsu:Created>"+created+"</wsu:Created></wsse:UsernameToken></wsse:Security>";
    }
    private String digestHeader(String challenge,String method,String path)throws Exception{
        String realm=parameter(challenge,"realm"),nonce=parameter(challenge,"nonce"),qop=parameter(challenge,"qop");
        String opaque=parameter(challenge,"opaque");if(qop!=null&&qop.contains("auth"))qop="auth";else qop=null;
        if(realm==null||nonce==null)throw new Exception("Puudulik Digest autentimine");
        String cnonce=Long.toHexString(new SecureRandom().nextLong());
        String ha1=md5(user+":"+realm+":"+pass),ha2=md5(method+":"+path);
        String response=qop==null?md5(ha1+":"+nonce+":"+ha2):md5(ha1+":"+nonce+":00000001:"+cnonce+":"+qop+":"+ha2);
        String h="Digest username=\""+user+"\", realm=\""+realm+"\", nonce=\""+nonce+
                "\", uri=\""+path+"\", response=\""+response+"\"";
        if(qop!=null)h+=", qop="+qop+", nc=00000001, cnonce=\""+cnonce+"\"";
        if(opaque!=null)h+=", opaque=\""+opaque+"\"";
        return h;
    }
    private static String parameter(String header,String key){
        Matcher m=Pattern.compile("(?:^|[,\\s])"+key+"\\s*=\\s*(?:\"([^\"]*)\"|([^,\\s]+))",Pattern.CASE_INSENSITIVE).matcher(header);
        return m.find()?(m.group(1)!=null?m.group(1):m.group(2)):null;
    }
    private static String md5(String s)throws Exception {
        byte[] bytes=MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.US,"%02x",b&255));return out.toString();
    }
    /**
     * Android DOM's disallow-doctype-decl feature is unsupported.
     * Safely tokenize XML with Android XmlPullParser (DTD and entities forbidden)
     * and build a DOM for the existing ONVIF queries.
     */
    private static Document parse(InputStream data)throws Exception{
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document=factory.newDocumentBuilder().newDocument();
        XmlPullParser reader=Xml.newPullParser();
        reader.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);
        reader.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false);
        reader.setInput(data,"UTF-8");
        Node current=document;
        int count=0;
        for(int event=reader.getEventType();event!=XmlPullParser.END_DOCUMENT;event=reader.nextToken()){
            if(++count>120000)throw new Exception("ONVIF XML liiga mahukas");
            if(event==XmlPullParser.DOCDECL||event==XmlPullParser.ENTITY_REF)
                throw new Exception("ONVIF: DOCTYPE ja välised XML entity'd pole lubatud");
            if(event==XmlPullParser.START_TAG){
                String ns=reader.getNamespace(),prefix=reader.getPrefix();
                String q=(prefix==null||prefix.isEmpty()?"":prefix+":")+reader.getName();
                Element element=document.createElementNS(ns==null||ns.isEmpty()?null:ns,q);
                for(int i=0;i<reader.getAttributeCount();i++){
                    String attrNs=reader.getAttributeNamespace(i),attrPrefix=reader.getAttributePrefix(i);
                    String attr=(attrPrefix==null||attrPrefix.isEmpty()?"":attrPrefix+":")+reader.getAttributeName(i);
                    if(attrNs==null||attrNs.isEmpty())element.setAttribute(attr,reader.getAttributeValue(i));
                    else element.setAttributeNS(attrNs,attr,reader.getAttributeValue(i));
                }
                current.appendChild(element);current=element;
            }else if(event==XmlPullParser.END_TAG){
                if(current.getParentNode()!=null)current=current.getParentNode();
            }else if(event==XmlPullParser.TEXT||event==XmlPullParser.CDSECT){
                if(current!=document)current.appendChild(document.createTextNode(reader.getText()));
            }
        }
        if(document.getDocumentElement()==null)throw new Exception("ONVIF vastuses puudub XML");
        return document;
    }
    static Element first(Element root,String local){
        if(root==null)return null;
        if(local.equals(root.getLocalName()))return root;
        NodeList l=root.getElementsByTagNameNS("*",local);
        return l.getLength()==0?null:(Element)l.item(0);
    }
    static List<Element> all(Element root,String local){
        List<Element> out=new ArrayList<>();if(root==null)return out;
        NodeList nodes=root.getElementsByTagNameNS("*",local);
        for(int i=0;i<nodes.getLength();i++)if(nodes.item(i) instanceof Element)out.add((Element)nodes.item(i));
        return out;
    }
    static String value(Element root,String local){
        Element e=first(root,local);return e==null?null:e.getTextContent().trim();
    }
    static String text(Element el){return el==null?"":el.getTextContent().trim();}
    static String esc(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
}
