package ee.joolinkviewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/** Authorized remote-video fallback: invokes the official Joolink app.
 *
 * Android isolates private camera player activities in the original app. The
 * original APK exports its launcher, but does NOT export its camera player.
 * Do not attempt explicit intents into non-exported activities or to read its
 * session tokens. The official Joolink application owns and renders the video.
 */
final class OfficialJoolinkBridge {
    static final String PACKAGE = "com.jooan.qiaoanzhilian.gp";
    private OfficialJoolinkBridge() {}

    static void open(Activity activity, String cameraName) {
        Intent launch=activity.getPackageManager().getLaunchIntentForPackage(PACKAGE);
        if(launch!=null) {
            try {
                // Camera selection cannot be controlled: original player
                // Activity is private. We must open its public launcher.
                launch.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(launch);
                Toast.makeText(activity,
                    "Vali Joolinki äpis kaamera: "+cameraName,
                    Toast.LENGTH_LONG).show();
                return;
            } catch(ActivityNotFoundException | SecurityException ignored) {
                // An installed package is not necessarily launchable.
            }
        }
        new AlertDialog.Builder(activity)
            .setTitle("Ametlik Joolinki rakendus on vajalik")
            .setMessage("Joolinki kaugvideo kasutab tootja litsentsitud Kalay / LinkVisual "+
                "videoteeki. HomeJoolink ei saa originaaläpi privaatset videovaadet "+
                "enda sisse tõsta. Paigalda ametlik Joolink ja logi seal sisse. "+
                "Seejärel saad selle nupuga avada päris kaugvaate.")
            .setNegativeButton("Tagasi",null)
            .setPositiveButton("Ava rakenduse leht",(dialog,which)->{
                Intent store=new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id="+PACKAGE));
                try{activity.startActivity(store);}
                catch(ActivityNotFoundException e){
                    Toast.makeText(activity,
                        "Rakenduse lehte ei saanud avada.",Toast.LENGTH_LONG).show();
                }
            }).show();
    }
}
