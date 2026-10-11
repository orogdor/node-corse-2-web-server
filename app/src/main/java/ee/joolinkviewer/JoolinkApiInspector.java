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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.ArrayList;
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
    private final Set<String> urlHosts=new TreeSet<>();
    private final Set<String> urlRoutes=new TreeSet<>();
    private final Set<String> brands=new TreeSet<>();
    private final Set<String> sdkModules=new TreeSet<>();
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
        Ui.add(root,Ui.text(this,"Joolinki pilveühenduse uuring 2",22,true),this,4);
        TextView hint=Ui.text(this,
          "Täiustatud uuring: eristab APK-s olevaid päris URL-e üldistest Alibaba teekidest ning kontrollib rohkem komponente. Ei loe kasutaja kontot, paroole ega sessioone. Analüüs toimub telefonis ja midagi ei saadeta serverisse. See ei loo veel Joolinki sisselogimist.",13,false);
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
        working=true;hosts.clear();routes.clear();urlHosts.clear();
        urlRoutes.clear();brands.clear();sdkModules.clear();report="";
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
        int files=0,dex=0,nativeLibraries=0,config=0;
        long bytes=0;
        byte[] buffer=new byte[65536];
        try(InputStream in=new BufferedInputStream(input.open());
            ZipInputStream zip=new ZipInputStream(in)){
            ZipEntry e;
            while((e=zip.getNextEntry())!=null){
                String name=e.getName().toLowerCase(Locale.ROOT);
                boolean isDex=name.matches("classes[0-9]*\\.dex");
                boolean isLibrary=name.matches("lib/(?:arm64-v8a|armeabi-v7a)/[^/]+\\.so");
                boolean isConfig=(name.startsWith("assets/")||name.startsWith("res/raw/"))&&
                        (name.endsWith(".json")||name.endsWith(".xml")||
                        name.endsWith(".properties")||name.endsWith(".txt")||
                        name.endsWith(".cfg")||name.endsWith(".ini"));
                if((!isDex&&!isLibrary&&!isConfig)||e.isDirectory())continue;
                files++;if(isDex)dex++;if(isLibrary)nativeLibraries++;if(isConfig)config++;
                String carry="";int read;long fileBytes=0;
                long limit=isDex?50000000L:(isLibrary?8500000L:12000000L);
                while(fileBytes<limit&&(read=zip.read(buffer,0,
                        (int)Math.min(buffer.length,limit-fileBytes)))!=-1){
                    fileBytes+=read;bytes+=read;
                    String segment=carry+new String(buffer,0,read,StandardCharsets.ISO_8859_1);
                    extract(segment);
                    carry=segment.substring(Math.max(0,segment.length()-300));
                }
                zip.closeEntry();
                if(files>=600||bytes>250000000)break;
            }
        }
        StringBuilder out=new StringBuilder();
        out.append("Joolink Viewer – PILVE DIAGNOSTIKA 2 (OFFLINE)\n");
        out.append("Allikas: ").append(source).append("\n");
        out.append("Komponente: ").append(files).append(" (DEX ").append(dex)
           .append(", native ").append(nativeLibraries)
           .append(", konfiguratsioon ").append(config).append(")\n");
        out.append("Paroole, tokeneid ega telefoni kontot ei loetud. Serveriparinguid ei tehtud.\n\n");
        out.append("1. JOOLINK/JOOAN spetsiifilised viited (kõrgem prioriteet):\n");
        appendNames(out,brands,40,"Joolinki-spetsiifilisi domeene ei leitud.");
        out.append("\n2. Serverid, mis esinesid HTTPS/HTTP aadressis:\n");
        appendNames(out,urlHosts,70,"Päris HTTP/HTTPS aadressides domeene ei leitud.");
        out.append("\n3. URL-ides esinenud võimalikud API rajad (mitte klassinimed):\n");
        appendNames(out,urlRoutes,55,"Tuvastatud URL-ides sobivaid API radu ei leitud.");
        out.append("\n4. Tuvastatud integreeritud SDK moodulid:\n");
        appendNames(out,sdkModules,25,"Ühtegi teadaolevat SDK viidet ei leitud.");
        out.append("\n5. Muud tõenäolised serverid (sh Alibaba SDK):\n");
        TreeSet<String> other=new TreeSet<>(hosts);
        other.removeAll(urlHosts);other.removeAll(brands);
        appendNames(out,other,45,"Täiendavaid domeene ei leitud.");
        out.append("\nMÄRKUS: staatilised viited ei kinnita, et neid teenuseid praegu kasutatakse. ");
        out.append("Joolinki konto sisselogimiseks on vaja kinnitatud serveriaadressi, päringuvormingut ja rakenduse autentimismehhanismi.\n");
        return out.toString();
    }
    private static void appendNames(StringBuilder out,Set<String> list,int limit,String empty){
        if(list.isEmpty()){out.append(empty).append("\n");return;}
        int n=0;
        for(String item:list){if(n++>=limit)break;out.append(" • ").append(item).append("\n");}
        if(list.size()>limit)out.append(" ... ").append(list.size()-limit).append(" täiendavat kirjet peidetud\n");
    }
    private void extract(String segment){
        // A URL is stronger evidence than a coincidental Java class/package name.
        Matcher urls=URLS.matcher(segment);
        while(urls.find()){
            String raw=urls.group();
            try{
                URI uri=new URI(raw);
                String host=uri.getHost(),path=uri.getPath();
                if(host==null)continue;
                if(keepHost(host)){
                    if(urlHosts.size()<500)urlHosts.add(host.toLowerCase(Locale.ROOT));
                    if(path!=null)keepRoute(path,true);
                }
            }catch(Exception ignored){}
        }
        Matcher names=HOSTS.matcher(segment);
        while(names.find())keepHost(names.group());
        Matcher api=ENDPOINTS.matcher(segment);
        while(api.find())keepRoute(api.group(),false);
        if(segment.contains("IoTSmart")&&sdkModules.size()<40)
            sdkModules.add("Alibaba Link Living: IoTSmart");
        if(segment.contains("OpenAccount")&&sdkModules.size()<40)
            sdkModules.add("Alibaba OpenAccount (konto SDK)");
        if(segment.contains("IoTCredential")&&sdkModules.size()<40)
            sdkModules.add("Alibaba IoTCredential");
        if(segment.contains("LinkVisual")&&sdkModules.size()<40)
            sdkModules.add("Alibaba LinkVisual video SDK");
        if(segment.contains("KP2P")&&sdkModules.size()<40)
            sdkModules.add("KP2P seadmepõhine P2P");
    }
    private boolean keepHost(String host){
        String h=host.toLowerCase(Locale.ROOT);
        if(h.length()<7||h.length()>130||hosts.size()>=1200)return false;
        if(!h.matches("[a-z0-9.-]+")||!h.contains("."))return false;
        String[] parts=h.split("\\.");
        if(parts.length<2)return false;
        String tld=parts[parts.length-1];
        if(tld.length()<2||tld.length()>14)return false;
        if(h.startsWith("com.")||h.startsWith("org.")||h.startsWith("net.")||
           h.startsWith("java.")||h.startsWith("javax.")||
           h.startsWith("android.")||h.startsWith("kotlin.")||
           h.startsWith("support.")||h.startsWith("t."))return false;
        if(h.contains("google")||h.contains("facebook")||h.contains("doubleclick")||
           h.contains("firebase")||h.contains("amazonaws")||h.contains("w3.org")||
           h.contains("gstatic")||h.contains("schemas")||
           h.contains("gradle")||h.contains("maven")||h.contains("apache"))return false;
        hosts.add(h);
        if(h.contains("jooan")||h.contains("joolink")||h.contains("qiaoan")||
           h.contains("joo-")||h.contains("joocom")){
            if(brands.size()<80)brands.add(h);
        }
        return true;
    }
    private void keepRoute(String path,boolean verifiedUrl){
        if(path==null||path.length()<4)return;
        // Query, fragments, and embedded identifiers are NEVER copied to the report.
        String clean=path.split("[?#]",2)[0];
        if(!clean.matches("/[a-zA-Z0-9_./-]{4,100}"))return;
        if(!ENDPOINTS.matcher(clean).find())return;
        // A longer path can contain account/device IDs; keep only first two segments.
        String[] p=clean.split("/");
        StringBuilder safe=new StringBuilder();
        for(int i=1;i<Math.min(p.length,4);i++){
            if(p[i].length()>25||p[i].matches(".*[0-9]{5,}.*"))break;
            safe.append('/').append(p[i]);
        }
        if(safe.length()<4)return;
        if(verifiedUrl&&urlRoutes.size()<220)urlRoutes.add(safe.toString());
        else if(routes.size()<600)routes.add(safe.toString());
    }
    private void copyReport(){
        ClipboardManager clip=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        clip.setPrimaryClip(ClipData.newPlainText("Joolinki diagnostika",report));
        Toast.makeText(this,"Puhastatud raport kopeeritud",Toast.LENGTH_SHORT).show();
    }
}
