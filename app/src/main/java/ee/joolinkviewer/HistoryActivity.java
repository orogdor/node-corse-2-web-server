package ee.joolinkviewer;

import android.app.Activity;
import org.json.JSONArray;
import android.content.Intent;
import android.content.ContentResolver;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.InputType;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends Activity {
    private LinearLayout content,localList,remoteList;
    private TextView remoteStatus,localStatus,selectedDate;
    private LinearLayout importedList;
    private static final int PICK_VIDEO=3196;
    private long filterDay=-1;
    private String cameraIp,user,pass;
    private int port;
    private volatile boolean closed;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        cameraIp=getIntent().getStringExtra("camera_ip");
        user=getIntent().getStringExtra("user");pass=getIntent().getStringExtra("pass");
        port=getIntent().getIntExtra("onvif_port",80);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.BG);
        content=Ui.vertical(this);
        content.setPadding(Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,24));
        scroll.addView(content);setContentView(scroll);
        content.setOnApplyWindowInsetsListener((v,insets)->{
            int top=android.os.Build.VERSION.SDK_INT>=30?
                insets.getInsets(android.view.WindowInsets.Type.statusBars()).top:insets.getSystemWindowInsetTop();
            int bottom=android.os.Build.VERSION.SDK_INT>=30?
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom:insets.getSystemWindowInsetBottom();
            v.setPadding(Ui.dp(this,16),Ui.dp(this,16)+top,Ui.dp(this,16),Ui.dp(this,24)+bottom);
            return insets;
        });
        content.requestApplyInsets();
        Ui.add(content,Ui.text(this,"Kaamera ajalugu",23,true),this,0);
        TextView intro=Ui.text(this,"Kohalikud videod ja võimalusel ONVIF salvestused kaamera mälukaardilt.",13,false);
        intro.setTextColor(Ui.MUTED);Ui.add(content,intro,this,3);
        Ui.add(content,Ui.text(this,"Telefonis salvestatud videod",18,true),this,16);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        Button calendar=Ui.secondary(this,"Vali kuupäev");
        calendar.setOnClickListener(v->pickDate());
        row.addView(calendar,new LinearLayout.LayoutParams(0,Ui.dp(this,47),1));
        Button all=Ui.secondary(this,"Kõik päevad");
        all.setOnClickListener(v->{filterDay=-1;selectedDate.setText("Kõik salvestised");refreshLocal();});
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,Ui.dp(this,47),1);
        ap.leftMargin=Ui.dp(this,6);row.addView(all,ap);
        Ui.add(content,row,this,8);
        selectedDate=Ui.text(this,"Kõik salvestised",13,false);
        Ui.add(content,selectedDate,this,3);
        localStatus=Ui.text(this,"Otsin rakenduse salvestusi …",12,false);
        localStatus.setTextColor(Ui.MUTED);Ui.add(content,localStatus,this,3);
        localList=Ui.vertical(this);Ui.add(content,localList,this,5);
        refreshLocal();
        Ui.add(content,Ui.text(this,"Lisa salvestis telefonist või SD-kaardilt",18,true),this,16);
        TextView pickerHelp=Ui.text(this,"Kui kaamera salvestab mälukaardile, aga ei anna ajalugu ONVIF-i kaudu, saad valida telefonis olevad või USB/SD-kaardi lugejast avatud MP4/MKV/TS videod.",12,false);
        pickerHelp.setTextColor(Ui.MUTED);Ui.add(content,pickerHelp,this,4);
        Button importButton=Ui.secondary(this,"+ Vali telefoni või SD-kaardi videofailid");
        importButton.setOnClickListener(v->pickVideo());
        Ui.add(content,importButton,this,7);
        importedList=Ui.vertical(this);
        Ui.add(content,importedList,this,5);
        refreshImported();
        Ui.add(content,Ui.text(this,"Kaamera mälukaart / NVR (ONVIF)",18,true),this,18);
        remoteStatus=Ui.text(this,"ONVIF arhiiv nõuab salvestus- ja taasesitusteenuse tuge.",13,false);
        remoteStatus.setTextColor(Ui.MUTED);Ui.add(content,remoteStatus,this,3);
        remoteList=Ui.vertical(this);Ui.add(content,remoteList,this,6);
        Button check=Ui.button(this,"Otsi kaamera mälukaardi salvestisi");
        check.setOnClickListener(v->refreshRemote());Ui.add(content,check,this,9);
        Ui.add(content,Ui.text(this,"Ava oma salvestise URL",18,true),this,17);
        TextView hint=Ui.text(this,"RTSP replay, HTTP MP4, HLS või muu võrgu kaudu kättesaadav salvestus.",12,false);
        hint.setTextColor(Ui.MUTED);Ui.add(content,hint,this,3);
        EditText manual=Ui.input(this,"rtsp://... või https://.../video.mp4");
        manual.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        Ui.add(content,manual,this,5);
        Button open=Ui.secondary(this,"▶ Ava salvestise video");
        open.setOnClickListener(v->{
            String url=manual.getText().toString().trim();
            if(!RtspNetwork.validUrl(url)){remoteStatus.setText("Salvestise aadress peab olema korrektne.");return;}
            startPlayer("Arhiivivideo",url,false);
        });
        Ui.add(content,open,this,7);
        TextView limitation=Ui.text(this,"Mõned Joolinki kaamerad ei ava SD-kaardi ajalugu ONVIF kaudu. Sel juhul on vajalik tootja pilveteenus või tootja spetsiaalne liides. " +
          "Telefoni kohalik ajalugu sisaldab ainult selles rakenduses tehtud salvestusi. Ülal võid lisada eraldi videofaile.",12,false);
        limitation.setTextColor(Ui.MUTED);Ui.add(content,limitation,this,16);
    }
    private void pickVideo(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("video/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        startActivityForResult(i,PICK_VIDEO);
    }
    @Override protected void onActivityResult(int request,int result,Intent intent){
        super.onActivityResult(request,result,intent);
        if(request!=PICK_VIDEO||result!=RESULT_OK||intent==null)return;
        JSONArray saved;
        try{saved=new JSONArray(getPreferences(MODE_PRIVATE).getString("external_video_uris","[]"));}
        catch(Exception ex){saved=new JSONArray();}
        List<Uri> incoming=new ArrayList<>();
        if(intent.getData()!=null)incoming.add(intent.getData());
        if(intent.getClipData()!=null)for(int n=0;n<intent.getClipData().getItemCount();n++)
            incoming.add(intent.getClipData().getItemAt(n).getUri());
        for(Uri item:incoming){
            try{
                getContentResolver().takePersistableUriPermission(item,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                boolean exists=false;
                for(int n=0;n<saved.length();n++)if(item.toString().equals(saved.optString(n)))exists=true;
                if(!exists)saved.put(item.toString());
            }catch(Exception ignored){}
        }
        getPreferences(MODE_PRIVATE).edit().putString("external_video_uris",saved.toString()).apply();
        refreshImported();
    }
    private void refreshImported(){
        if(importedList==null)return;
        importedList.removeAllViews();
        JSONArray arr;
        try{arr=new JSONArray(getPreferences(MODE_PRIVATE).getString("external_video_uris","[]"));}
        catch(Exception e){arr=new JSONArray();}
        for(int i=0;i<arr.length();i++){
            String address=arr.optString(i);
            if(address.isEmpty())continue;
            Uri uri=Uri.parse(address);
            String title="Videofail "+(i+1);
            try(android.database.Cursor cur=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
                if(cur!=null&&cur.moveToFirst())title=cur.getString(0);
            }catch(Exception ignored){}
            Button b=Ui.secondary(this,"▶ "+title);
            b.setOnClickListener(v->startPlayer("Avatud fail",address,true));
            Ui.add(importedList,b,this,6);
        }
    }
    private File dir(){
        File base=getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if(base==null)base=getFilesDir();
        return new File(base,"JoolinkViewer");
    }
    private void pickDate(){
        Calendar c=Calendar.getInstance();
        new DatePickerDialog(this,(picker,year,month,day)->{
            Calendar cal=Calendar.getInstance();
            cal.set(year,month,day,0,0,0);cal.set(Calendar.MILLISECOND,0);
            filterDay=cal.getTimeInMillis();
            selectedDate.setText(new SimpleDateFormat("dd.MM.yyyy",Locale.getDefault()).format(new Date(filterDay)));
            refreshLocal();
        },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
    }
    private void refreshLocal(){
        localList.removeAllViews();
        File folder=dir();
        File[] all=folder.listFiles(file->file.isFile()&&(
            file.getName().toLowerCase(Locale.ROOT).endsWith(".mp4")||
            file.getName().toLowerCase(Locale.ROOT).endsWith(".ts")||
            file.getName().toLowerCase(Locale.ROOT).endsWith(".mkv")||
            file.getName().toLowerCase(Locale.ROOT).endsWith(".mov")));
        if(all==null)all=new File[0];
        Arrays.sort(all,Comparator.comparingLong(File::lastModified).reversed());
        int count=0;
        SimpleDateFormat format=new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault());
        for(File f:all){
            if(filterDay>=0&&(f.lastModified()<filterDay||f.lastModified()>=filterDay+86400000L))continue;
            String label=format.format(new Date(f.lastModified()))+"  •  "+Math.max(1,f.length()/1024/1024)+" MB";
            Button button=Ui.secondary(this,"▶ "+label+"\n"+f.getName());
            button.setOnClickListener(v->startPlayer("Telefonis salvestatud",Uri.fromFile(f).toString(),true));
            Ui.add(localList,button,this,6);count++;if(count>=150)break;
        }
        localStatus.setText(count==0?"Valitud perioodil salvestisi ei ole.":count+" salvestist (uusimad esimesena). Puuduta salvestist selle avamiseks.");
    }
    @Override protected void onResume(){super.onResume();if(localList!=null)refreshLocal();if(importedList!=null)refreshImported();}
    private void refreshRemote(){
        remoteList.removeAllViews();
        if(cameraIp==null||!RtspNetwork.isPrivate(cameraIp)){
            remoteStatus.setText("Kaamera kohalik IP puudub. Vali avalehel kaamera.");return;
        }
        remoteStatus.setText("Ühendan ONVIF Recording/Replay teenustega …");
        new Thread(()->{
            try{
                OnvifClient c=OnvifClient.autoConnect(cameraIp,port,user,pass);
                if(!c.recordingAvailable){
                    showRemoteMessage("ONVIF port "+c.devicePort()+" ühendus korras, aga kaamera ei paku Recording/Replay teenuseid. SD-kaardi arhiiv vajab tootja liidest. Telefoni videod töötavad eraldi.");
                    return;
                }
                List<OnvifClient.RemoteRecording> items=c.recordings();
                if(closed)return;
                runOnUiThread(()->{
                    remoteStatus.setText(items.isEmpty()?"ONVIF port "+c.devicePort()+" ei tagastanud salvestisi.":"Leidsin "+items.size()+" ONVIF arhiivivoogu (port "+c.devicePort()+").");
                    for(OnvifClient.RemoteRecording item:items){
                        Button b=Ui.secondary(this,"▶ "+item.name+" (kaamera arhiiv)");
                        b.setOnClickListener(v->startPlayer(item.name,RtspNetwork.withCredentials(item.uri,user,pass),false));
                        Ui.add(remoteList,b,this,7);
                    }
                });
            }catch(Exception e){showRemoteMessage("Arhiivi ei saanud avada: "+e.getMessage());}
        },"onvif-recording-browser").start();
    }
    private void showRemoteMessage(String text){if(!closed)runOnUiThread(()->remoteStatus.setText(text));}
    private void startPlayer(String title,String url,boolean local){
        Intent i=new Intent(this,PlayerActivity.class);
        i.putExtras(getIntent().getExtras());
        i.putExtra("camera",title);i.putExtra("url",url);i.putExtra("recording",local);
        startActivity(i);
    }
    @Override protected void onDestroy(){closed=true;super.onDestroy();}
}
