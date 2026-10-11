package com.jooan.qiaoanzhilian.ui.activity.play;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.lang.reflect.Field;

/** Experimental Joolin2 extension; no login, network, or account permissions added.
 * Decoder flag is applied before the vendor's monitor-selection functions.
 * Color conversion is a post-render RGB filter, not native NV12/NV21 buffer change.
 */
public final class VideoSettings {
    private static final String PREFS="joolin2_decoder_menu_v1";
    private static final String DECODER="decoder";
    private static final String COLOR="color";
    private static final String[] DECODERS={
        "Automaatne – Joolinki vaikimisi valik",
        "Tarkvaraline (SoftMonitor)",
        "Riistvaraline (HardMonitor)"
    };
    private static final String[] COLORS={
        "Originaalvärvid",
        "U/V vahetus (RGB järelparandus)",
        "BT.601 → BT.709",
        "BT.709 → BT.601"
    };
    private VideoSettings(){}
    private static SharedPreferences prefs(Activity activity){
        return activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    public static void install(Activity activity){
        try{
            FrameLayout container=(FrameLayout)activity.getWindow().getDecorView()
                .findViewById(android.R.id.content);
            if(container==null||container.findViewWithTag("joolin2_video_menu")!=null)return;
            Button button=new Button(activity);
            button.setTag("joolin2_video_menu");
            button.setText("⚙ Video");
            button.setAllCaps(false);
            button.setTextColor(Color.WHITE);
            button.setBackgroundTintList(ColorStateList.valueOf(0xff314256));
            FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(
                dp(activity,110),dp(activity,47));
            params.gravity=Gravity.RIGHT|Gravity.TOP;
            params.setMargins(0,dp(activity,65),dp(activity,10),0);
            container.addView(button,params);
            button.setOnClickListener(v->showMenu(activity));
            container.postDelayed(()->apply(activity),500);
        }catch(Throwable exception){
            Toast.makeText(activity,"Joolin2 menüü käivitamise viga",Toast.LENGTH_LONG).show();
        }
    }
    private static int dp(Context context,int size){
        return Math.round(size*context.getResources().getDisplayMetrics().density);
    }
    private static void showMenu(Activity activity){
        new AlertDialog.Builder(activity).setTitle("Joolin2 – videoseaded")
            .setItems(new String[]{"Videodekooder","Värviparandus (katse)",
                "Taasta algseaded"},(d,which)->{
                if(which==0)chooseDecoder(activity);
                else if(which==1)chooseColors(activity);
                else {
                    prefs(activity).edit().clear().apply();
                    activity.recreate();
                }
            }).setNegativeButton("Sulge",null).show();
    }
    private static void chooseDecoder(Activity activity){
        int current=prefs(activity).getInt(DECODER,0);
        new AlertDialog.Builder(activity).setTitle("Dekodeerimisrežiim")
            .setSingleChoiceItems(DECODERS,current,(dialog,choice)->{
                prefs(activity).edit().putInt(DECODER,choice).apply();
                dialog.dismiss();
                Toast.makeText(activity,"Kaameravaade käivitatakse uuesti",Toast.LENGTH_SHORT).show();
                activity.recreate();
            }).setNegativeButton("Tagasi",null).show();
    }
    private static void chooseColors(Activity activity){
        int current=prefs(activity).getInt(COLOR,0);
        new AlertDialog.Builder(activity).setTitle("Katseta videopildi värve")
            .setSingleChoiceItems(COLORS,current,(dialog,choice)->{
                prefs(activity).edit().putInt(COLOR,choice).apply();
                dialog.dismiss();
                apply(activity);
                Toast.makeText(activity,COLORS[choice],Toast.LENGTH_SHORT).show();
            }).setNegativeButton("Tagasi",null).show();
    }
    public static void apply(Activity activity){
        try{
            int choice=prefs(activity).getInt(DECODER,0);
            if(choice==1||choice==2){
                // Do not alter the automatic vendor choice unless requested.
                for(Class<?> cls=activity.getClass();cls!=null;cls=cls.getSuperclass()){
                    try{
                        Field field=cls.getDeclaredField("isRunSoft");
                        field.setAccessible(true);
                        if(field.getType()==boolean.class)field.setBoolean(activity,choice==1);
                    }catch(NoSuchFieldException ignored){}
                }
            }
            applyColor(activity);
        }catch(Throwable ignored){}
    }
    private static void applyColor(Activity activity){
        try{
            int mode=prefs(activity).getInt(COLOR,0);
            ColorMatrix matrix=matrix(mode);
            Paint paint=null;
            if(matrix!=null){
                paint=new Paint();
                paint.setColorFilter(new ColorMatrixColorFilter(matrix));
            }
            // Both monitor fields are reflection-accessed; depending on Android
            // GPU/surface type a color filter may have no effect.
            for(Class<?> cls=activity.getClass();cls!=null;cls=cls.getSuperclass()){
                for(String fieldName:new String[]{"mSoftMonitor","mHardMonitor"}){
                    try{
                        Field f=cls.getDeclaredField(fieldName);
                        f.setAccessible(true);
                        Object monitor=f.get(activity);
                        if(monitor instanceof TextureView){
                            View view=(View)monitor;
                            view.setLayerType(mode==0?View.LAYER_TYPE_NONE:View.LAYER_TYPE_HARDWARE,paint);
                        }
                    }catch(NoSuchFieldException ignored){}
                }
            }
        }catch(Throwable ignored){}
    }
    private static ColorMatrix matrix(int mode){
        if(mode==0)return null;
        double ri=mode==3?0.2126:0.299,bi=mode==3?0.0722:0.114;
        double ro=mode==2?0.2126:0.299,bo=mode==2?0.0722:0.114;
        double gi=1-ri-bi,go=1-ro-bo;
        double[] y={ri,gi,bi};
        double[] u={-ri/(2*(1-bi)),-gi/(2*(1-bi)),0.5};
        double[] v={0.5,-gi/(2*(1-ri)),-bi/(2*(1-ri))};
        if(mode==1){double[] swap=u;u=v;v=swap;}
        double[] r=new double[3],g=new double[3],b=new double[3];
        for(int i=0;i<3;i++){
            r[i]=y[i]+2*(1-ro)*v[i];
            b[i]=y[i]+2*(1-bo)*u[i];
            g[i]=(y[i]-ro*r[i]-bo*b[i])/go;
        }
        return new ColorMatrix(new float[]{
          (float)r[0],(float)r[1],(float)r[2],0,0,
          (float)g[0],(float)g[1],(float)g[2],0,0,
          (float)b[0],(float)b[1],(float)b[2],0,0,
          0,0,0,1,0
        });
    }
}
