package ee.joolinkviewer;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Stores only camera name, sanitized address, username and ONVIF port. No passwords. */
final class CameraStore {
    static class Camera {
        String name,url,username;
        int onvifPort;
        Camera(String name,String url,String username,int port) {
            this.name=name;this.url=url;this.username=username;this.onvifPort=port;
        }
    }
    static List<Camera> load(Context c){
        List<Camera> list=new ArrayList<>();
        String json=c.getSharedPreferences("cameras",Context.MODE_PRIVATE).getString("items","[]");
        try{
            JSONArray a=new JSONArray(json);
            for(int i=0;i<a.length();i++){
                JSONObject x=a.getJSONObject(i);
                list.add(new Camera(x.optString("name"),x.optString("url"),x.optString("username"),x.optInt("onvifPort",80)));
            }
        }catch(Exception ignored){}
        return list;
    }
    static void save(Context c,List<Camera> cameras){
        JSONArray arr=new JSONArray();
        for(Camera item:cameras){
            JSONObject x=new JSONObject();
            try{
                x.put("name",item.name);
                x.put("url",RtspNetwork.stripCredentials(item.url));
                x.put("username",item.username);
                x.put("onvifPort",item.onvifPort);
                arr.put(x);
            }catch(Exception ignored){}
        }
        c.getSharedPreferences("cameras",Context.MODE_PRIVATE).edit().putString("items",arr.toString()).apply();
    }
}
