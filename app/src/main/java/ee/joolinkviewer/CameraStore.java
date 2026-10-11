package ee.joolinkviewer;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Stores only camera name, RTSP address and optional username. Never persists passwords. */
final class CameraStore {
    static class Camera {
        String name, url, username;
        Camera(String name, String url, String username) {
            this.name = name; this.url = url; this.username = username;
        }
    }
    static List<Camera> load(Context context) {
        List<Camera> list = new ArrayList<>();
        String json = context.getSharedPreferences("cameras", Context.MODE_PRIVATE)
                .getString("items", "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                list.add(new Camera(item.optString("name"), item.optString("url"), item.optString("username")));
            }
        } catch (Exception ignored) {}
        return list;
    }
    static void save(Context context, List<Camera> cams) {
        JSONArray arr = new JSONArray();
        for (Camera cam : cams) {
            JSONObject item = new JSONObject();
            try {
                item.put("name", cam.name);
                item.put("url", RtspNetwork.stripCredentials(cam.url));
                item.put("username", cam.username);
                arr.put(item);
            } catch (Exception ignored) {}
        }
        context.getSharedPreferences("cameras", Context.MODE_PRIVATE)
                .edit().putString("items", arr.toString()).apply();
    }
}
