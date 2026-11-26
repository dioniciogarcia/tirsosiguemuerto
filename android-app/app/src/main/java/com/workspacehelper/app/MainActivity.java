package com.workspacehelper.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;

// Hosts the WebView app UI and exposes a small native bridge to it for
// things the web layer can't do: fingerprint confirmation and installing
// an app update.
public class MainActivity extends FragmentActivity {
    private WebView web;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("wh", MODE_PRIVATE);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportZoom(false);          // no pinch-to-zoom of the whole UI
        s.setBuiltInZoomControls(false);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        web.loadUrl("file:///android_asset/www/index.html");
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    // ----- bridge exposed to JavaScript as window.Android -----
    private class Bridge {
        @JavascriptInterface
        public void authenticate(final String action) {
            runOnUiThread(() -> showBiometric(action));
        }

        @JavascriptInterface
        public void installUpdate(final String url) {
            new Thread(() -> downloadAndInstall(url)).start();
        }

        @JavascriptInterface
        public String appVersion() {
            return BuildConfig.VERSION_NAME;
        }

        @JavascriptInterface
        public void shareText(final String text) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, "Share"));
            });
        }

        // The web layer pushes the connection here so the background worker and
        // notification buttons can reach Firebase while the app is closed.
        @JavascriptInterface
        public void saveConfig(String url, String secret, String deviceId) {
            prefs.edit()
                 .putString("url", url == null ? "" : url)
                 .putString("secret", secret == null ? "" : secret)
                 .putString("deviceId", deviceId == null ? "" : deviceId)
                 .apply();
        }

        @JavascriptInterface
        public void setNotifications(final boolean on) {
            prefs.edit().putBoolean("notif_enabled", on).apply();
            runOnUiThread(() -> {
                if (on) {
                    requestNotifPermission();
                    scheduleWorker();
                } else {
                    cancelWorker();
                }
            });
        }
    }

    private void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                   != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private void scheduleWorker() {
        PeriodicWorkRequest w = new PeriodicWorkRequest.Builder(
                StatusWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "status-check", ExistingPeriodicWorkPolicy.UPDATE, w);
    }

    private void cancelWorker() {
        WorkManager.getInstance(this).cancelUniqueWork("status-check");
    }

    private void updateProgress(final int pct) {
        runOnUiThread(() ->
            web.evaluateJavascript("window.onUpdateProgress&&window.onUpdateProgress(" + pct + ")", null));
    }

    private void callAuth(String action, boolean ok) {
        runOnUiThread(() ->
            web.evaluateJavascript("window.onAuth && window.onAuth('" + action + "'," + ok + ")", null));
    }

    private void showBiometric(final String action) {
        BiometricManager bm = BiometricManager.from(this);
        int allowed = BiometricManager.Authenticators.BIOMETRIC_WEAK
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        if (bm.canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            // No fingerprint/PIN set up on the phone - don't block the action.
            callAuth(action, true);
            return;
        }
        Executor ex = ContextCompat.getMainExecutor(this);
        BiometricPrompt bp = new BiometricPrompt(this, ex,
            new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                    callAuth(action, true);
                }
                @Override
                public void onAuthenticationError(int code, CharSequence msg) {
                    callAuth(action, false);
                }
            });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("WorkspaceHelper")
                .setSubtitle(action.equals("stop")
                        ? "Confirm closing DeskTime" : "Confirm opening DeskTime")
                .setAllowedAuthenticators(allowed)
                .build();
        bp.authenticate(info);
    }

    private void downloadAndInstall(String url) {
        try {
            File out = new File(getExternalFilesDir(null), "update.apk");
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(20000);
            c.setReadTimeout(60000);
            int total = c.getContentLength();
            InputStream in = c.getInputStream();
            FileOutputStream fo = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            long read = 0;
            int lastPct = -1;
            while ((n = in.read(buf)) > 0) {
                fo.write(buf, 0, n);
                read += n;
                if (total > 0) {
                    int pct = (int) (read * 100 / total);
                    if (pct != lastPct && pct % 2 == 0) {
                        lastPct = pct;
                        updateProgress(pct);
                    }
                }
            }
            fo.close();
            in.close();
            c.disconnect();
            updateProgress(100);

            // A real APK is multi-MB; anything tiny is an error page, not an APK.
            if (out.length() < 100000) {
                runOnUiThread(() ->
                    web.evaluateJavascript("window.onUpdateFailed && window.onUpdateFailed()", null));
                return;
            }

            // Android needs the user to allow "install unknown apps" for this app.
            if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                runOnUiThread(() -> {
                    web.evaluateJavascript("window.onUpdateNeedsPermission && window.onUpdateNeedsPermission()", null);
                    try {
                        startActivity(new Intent(
                                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception ignored) {}
                });
                return;
            }

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", out);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            runOnUiThread(() ->
                web.evaluateJavascript("window.onUpdateFailed && window.onUpdateFailed()", null));
        }
    }
}
