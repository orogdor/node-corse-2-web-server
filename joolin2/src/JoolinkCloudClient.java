package com.jooan.qiaoanzhilian.ui.activity.play;

import android.os.Build;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Experimental interoperable client for endpoints identified in the
 * user's own Joolink APK. No SDK secrets, bypasses, or third-party services.
 * Only HTTPS to the official production domain is ever permitted.
 */
final class JoolinkCloudClient {
    static final String ORIGIN="https://qacloudapi.jooancloud.com";
    static final String PACKAGE="com.jooan.qiaoanzhilian.gp";
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final int MAX_RESPONSE=1024*1024;
    private static final int CONNECT_TIMEOUT=6000, READ_TIMEOUT=10000;

    private JoolinkCloudClient(){}
    static final class Session {
        final String userId,token;
        final String accountLabel;
        Session(String userId,String token,String accountLabel){
            this.userId=userId;this.token=token;this.accountLabel=accountLabel;
        }
    }
    static final class AuthException extends Exception {
        AuthException(String message){super(message);}
    }
    private static String requestId(){
        byte[] b=new byte[12];RANDOM.nextBytes(b);
        StringBuilder s=new StringBuilder();
        for(byte value:b)s.append(String.format(Locale.US,"%02x",value&255));
        return s.toString();
    }
    private static JSONObject header(String user,String token) throws Exception {
        JSONObject h=new JSONObject();
        h.put("seqno",requestId());
        h.put("package_name",PACKAGE);
        h.put("client_version","1.0");
        h.put("phone_model",Build.MODEL);
        h.put("language",Locale.getDefault().getLanguage());
        if(user!=null&&!user.isEmpty())h.put("user_id",user);
        if(token!=null&&!token.isEmpty())h.put("token",token);
        return h;
    }
    static Session login(String account,String password) throws Exception {
        if(account==null||account.trim().isEmpty()||password==null||password.isEmpty()){
            throw new AuthException("Sisesta Joolinki kasutajakonto ja parool.");
        }
        String name=account.trim();
        boolean email=name.contains("@");
        JSONObject body=new JSONObject();
        body.put("header",header(null,null));
        body.put("login_mode",email?"1":"0");
        body.put("email",email?name:"");
        body.put("phone_no",email?"":name);
        body.put("password",password);
        body.put("app_version","1.0");
        body.put("phone_model",Build.MODEL);
        body.put("system_version",Build.VERSION.RELEASE);
        body.put("client_id","android-"+requestId());
        body.put("package_name",PACKAGE);
        JSONObject response=post("/v3/am/login",body);
        JSONObject result=response.optJSONObject("body");
        if(result==null)result=response.optJSONObject("data");
        String error=response.optString("error_code",response.optString("code",""));
        if(result==null)throw new AuthException("Server ei tagastanud sisselogimisseanssi (kood "+safeError(error)+"). "+safeError(response.optString("error_msg","Kontrolli konto tüüpi.")));
        String userId=result.optString("user_id",result.optString("userId",""));
        String token=result.optString("token",result.optString("user_token",""));
        if(token.isEmpty()||userId.isEmpty()){
            throw new AuthException("Server vastas, kuid ei andnud ühilduvat tokenit. "+
                "Kood: "+safeError(error)+". Teade: "+safeError(response.optString("error_msg",response.optString("message","Puuduvad seansiandmed"))));
        }
        return new Session(userId,token,name);
    }
    /** GetDeviceListResponse in the vendor's APK extends NewBaseHeader:
     * the device_info_list field is ROOT level, not under body/data.
     */
    static JSONArray devices(Session session) throws Exception{
        JSONObject body=new JSONObject();
        body.put("header",header(session.userId,session.token));
        body.put("force_query_bind","Y");
        body.put("app_version","1.0");
        body.put("phone_model",Build.MODEL);
        body.put("package_name",PACKAGE);
        JSONObject response=post("/v3/am/query_device_list",body);
        String code=response.optString("error_code","");
        if(!code.isEmpty()&&!isSuccess(code)){
            throw new AuthException("Kaamerate päring ebaõnnestus (kood "+safeError(code)+
                    "): "+safeError(response.optString("error_msg","tundmatu viga")));
        }
        JSONArray list=findDevices(response,0);
        if(list!=null)return list;
        throw new AuthException("Server vastas "+safeError(code)+
            ", kuid tuntud kaameraloendi välja ei olnud. Vastuse struktuur: "+
            safeStructure(response));
    }
    private static boolean isSuccess(String code){
        return "success".equalsIgnoreCase(code)||"0".equals(code)||"200".equals(code)||
               "ok".equalsIgnoreCase(code);
    }
    private static JSONArray findDevices(JSONObject json,int depth){
        if(json==null||depth>3)return null;
        // Real Joolink GetDeviceListResponse: response.device_info_list.
        for(String field:new String[]{"device_info_list","device_list","deviceList","devices"}){
            JSONArray array=json.optJSONArray(field);
            if(array!=null)return array;
        }
        for(String wrap:new String[]{"body","body_info","data","result","payload"}){
            JSONObject inner=json.optJSONObject(wrap);
            if(inner!=null){
                JSONArray array=findDevices(inner,depth+1);
                if(array!=null)return array;
            }
        }
        return null;
    }
    /** Never include JSON values: responses could contain tokens, device IDs or account info. */
    private static String safeStructure(JSONObject json){
        java.util.ArrayList<String> fields=new java.util.ArrayList<>();
        java.util.Iterator<String> keys=json.keys();
        int count=0;
        while(keys.hasNext()&&count++<18){
            String k=keys.next();
            String normalized=k.toLowerCase(Locale.ROOT);
            // Do not surface unknown dynamic key material; only approved field names.
            if(normalized.matches("[a-z_]{2,28}"))
                fields.add(k);
        }
        java.util.Collections.sort(fields);
        return fields.toString();
    }
    static JSONObject post(String path,JSONObject body) throws Exception{
        if(!path.startsWith("/v3/am/"))throw new IllegalArgumentException("Unexpected cloud API path");
        URL target=new URL(ORIGIN+path);
        HttpURLConnection conn=(HttpURLConnection)target.openConnection();
        try{
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(CONNECT_TIMEOUT);conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
            conn.setRequestProperty("Accept","application/json");
            conn.setRequestProperty("Cache-Control","no-store");
            byte[] encoded=body.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(encoded.length);
            try(OutputStream out=conn.getOutputStream()){out.write(encoded);}
            int status=conn.getResponseCode();
            if(status>=300&&status<400)throw new AuthException("Server suunab mujale (HTTP "+status+"); turvalisuse huvides ümbersuunamist ei järgita.");
            InputStream raw=status>=400?conn.getErrorStream():conn.getInputStream();
            if(raw==null)throw new AuthException("Joolinki server ei tagastanud andmeid: HTTP "+status);
            byte[] bytes;
            try(InputStream in=raw;ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] block=new byte[4096];int n;
                while((n=in.read(block))!=-1){if(out.size()+n>MAX_RESPONSE)throw new AuthException("Serveri vastus on liiga suur");out.write(block,0,n);}
                bytes=out.toByteArray();
            }
            String text=new String(bytes,StandardCharsets.UTF_8);
            if(status>=400)throw new AuthException("Joolinki server vastas HTTP "+status+". "+
                 "Autentimisprotokoll võib vajada tootja lisavälju.");
            try{return new JSONObject(text);}
            catch(Exception error){throw new AuthException("Serveri vastus ei olnud JSON; võimalik, et tootja kasutab teist protokolli.");}
        }finally{conn.disconnect();}
    }
    private static String safeError(String string){
        if(string==null)return "";
        // Suppress long / structured server responses that might contain tokens.
        String s=string.replaceAll("[\\r\\n\\t]"," ").replaceAll("(?i)(token|password|authorization)[=:]\\S+","[peidetud]");
        return s.substring(0,Math.min(100,s.length()));
    }
}
