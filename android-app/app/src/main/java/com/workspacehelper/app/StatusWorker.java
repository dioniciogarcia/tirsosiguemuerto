package com.workspacehelper.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

// Runs in the background (every ~15 min, even when the app is closed) and
// posts a notification when the PC transitions from online to offline.
public class StatusWorker extends Worker {
    public StatusWorker(@NonNull Context c, @NonNull WorkerParameters p) {
        super(c, p);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        SharedPreferences pr = ctx.getSharedPreferences("wh", Context.MODE_PRIVATE);
        if (!pr.getBoolean("notif_enabled", false)) return Result.success();
        String url = pr.getString("url", "");
        String secret = pr.getString("secret", "");
        String dev = pr.getString("deviceId", "");
        if (url.isEmpty() || secret.isEmpty() || dev.isEmpty()) return Result.success();

        String res = Net.get(Net.fbUrl(url, "devices/" + dev + "/status", secret));
        boolean online = false;
        try {
            if (res != null && !res.equals("null")) {
                JSONObject st = new JSONObject(res);
                online = st.optBoolean("online", false)
                        && (System.currentTimeMillis() / 1000.0 - st.optLong("ts", 0) < 120);
            }
        } catch (Exception ignored) {}

        boolean last = pr.getBoolean("last_online", true);
        if (last && !online) {
            Notif.offline(ctx);
        }
        pr.edit().putBoolean("last_online", online).apply();
        return Result.success();
    }
}
