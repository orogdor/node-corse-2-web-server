package ee.joolinkviewer;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Offline, read-only inspection of the user's own installed Joolink APK.
 * Never reads app data, passwords, auth tokens or Android account files;
 * never sends a request to any discovered host. Only sanitized hostnames
 * and endpoint PATHS are included in the optional shareable report.
 */
public class JoolinkApiInspector extends Activity {
    private static final int PICK_APK=7194;
    private static final String[] PACKAGES={
      "com.jooan.qiaoanzhilian.gp","com.jooan.qiaoanzhilian"
    };
    private static final Pattern URLS=Pattern.compile(
      "(?i)https?://[a-z0-9][a-z0-9._:-]{3,180}(?:/[a-z0-9_./%+@~-]{0,100})?");
    private static final Pattern HOSTS=Pattern.compile(
      "(?i)(?:[a-z0-9-]+\\.){1,5}(?:com|cn|net|org|io|cloud|top|tech|app|tv|co|cc|biz)");
    private static final Pattern ENDPOINTS=Pattern.compile(
      "(?i)/(?:api/)?(?:v[0-9]+/)?(?:user|account|auth|login|signin|token|device|camera|cloud|p2p|stream|service)(?:/[a-z0-9_./-]{0,70})?");
    private final Set<String> hosts=new TreeSet<>();
    private final Set<String> routes=new TreeSet<>();
    private TextView info,results;
    private Button copy;
    private String report="";
    private volatile boolean working=false;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=Ui.vertical(this);
        root.setPadding(Ui.dp(this,18),Ui.dp(this,30),Ui.dp(this,18),Ui.dp(this,36));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int top=android.os.Build.VERSION.SDK_INT>=30?
                 insets.getInsets(android.view.WindowInsets.Type.statusBars()).top:insets.getSystemWindowInsetTop();
            int bottom=android.os.Build.VERSION.SDK_INT>=30?
                 insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom:insets.getSystemWindowInsetBottom();
            v.setPadding(Ui.dp(this,18),Ui.dp(this,25)+top,Ui.dp(this,18),Ui.dp(this,36)+bottom);
            return insets;
        });
        scroll.addView(root);
        setContentView(scroll);
        root.requestApplyInsets();
        Ui.add(root,Ui.text(this,"Joolinki pilveühenduse uuring",22,true),this,4);
        TextView hint=Ui.text(this,
          "See test otsib sinu telefonis asuvast Joolinki APK-failist serverite nimesid ja API radade viiteid. Ei kasuta ega kogu paroole. Kõik toimub telefonis, midagi ei saadeta võrku. See EI loo veel Joolinki kontosse sisselogimist.",13,false);
        hint.setTextColor(Ui.MUTED);Ui.add(root,hint,this,8);
        Button installed=Ui.button(this,"Uuri paigaldatud Joolinki rakendust");
        installed.setOnClickListener(v->scanInstalled());
        Ui.add(root,installed,this,17);
        Button manual=Ui.secondary(this,"Vali Joolinki APK fail telefonist");
        manual.setOnClickListener(v->chooseApk());
        Ui.add(root,manual,this,8);
        info=Ui.text(this,"APK-faili pole veel kontrollitud.",13,false);
        info.setTextColor(Ui.MUTED);Ui.add(root,info,this,13);
        results=Ui.text(this,"",12,false);
        results.setTextIsSelectable(true);
        Ui.add(root,results,this,8);
        copy=Ui.secondary(this,"Kopeeri puhastatud diagnostikaraport");
        copy.setEnabled(false);
        copy.setOnClickListener(v->copyReport());
        Ui.add(root,copy,this,12);
        Button share=Ui.secondary(this,"Jaga diagnostikaraportit");
        share.setOnClickListener(v->{
            if(report.isEmpty())return;
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT,report);
            startActivity(Intent.createChooser(send,"Jaga Joolinki diagnostikat"));
        });
        Ui.add(root,share,this,8);
        TextView foot=Ui.text(this,
          "Kui aruandes on serverid ja login/API viited, saada ainult see puhastatud aruanne siia vestlusse. Päris sisselogimise tegemiseks peame veel kinnitama päringu vormingu ja turvalise serveriaadressi.",12,false);
        foot.setTextColor(Ui.MUTED);Ui.add(root,foot,this,17);
    }
    private void scanInstalled(){
        if(working)return;
        String filename=null,packageName=null;
        for(String app:PACKAGES){
            try{
                ApplicationInfo ai=getPackageManager().getApplicationInfo(app,0);
                if(ai.sourceDir!=null){filename=ai.sourceDir;packageName=app;break;}
            }catch(Exception ignored){}
        }
        if(filename==null){
            info.setText("Joolinki rakendust ei leitud või Android ei võimalda APK-le ligi pääseda. Kasuta APK faili valimist.");
            return;
        }
        final String found=filename;
        runScan(()->new FileInputStream(found),"Paigaldatud "+packageName);
    }
    private void chooseApk(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addCategory(Intent.CATEGORY_DEFAULT);
        try{startActivityForResult(i,PICK_APK);}
        catch(Exception e){info.setText("Failivalija viga: "+e.getMessage());}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_APK||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        runScan(()->getContentResolver().openInputStream(uri),"Valitud APK");
    }
    private interface StreamOpener{InputStream open() throws Exception;}
    private void runScan(StreamOpener file,String source){
        if(working)return;
        working=true;hosts.clear();routes.clear();report="";
        copy.setEnabled(false);results.setText("");
        info.setText("Uurin APK sisu kohapeal …");
        new Thread(()->{
            try{
                String result=inspect(file,source);
                runOnUiThread(()->{
                    report=result;results.setText(result);
                    info.setText("Valmis. Võid raporti kopeerida (ilma paroolideta).");
                    copy.setEnabled(true);working=false;
                });
            }catch(Exception ex){
                runOnUiThread(()->{
                    info.setText("APK uurimine ebaõnnestus: "+ex.getClass().getSimpleName()+
                      ". Proovi eksportida Joolinki base.apk ja vali see käsitsi.");
                    working=false;
                });
            }
        },"joolink-apk-offline-inspector").start();
    }
    private String inspect(StreamOpener input,String source)throws Exception{
        int files=0;
        byte[] buffer=new byte[65536];
        try(InputStream in=new BufferedInputStream(input.open());
            ZipInputStream zip=new ZipInputStream(in)){
            ZipEntry e;
            while((e=zip.getNextEntry())!=null){
                String name=e.getName().toLowerCase(Locale.ROOT);
                // Only app code, native libs and small textual configs; skip video/assets.
                boolean inspect=name.matches("classes[0-9]*\\.dex")||
                    name.matches("lib/.+\\.so")||
                    (name.startsWith("assets/")&&(name.endsWith(".json")||
                       name.endsWith(".xml")||name.endsWith(".properties")||name.endsWith(".txt")));
                if(!inspect||e.isDirectory())continue;
                files++;
                String carry="";
                int r;long read=0;
                while((r=zip.read(buffer))!=-1){
                    read+=r;
                    if(read>35_000_000)break; // one component cap
                    String segment=carry+new String(buffer,0,r,StandardCharsets.ISO_8859_1);
                    extract(segment);
                    carry=segment.substring(Math.max(0,segment.length()-250));
                }
                zip.closeEntry();
                if(files>=110)break;
            }
        }
        StringBuilder out=new StringBuilder();
        out.append("Joolink Viewer – pilveliidesed (APK analüüs, OFFLINE)\n");
        out.append("Allikas: ").append(source).append("\n");
        out.append("Kontrollitud komponente: ").append(files).append("\n");
        out.append("Paroole, token'eid ja API-vastuseid pole loetud.\n\n");
        out.append("Serveri domeenid (võivad sisaldada SDK/reklaamide domeene):\n");
        if(hosts.isEmpty())out.append("Ühtegi sobivat domeeni ei leitud.\n");
        else for(String host:hosts)out.append(" • ").append(host).append("\n");
        out.append("\nVõimalikud autentimis- ja seadme API rajad:\n");
        if(routes.isEmpty())out.append("Loetavaid API radu ei leitud.\n");
        else for(String route:routes)out.append(" • ").append(route).append("\n");
        out.append("\nNeed on APK-st leitud viited, mitte kinnitatud ega töötavad serveri API-d.\n");
        return out.toString();
    }
    private void extract(String segment){
        Matcher urls=URLS.matcher(segment);
        while(urls.find()){
            String raw=urls.group();
            try{
                // Strip all URL path/query metadata before collecting hosts.
                URI uri=new URI(raw);
                String host=uri.getHost();
                if(host!=null)keepHost(host);
                String path=uri.getPath();
                if(path!=null)keepRoute(path);
            }catch(Exception ignored){}
        }
        Matcher names=HOSTS.matcher(segment);
        while(names.find())keepHost(names.group());
        Matcher endpoints=ENDPOINTS.matcher(segment);
        while(endpoints.find())keepRoute(endpoints.group());
    }
    private void keepHost(String host){
        String h=host.toLowerCase(Locale.ROOT);
        if(h.length()<5||h.length()>110||hosts.size()>=100)return;
        if(h.contains("google")||h.contains("facebook")||h.contains("doubleclick")||
           h.contains("firebase")||h.contains("amazonaws")||h.contains("android")||
           h.contains("maven")||h.contains("w3.org")||h.contains("apache")||
           h.contains("schemas")||h.contains("gstatic"))return;
        if(!h.matches("[a-z0-9.-]+")||!h.contains("."))return;
        hosts.add(h);
    }
    private void keepRoute(String path){
        if(path==null||path.length()<5||routes.size()>=100)return;
        String clean=path.split("[?#]",2)[0];
        if(!clean.matches("/[a-zA-Z0-9_./-]{4,100}"))return;
        if(!ENDPOINTS.matcher(clean).find())return;
        // No query params, credentials or tokens included in output.
        routes.add(clean);
    }
    private void copyReport(){
        ClipboardManager clip=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        clip.setPrimaryClip(ClipData.newPlainText("Joolinki diagnostika",report));
        Toast.makeText(this,"Puhastatud raport kopeeritud",Toast.LENGTH_SHORT).show();
    }
}
