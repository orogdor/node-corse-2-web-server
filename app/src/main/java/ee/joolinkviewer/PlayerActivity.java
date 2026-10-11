package ee.joolinkviewer;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.FrameLayout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;
import java.util.ArrayList;

public class PlayerActivity extends Activity {
    private final String[] modes = {
            "Tarkvara + TextureView (värviparandus)", "Tarkvara + SurfaceView",
            "Riistvara + TextureView", "Riistvara + SurfaceView"
    };
    private LibVLC engine;
    private MediaPlayer player;
    private VLCVideoLayout video;
    private TextView status;
    private Spinner selector;
    private String streamUrl;
    private boolean isClosed = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        streamUrl = getIntent().getStringExtra("url");
        if (streamUrl == null || !RtspNetwork.validUrl(streamUrl)) { finish(); return; }
        LinearLayout root = Ui.vertical(this);
        root.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        root.setBackgroundColor(Ui.BG);
        setContentView(root);

        String title = getIntent().getStringExtra("camera");
        Ui.add(root, Ui.text(this, title == null || title.isEmpty() ? "Kaamerapilt" : title, 21, true), this, 0);
        video = new VLCVideoLayout(this);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        vp.topMargin = Ui.dp(this, 8);
        vp.bottomMargin = Ui.dp(this, 10);
        FrameLayout videoContainer = new FrameLayout(this);
        root.addView(videoContainer, vp);
        videoContainer.addView(video, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        status = Ui.text(this, "Laadin videopilti …", 13, false);
        status.setTextColor(Ui.MUTED);
        Ui.add(root, status, this, 2);
        selector = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes);
        selector.setAdapter(adapter);
        selector.setSelection(getPreferences(MODE_PRIVATE).getInt("render-mode", 0));
        Ui.add(root, selector, this, 7);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        Button restart = Ui.button(this, "↻ Ava uuesti");
        restart.setOnClickListener(v -> startStream());
        buttons.addView(restart, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        Button rotate = Ui.secondary(this, "Täisekraan");
        rotate.setOnClickListener(v -> {
            boolean landscape = getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
            setRequestedOrientation(landscape ? ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1);
        rp.leftMargin = Ui.dp(this, 8);
        buttons.addView(rotate, rp);
        Ui.add(root, buttons, this, 10);
        Button back = Ui.secondary(this, "← Tagasi kaamerate juurde");
        back.setOnClickListener(v -> finish());
        Ui.add(root, back, this, 8);
        selector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            private boolean first = true;
            @Override public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) {
                getPreferences(MODE_PRIVATE).edit().putInt("render-mode", pos).apply();
                if (first) { first = false; return; }
                startStream();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
    }
    @Override protected void onStart() { super.onStart(); if (streamUrl != null) startStream(); }
    @Override protected void onStop() { stopStream(); super.onStop(); }
    private void startStream() {
        if (isClosed || streamUrl == null) return;
        stopStream();
        int mode = selector.getSelectedItemPosition();
        boolean hw = mode >= 2;
        boolean texture = mode == 0 || mode == 2;
        status.setText("Ühendan … " + (hw ? "riistvaraline" : "tarkvaraline") + " dekooder");
        try {
            ArrayList<String> params = new ArrayList<>();
            params.add("--rtsp-tcp");
            params.add("--network-caching=450");
            params.add("--no-audio");
            engine = new LibVLC(this, params);
            player = new MediaPlayer(engine);
            player.attachViews(video, null, false, texture);
            player.setEventListener(event -> {
                if (isClosed) return;
                if (event.type == MediaPlayer.Event.Playing) {
                    runOnUiThread(() -> status.setText("● Otseülekanne • " + (hw ? "riistvara" : "tarkvara")));
                } else if (event.type == MediaPlayer.Event.EncounteredError) {
                    runOnUiThread(() -> status.setText("Videovoog ei avanenud. Kontrolli RTSP-aadressi, parooli ja kas kaamera toetab RTSP-d."));
                } else if (event.type == MediaPlayer.Event.Buffering) {
                    runOnUiThread(() -> status.setText("Puhverdan videopilti …"));
                }
            });
            Media media = new Media(engine, Uri.parse(streamUrl));
            media.setHWDecoderEnabled(hw, hw);
            media.addOption(":rtsp-tcp");
            media.addOption(":network-caching=450");
            player.setMedia(media);
            media.release();
            player.play();
        } catch (Exception err) {
            status.setText("Mängija käivitamise viga: " + err.getClass().getSimpleName());
            stopStream();
        }
    }
    private void stopStream() {
        if (player != null) {
            player.setEventListener(null);
            player.stop();
            player.detachViews();
            player.release();
            player = null;
        }
        if (engine != null) { engine.release(); engine = null; }
    }
    @Override protected void onDestroy() { isClosed = true; stopStream(); super.onDestroy(); }
}
