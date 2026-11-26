package com.workspacehelper.watch;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

// Native Wear OS screen (no WebView). Talks to Firebase over REST.
public class MainActivity extends Activity {

    // The shared database URL (not a secret). Pre-filled to save typing on the
    // watch; editable on the Setup screen.
    private static final String DEFAULT_URL = "https://tirsosiguemuerto-default-rtdb.firebaseio.com";

    private SharedPreferences prefs;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable poller;
    private RingView ring;
    private final List<String> deviceIds = new ArrayList<>();
    private final List<String> deviceNames = new ArrayList<>();

    private String url() { return prefs.getString("url", ""); }
    private String secret() { return prefs.getString("secret", ""); }
    private String deviceId() { return prefs.getString("deviceId", ""); }
    private String lang() { return prefs.getString("lang", "en"); }
    private boolean es() { return lang().equals("es"); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("wh", MODE_PRIVATE);
        if (url().isEmpty() || secret().isEmpty()) {
            // If a secret was baked in at build time, connect automatically so
            // there's nothing to type on the watch.
            String baked = BuildConfig.FIREBASE_SECRET;
            if (baked != null && !baked.isEmpty()) {
                prefs.edit().putString("url", DEFAULT_URL).putString("secret", baked).apply();
                showMain();
            } else {
                showSetup();
            }
        } else {
            showMain();
        }
    }

    @Override
    protected void onDestroy() {
        stopPolling();
        super.onDestroy();
    }

    // -------- screens --------
    private void showSetup() {
        stopPolling();
        setContentView(R.layout.setup);
        EditText inUrl = findViewById(R.id.in_url);
        EditText inSecret = findViewById(R.id.in_secret);
        inUrl.setText(url().isEmpty() ? DEFAULT_URL : url());
        inSecret.setText(secret());
        ((Button) findViewById(R.id.connect)).setOnClickListener(v -> {
            String u = inUrl.getText().toString().trim();
            String s = inSecret.getText().toString().trim();
            if (u.isEmpty() || s.isEmpty()) return;
            prefs.edit().putString("url", u).putString("secret", s).apply();
            showMain();
        });
    }

    private void showMain() {
        setContentView(R.layout.main);
        ring = findViewById(R.id.ring);
        ((Button) findViewById(R.id.stop)).setOnClickListener(v -> sendCommand("desktime_stop", null));
        ((Button) findViewById(R.id.f9)).setOnClickListener(v -> sendCommand("mouse_start", "\"minutes\":0"));
        ((Button) findViewById(R.id.gear)).setOnClickListener(v -> showSettings());
        loadDevices();
        startPolling();
    }

    private void showSettings() {
        stopPolling();
        setContentView(R.layout.settings);
        ((TextView) findViewById(R.id.title)).setText(es() ? "Ajustes" : "Settings");
        Button dev = findViewById(R.id.device);
        updateDeviceButton(dev);
        dev.setOnClickListener(v -> {
            if (deviceIds.isEmpty()) return;
            int i = deviceIds.indexOf(deviceId());
            i = (i + 1) % deviceIds.size();
            prefs.edit().putString("deviceId", deviceIds.get(i)).apply();
            updateDeviceButton(dev);
        });
        Button en = findViewById(R.id.lang_en), esb = findViewById(R.id.lang_es);
        en.setOnClickListener(v -> { prefs.edit().putString("lang", "en").apply(); showSettings(); });
        esb.setOnClickListener(v -> { prefs.edit().putString("lang", "es").apply(); showSettings(); });
        boolean isEs = es();
        en.setBackgroundResource(isEs ? android.R.color.transparent : R.drawable.w_seg_on);
        esb.setBackgroundResource(isEs ? R.drawable.w_seg_on : android.R.color.transparent);
        en.setTextColor(isEs ? 0xFF9AA3B2 : 0xFFC4B9FF);
        esb.setTextColor(isEs ? 0xFFC4B9FF : 0xFF9AA3B2);
        Button edit = findViewById(R.id.edit);
        edit.setText(es() ? "Editar conexión" : "Edit connection");
        edit.setOnClickListener(v -> showSetup());
        Button back = findViewById(R.id.back);
        back.setText(es() ? "Atrás" : "Back");
        back.setOnClickListener(v -> showMain());
    }

    private void updateDeviceButton(Button dev) {
        String label = es() ? "Dispositivo: " : "Device: ";
        int i = deviceIds.indexOf(deviceId());
        dev.setText(label + (i >= 0 ? deviceNames.get(i) : "—"));
    }

    // -------- polling --------
    private void startPolling() {
        stopPolling();
        poller = new Runnable() {
            @Override public void run() {
                refresh();
                ui.postDelayed(this, 5000);
            }
        };
        ui.post(poller);
    }

    private void stopPolling() {
        if (poller != null) ui.removeCallbacks(poller);
        poller = null;
    }

    private void refresh() {
        if (deviceId().isEmpty()) return;
        bgGet("devices/" + deviceId() + "/status", res -> {
            if (ring == null) return;
            try {
                if (res == null || res.equals("null")) { setPip(false); return; }
                JSONObject st = new JSONObject(res);
                boolean online = st.optBoolean("online", false)
                        && (System.currentTimeMillis() / 1000.0 - st.optLong("ts", 0) < 30);
                setPip(online);
                boolean running = st.optBoolean("desktime_running", false);
                JSONObject h = st.optJSONObject("hours");
                int pct = h != null ? (int) Math.round(h.optDouble("productivity", 0)) : 0;
                ring.setPct(pct);
                TextView total = findViewById(R.id.total);
                TextView sub = findViewById(R.id.sub);
                if (total != null) total.setText(h != null ? hms(h.optLong("desktimeTime", 0)) : "—");
                if (sub != null) {
                    String s = running ? (es() ? "En marcha" : "Running") : (es() ? "Detenido" : "Stopped");
                    if (h != null) s += " · " + pct + "%";
                    sub.setText(s);
                }
            } catch (Exception ignored) {}
        });
    }

    private void setPip(boolean online) {
        TextView pip = findViewById(R.id.pip);
        if (pip == null) return;
        String w = online ? (es() ? "en línea" : "online") : (es() ? "sin conexión" : "offline");
        pip.setText("PC " + w);
        pip.setTextColor(online ? 0xFF6EE7B7 : 0xFFFB7185);
    }

    private void loadDevices() {
        bgGet("devices", res -> {
            deviceIds.clear(); deviceNames.clear();
            try {
                if (res == null || res.equals("null")) return;
                JSONObject d = new JSONObject(res);
                Iterator<String> it = d.keys();
                while (it.hasNext()) {
                    String id = it.next();
                    deviceIds.add(id);
                    JSONObject st = d.optJSONObject(id) != null ? d.getJSONObject(id).optJSONObject("status") : null;
                    deviceNames.add(st != null ? st.optString("device_name", id) : id);
                }
                if (deviceId().isEmpty() && !deviceIds.isEmpty()) {
                    prefs.edit().putString("deviceId", deviceIds.get(0)).apply();
                }
            } catch (Exception ignored) {}
        });
    }

    private void sendCommand(String type, String extraArgs) {
        if (deviceId().isEmpty()) return;
        String args = extraArgs == null ? "{}" : "{" + extraArgs + "}";
        String body = "{\"type\":\"" + type + "\",\"args\":" + args + ",\"ts\":" + System.currentTimeMillis() + "}";
        bgPost("devices/" + deviceId() + "/commands", body);
    }

    // -------- helpers --------
    private static String hms(long sec) {
        long h = sec / 3600, m = (sec % 3600) / 60;
        return h + "h " + (m < 10 ? "0" : "") + m + "m";
    }

    private String fbUrl(String path) {
        try {
            return url().replaceAll("/+$", "") + "/" + path + ".json?auth="
                    + URLEncoder.encode(secret(), "UTF-8");
        } catch (Exception e) { return ""; }
    }

    private void bgGet(String path, Consumer<String> cb) {
        exec.execute(() -> {
            String res = http("GET", fbUrl(path), null);
            ui.post(() -> cb.accept(res));
        });
    }

    private void bgPost(String path, String body) {
        exec.execute(() -> http("POST", fbUrl(path), body));
    }

    private static String http(String method, String u, String body) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                OutputStream os = c.getOutputStream();
                os.write(body.getBytes("UTF-8"));
                os.close();
            }
            int code = c.getResponseCode();
            InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
            StringBuilder sb = new StringBuilder();
            if (is != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
