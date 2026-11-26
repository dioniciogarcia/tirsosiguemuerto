package com.workspacehelper.watch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

// Draws the circular DeskTime progress ring behind the watch content.
public class RingView extends View {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float pct = 0f;

    public RingView(Context c, AttributeSet a) {
        super(c, a);
        float w = dp(9);
        bg.setStyle(Paint.Style.STROKE);
        bg.setStrokeWidth(w);
        bg.setColor(Color.parseColor("#262B36"));
        fg.setStyle(Paint.Style.STROKE);
        fg.setStrokeWidth(w);
        fg.setStrokeCap(Paint.Cap.ROUND);
        fg.setColor(Color.parseColor("#34D399"));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public void setPct(float p) {
        pct = Math.max(0f, Math.min(100f, p));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float pad = fg.getStrokeWidth() / 2f + dp(2);
        float d = Math.min(getWidth(), getHeight());
        float left = (getWidth() - d) / 2f + pad;
        float top = (getHeight() - d) / 2f + pad;
        RectF r = new RectF(left, top, left + d - 2 * pad, top + d - 2 * pad);
        canvas.drawArc(r, 0, 360, false, bg);
        if (pct > 0) canvas.drawArc(r, -90, pct * 3.6f, false, fg);
    }
}
