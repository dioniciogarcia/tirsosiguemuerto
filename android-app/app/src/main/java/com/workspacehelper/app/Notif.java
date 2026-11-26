package com.workspacehelper.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

// Builds and posts the "PC offline" notification with its action buttons.
public class Notif {
    static final String CH = "wh_status";
    static final int ID = 100;

    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CH, "PC status", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    public static void offline(Context ctx) {
        ensureChannel(ctx);
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent open = PendingIntent.getActivity(
                ctx, 0, new Intent(ctx, MainActivity.class), flags);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CH)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("WorkspaceHelper")
                .setContentText("Your PC went offline")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .addAction(0, "Stop DeskTime", action(ctx, 1, "desktime_stop"))
                .addAction(0, "F9", action(ctx, 2, "mouse_start"));

        try {
            NotificationManagerCompat.from(ctx).notify(ID, b.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS not granted - nothing to do.
        }
    }

    public static void cancel(Context ctx) {
        try { NotificationManagerCompat.from(ctx).cancel(ID); } catch (Exception ignored) {}
    }

    static PendingIntent action(Context ctx, int rc, String cmd) {
        Intent i = new Intent(ctx, ActionReceiver.class).setAction(cmd).putExtra("cmd", cmd);
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        return PendingIntent.getBroadcast(ctx, rc, i, flags);
    }
}
