package com.workspacehelper.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

// Handles the Stop/F9 buttons on the offline notification: sends the command
// to Firebase, then dismisses the notification.
public class ActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context ctx, Intent intent) {
        final String cmd = intent.getStringExtra("cmd");
        if (cmd == null) return;
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                SharedPreferences p = ctx.getSharedPreferences("wh", Context.MODE_PRIVATE);
                String url = p.getString("url", "");
                String secret = p.getString("secret", "");
                String dev = p.getString("deviceId", "");
                if (!url.isEmpty() && !secret.isEmpty() && !dev.isEmpty()) {
                    String args = cmd.equals("mouse_start") ? "{\"minutes\":0}" : "{}";
                    String body = "{\"type\":\"" + cmd + "\",\"args\":" + args
                            + ",\"ts\":" + System.currentTimeMillis() + "}";
                    Net.post(Net.fbUrl(url, "devices/" + dev + "/commands", secret), body);
                }
            } catch (Exception ignored) {
            } finally {
                Notif.cancel(ctx);
                pr.finish();
            }
        }).start();
    }
}
