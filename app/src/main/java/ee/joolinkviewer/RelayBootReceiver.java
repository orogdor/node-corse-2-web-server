package ee.joolinkviewer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/** Optional user-controlled startup after reboot. Device firmware may prevent automatic launch. */
public final class RelayBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))return;
        SharedPreferences pref=context.getSharedPreferences(RelayService.PREFS,Context.MODE_PRIVATE);
        if(!pref.getBoolean("autostart",false))return;
        String url=pref.getString("url","");
        if(url.isEmpty())return;
        Intent service=new Intent(context,RelayService.class);
        service.setAction(RelayService.START);service.putExtra("url",url);
        try{
            if(Build.VERSION.SDK_INT>=26)context.startForegroundService(service);
            else context.startService(service);
        }catch(Exception failure){
            pref.edit().putString("last_error","Boot: "+failure.getMessage()).apply();
        }
    }
}
