package ee.joolinkviewer;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Android Keystore AES-256-GCM encrypted state. No account password is stored.
 * Token, account ID and cached cloud devices are encrypted before SharedPreferences.
 */
final class SecureCloudStore {
    private static final String STORE="joolink_cloud_secure", KEY="v1", ALIAS="joolinkviewer.cloud.v1";
    private SecureCloudStore(){}
    private static SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(ALIAS)){
            KeyStore.SecretKeyEntry entry=(KeyStore.SecretKeyEntry)ks.getEntry(ALIAS,null);
            return entry.getSecretKey();
        }
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build());
        return generator.generateKey();
    }
    private static JSONObject state(Context ctx){
        try{
            String encoded=ctx.getSharedPreferences(STORE,Context.MODE_PRIVATE).getString(KEY,null);
            if(encoded==null)return new JSONObject();
            byte[] data=Base64.decode(encoded,Base64.NO_WRAP);
            if(data.length<28)return new JSONObject();
            byte[] nonce=new byte[12];
            System.arraycopy(data,0,nonce,0,12);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,nonce));
            byte[] clear=cipher.doFinal(data,12,data.length-12);
            return new JSONObject(new String(clear,StandardCharsets.UTF_8));
        }catch(Exception ignored){return new JSONObject();}
    }
    private static void persist(Context ctx,JSONObject state) throws Exception{
        // Android Keystore requires the provider to generate a fresh random IV.
        // Supplying our own IV causes "Caller-provided IV not permitted".
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] nonce=cipher.getIV();
        if(nonce==null||nonce.length!=12)throw new Exception("Unexpected Keystore GCM IV");
        byte[] ciphertext=cipher.doFinal(state.toString().getBytes(StandardCharsets.UTF_8));
        byte[] joined=new byte[nonce.length+ciphertext.length];
        System.arraycopy(nonce,0,joined,0,nonce.length);
        System.arraycopy(ciphertext,0,joined,nonce.length,ciphertext.length);
        String encoded=Base64.encodeToString(joined,Base64.NO_WRAP);
        if(!ctx.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit().putString(KEY,encoded).commit())
            throw new Exception("Turvaline salvestus ebaõnnestus");
    }
    static synchronized JoolinkCloudClient.Session session(Context ctx){
        JSONObject data=state(ctx);
        String token=data.optString("token",""),id=data.optString("user_id","");
        if(token.isEmpty()||id.isEmpty())return null;
        return new JoolinkCloudClient.Session(id,token,data.optString("account",""));
    }
    static synchronized String account(Context ctx){return state(ctx).optString("account","");}
    static synchronized JSONArray cameras(Context ctx){
        JSONArray arr=state(ctx).optJSONArray("cameras");
        return arr==null?new JSONArray():arr;
    }
    static synchronized long updatedAt(Context ctx){return state(ctx).optLong("updated_at",0L);}
    static synchronized void saveSession(Context ctx,JoolinkCloudClient.Session session) throws Exception {
        JSONObject d=state(ctx);
        // If another account signs in, do not leak its devices and RTSP bindings.
        String previous=d.optString("user_id","");
        if(!previous.equals(session.userId)){
            d=new JSONObject();
            d.put("cameras",new JSONArray());
            d.put("routes",new JSONObject());
        }
        d.put("user_id",session.userId);
        d.put("token",session.token);
        d.put("account",session.accountLabel);
        persist(ctx,d);
    }
    static synchronized void saveCameras(Context ctx,JSONArray arr) throws Exception{
        JSONObject d=state(ctx);
        if(d.optString("token","").isEmpty())return;
        d.put("cameras",arr);
        d.put("updated_at",System.currentTimeMillis());
        persist(ctx,d);
    }
    static String cameraName(JSONObject camera,int index){
        JSONObject j=camera.optJSONObject("jooan_info");
        JSONObject a=camera.optJSONObject("alilv_info");
        String name=j==null?"":j.optString("nick_name","");
        if(name.isEmpty()&&a!=null)name=a.optString("nickName","");
        if(name.isEmpty())name=camera.optString("device_name","");
        return name.isEmpty()?"Kaamera "+(index+1):name;
    }
    static String cameraModel(JSONObject camera){
        JSONObject j=camera.optJSONObject("jooan_info"),a=camera.optJSONObject("alilv_info");
        String model=j==null?"":j.optString("device_model","");
        if(model.isEmpty()&&a!=null)model=a.optString("productModel","");
        return model;
    }
    static String cameraStatus(JSONObject camera){
        JSONObject j=camera.optJSONObject("jooan_info"),a=camera.optJSONObject("alilv_info");
        String status=j==null?"":j.optString("is_online","");
        if(status.isEmpty()&&a!=null)status=a.optString("status","");
        if("Y".equalsIgnoreCase(status)||"1".equals(status)||"online".equalsIgnoreCase(status))return "Online";
        if("N".equalsIgnoreCase(status)||"0".equals(status)||"offline".equalsIgnoreCase(status))return "Offline";
        return "Olek teadmata";
    }
    static String cameraKey(JSONObject camera,int index){
        JSONObject j=camera.optJSONObject("jooan_info"),a=camera.optJSONObject("alilv_info");
        for(JSONObject obj:new JSONObject[]{j,a,camera}){
            if(obj==null)continue;
            for(String attr:new String[]{"device_id","deviceId","dev_id","device_uid","uid","iot_id","iotId","device_sn"}){
                String id=obj.optString(attr,"");
                if(!id.isEmpty()&&id.length()<256)return attr+":"+id;
            }
        }
        return "camera:"+cameraModel(camera)+":"+cameraName(camera,index).toLowerCase(Locale.ROOT);
    }
    static synchronized JSONObject binding(Context ctx,String deviceKey){
        JSONObject d=state(ctx);JSONObject routes=d.optJSONObject("routes");
        if(routes==null)return null;
        return routes.optJSONObject(deviceKey);
    }
    static synchronized void saveBinding(Context ctx,String deviceKey,String url,String user,int port)throws Exception{
        if(!RtspNetwork.validUrl(url))throw new Exception("Vigane videovoo aadress");
        JSONObject d=state(ctx),routes=d.optJSONObject("routes");
        if(routes==null)routes=new JSONObject();
        JSONObject route=new JSONObject();
        route.put("url",RtspNetwork.stripCredentials(url));
        route.put("user",user);
        route.put("onvif_port",port);
        routes.put(deviceKey,route);
        d.put("routes",routes);
        persist(ctx,d);
    }
    static synchronized void removeBinding(Context ctx,String deviceKey)throws Exception{
        JSONObject d=state(ctx),routes=d.optJSONObject("routes");
        if(routes!=null){routes.remove(deviceKey);d.put("routes",routes);persist(ctx,d);}
    }
    static synchronized void clear(Context ctx){
        ctx.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit().remove(KEY).commit();
    }
}
