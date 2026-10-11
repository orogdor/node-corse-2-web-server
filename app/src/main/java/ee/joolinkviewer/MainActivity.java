package ee.joolinkviewer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

public class MainActivity extends Activity {
    private LinearLayout base, foundList, savedList;
    private EditText nameInput, urlInput, userInput, passInput;
    private TextView status;
    private Button scanButton;
    private volatile boolean destroyed = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);
        base = Ui.vertical(this);
        base.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 36));
        scroll.addView(base);
        setContentView(scroll);

        Ui.add(base, Ui.text(this, "JOOLINK VIEWER", 25, true), this, 0);
        TextView tagline = Ui.text(this, "Kaamerapilt otse WiFi-võrgust • ilma Joolinki äpita", 13, false);
        tagline.setTextColor(Ui.MUTED);
        Ui.add(base, tagline, this, 1);
        Ui.add(base, Ui.text(this, "1. Otsi kaameraid", 19, true), this, 20);
        scanButton = Ui.button(this, "Otsi WiFi-võrgust kaameraid");
        scanButton.setOnClickListener(v -> scan());
        Ui.add(base, scanButton, this, 9);
        status = Ui.text(this, "Telefon ja kaamera peavad olema samas WiFi-võrgus.", 13, false);
        status.setTextColor(Ui.MUTED);
        Ui.add(base, status, this, 8);
        foundList = Ui.vertical(this);
        Ui.add(base, foundList, this, 5);

        Ui.add(base, Ui.text(this, "2. Kaamera aadress", 19, true), this, 19);
        TextView hint = Ui.text(this, "Vali leitud kaamera või sisesta RTSP-aadress. Vaikimisi proovitakse teadaolevaid Joolinki ja üldisi RTSP-radu.", 13, false);
        hint.setTextColor(Ui.MUTED);
        Ui.add(base, hint, this, 3);
        nameInput = Ui.input(this, "Kaamera nimi (näiteks Õu)");
        nameInput.setText("Kaamera 1");
        Ui.add(base, nameInput, this, 10);
        urlInput = Ui.input(this, "rtsp://192.168.1.123:5540/live/ch0");
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        Ui.add(base, urlInput, this, 9);
        userInput = Ui.input(this, "Kasutajanimi (vajadusel)");
        Ui.add(base, userInput, this, 9);
        passInput = Ui.input(this, "Kaamera parool (vajadusel, ei salvestata)");
        passInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Ui.add(base, passInput, this, 9);
        Button playButton = Ui.button(this, "▶ Ava kaamerapilt");
        playButton.setOnClickListener(v -> play());
        Ui.add(base, playButton, this, 12);
        Button saveButton = Ui.secondary(this, "Salvesta kaamera loendisse");
        saveButton.setOnClickListener(v -> save());
        Ui.add(base, saveButton, this, 7);

        Ui.add(base, Ui.text(this, "Minu kaamerad", 19, true), this, 21);
        savedList = Ui.vertical(this);
        Ui.add(base, savedList, this, 6);
        redrawSaved();

        TextView footer = Ui.text(this,
                "NB! Rakendus töötab ainult RTSP-d pakkuvate kaameratega. Mõned Joolinki mudelid lubavad pilti ainult tootja pilve kaudu. " +
                "See versioon ei saa neist pilvepiirangutest mööda. Otsing vaatab ainult koduse WiFi-võrgu ühte /24 aadressivahemikku.", 12, false);
        footer.setTextColor(Ui.MUTED);
        Ui.add(base, footer, this, 21);
    }

    private void setStatus(String s) { if (!destroyed) runOnUiThread(() -> status.setText(s)); }

    private void scan() {
        String prefix = RtspNetwork.wifiBase(this);
        if (prefix == null) {
            setStatus("Ühenda telefon kodusesse WiFi-võrku ja lülita VPN vajadusel välja. Avalikke võrke ei otsita.");
            return;
        }
        scanButton.setEnabled(false);
        foundList.removeAllViews();
        setStatus("Otsin: " + prefix + "1–254 (pordid 554, 5540, 8554, 10554) …");
        new Thread(() -> RtspNetwork.scanHosts(prefix, new RtspNetwork.ScanCallback() {
            @Override public void found(String ip, int port) {
                if (destroyed) return;
                runOnUiThread(() -> addCandidate(ip, port));
            }
            @Override public void finish(String message) {
                if (destroyed) return;
                runOnUiThread(() -> { status.setText(message); scanButton.setEnabled(true); });
            }
        }), "joolink-lan-scan").start();
    }
    private void addCandidate(String ip, int port) {
        LinearLayout card = Ui.card(this);
        Ui.add(card, Ui.text(this, "Võimalik kaamera: " + ip + ":" + port, 15, true), this, 0);
        Button use = Ui.secondary(this, "Leia videovoog ja kasuta");
        Ui.add(card, use, this, 7);
        use.setOnClickListener(v -> {
            setStatus("Kontrollin RTSP-aadresse: " + ip + ":" + port + " …");
            use.setEnabled(false);
            new Thread(() -> {
                String stream = RtspNetwork.findPath(ip, port);
                if (!destroyed) runOnUiThread(() -> {
                    urlInput.setText(stream);
                    nameInput.setText("Kaamera " + ip.substring(ip.lastIndexOf('.') + 1));
                    use.setEnabled(true);
                    setStatus("Aadress: " + stream + ". Kui pilt ei avane, muuda tee käsitsi.");
                });
            }, "joolink-path-check").start();
        });
        Ui.add(foundList, card, this, 8);
    }

    private String formUrl() {
        String url = urlInput.getText().toString().trim();
        if (!RtspNetwork.validUrl(url)) {
            Toast.makeText(this, "Sisesta korrektne rtsp:// aadress.", Toast.LENGTH_LONG).show();
            return null;
        }
        return url;
    }
    private void play() {
        String url = formUrl();
        if (url == null) return;
        String user = userInput.getText().toString().trim();
        String pass = passInput.getText().toString();
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("camera", nameInput.getText().toString().trim());
        intent.putExtra("url", RtspNetwork.withCredentials(url, user, pass));
        startActivity(intent);
    }
    private void save() {
        String url = formUrl();
        if (url == null) return;
        String user = userInput.getText().toString().trim();
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) name = "Kaamera";
        List<CameraStore.Camera> list = CameraStore.load(this);
        String safe = RtspNetwork.stripCredentials(url);
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).url.equals(safe)) {
                list.set(i, new CameraStore.Camera(name, safe, user));
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(new CameraStore.Camera(name, safe, user));
        CameraStore.save(this, list);
        redrawSaved();
        Toast.makeText(this, "Kaamera salvestatud. Parooli ei salvestatud.", Toast.LENGTH_SHORT).show();
    }
    private void redrawSaved() {
        savedList.removeAllViews();
        List<CameraStore.Camera> cams = CameraStore.load(this);
        if (cams.isEmpty()) {
            TextView empty = Ui.text(this, "Veel pole salvestatud kaameraid.", 13, false);
            empty.setTextColor(Ui.MUTED);
            Ui.add(savedList, empty, this, 0);
            return;
        }
        for (CameraStore.Camera cam : cams) {
            LinearLayout item = Ui.card(this);
            Ui.add(item, Ui.text(this, cam.name, 16, true), this, 0);
            TextView small = Ui.text(this, cam.url, 12, false);
            small.setTextColor(Ui.MUTED);
            Ui.add(item, small, this, 1);
            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            Button open = Ui.button(this, "Vali");
            open.setOnClickListener(v -> {
                nameInput.setText(cam.name);
                urlInput.setText(cam.url);
                userInput.setText(cam.username);
                passInput.setText("");
                Toast.makeText(this, "Kaamera valitud. Vajadusel sisesta parool.", Toast.LENGTH_SHORT).show();
            });
            actions.addView(open, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1));
            Button delete = Ui.secondary(this, "Kustuta");
            delete.setOnClickListener(v -> {
                List<CameraStore.Camera> current = CameraStore.load(this);
                current.removeIf(x -> x.url.equals(cam.url));
                CameraStore.save(this, current);
                redrawSaved();
            });
            LinearLayout.LayoutParams delParams = new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1);
            delParams.leftMargin = Ui.dp(this, 10);
            actions.addView(delete, delParams);
            Ui.add(item, actions, this, 9);
            Ui.add(savedList, item, this, 9);
        }
    }
    @Override protected void onDestroy() { destroyed = true; super.onDestroy(); }
}
