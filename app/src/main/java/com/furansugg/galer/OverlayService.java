package com.furansugg.galer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public final class OverlayService extends Service {
    private static final int TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    private WindowManager wm;
    private SharedPreferences prefs;
    private GuideView guide;
    private final View[] corners = new View[4];
    private View target, bubble;
    private float left, top, right, bottom, targetX, targetY;
    private boolean active = true;
    private int screenW, screenH, handleSize;

    @Override public void onCreate() {
        super.onCreate();
        startForegroundNow();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        screenW = getResources().getDisplayMetrics().widthPixels;
        screenH = getResources().getDisplayMetrics().heightPixels;
        handleSize = dp(38);
        prefs = getSharedPreferences("layout", MODE_PRIVATE);
        left = prefs.getFloat("left", screenW * .12f);
        top = prefs.getFloat("top", screenH * .22f);
        right = prefs.getFloat("right", screenW * .88f);
        bottom = prefs.getFloat("bottom", screenH * .78f);
        targetX = prefs.getFloat("x", screenW * .5f);
        targetY = prefs.getFloat("y", screenH * .5f);
        addGuide();
        for (int i = 0; i < 4; i++) addCorner(i);
        addTarget();
        addBubble();
        refresh();
    }

    private void startForegroundNow() {
        String id = "overlay";
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(id, "8Galer overlay", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, id)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("8Galer aktif")
                .setContentText("Tap tombol 8 untuk toggle overlay")
                .setContentIntent(open).setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(8, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(8, n);
    }

    private void addGuide() {
        guide = new GuideView();
        WindowManager.LayoutParams p = base(-1, -1);
        p.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        wm.addView(guide, p);
    }

    private void addCorner(final int index) {
        View v = new View(this);
        v.setBackground(circle(0xfff59e0b, 0xffffffff, 2));
        v.setOnTouchListener(new Drag((x, y) -> {
            float min = dp(100);
            if (index == 0 || index == 2) left = Math.min(x, right - min); else right = Math.max(x, left + min);
            if (index == 0 || index == 1) top = Math.min(y, bottom - min); else bottom = Math.max(y, top + min);
            clampTable(); refresh(); save();
        }, null));
        corners[index] = v;
        wm.addView(v, base(handleSize, handleSize));
    }

    private void addTarget() {
        target = new View(this);
        target.setBackground(circle(0x3300e5ff, 0xff00e5ff, 3));
        target.setOnTouchListener(new Drag((x, y) -> {
            targetX = clamp(x, 0, screenW); targetY = clamp(y, 0, screenH);
            refresh(); save();
        }, null));
        wm.addView(target, base(dp(54), dp(54)));
    }

    private void addBubble() {
        TextView v = new TextView(this);
        v.setText("8"); v.setTextColor(Color.WHITE); v.setTextSize(22); v.setGravity(Gravity.CENTER);
        v.setBackground(circle(0xff16a34a, Color.WHITE, 2));
        v.setElevation(dp(8));
        final float[] start = new float[2];
        v.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY; int originX, originY;
            @Override public boolean onTouch(View view, MotionEvent e) {
                WindowManager.LayoutParams p = (WindowManager.LayoutParams) view.getLayoutParams();
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = e.getRawX(); downY = e.getRawY(); originX = p.x; originY = p.y;
                    start[0] = downX; start[1] = downY; return true;
                }
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    p.x = originX + Math.round(e.getRawX() - downX);
                    p.y = originY + Math.round(e.getRawY() - downY);
                    wm.updateViewLayout(view, p); return true;
                }
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    if (Math.hypot(e.getRawX() - start[0], e.getRawY() - start[1]) < dp(8)) toggle();
                    return true;
                }
                return false;
            }
        });
        bubble = v;
        WindowManager.LayoutParams p = base(dp(52), dp(52)); p.x = dp(12); p.y = screenH / 3;
        wm.addView(v, p);
    }

    private void toggle() {
        active = !active;
        int visibility = active ? View.VISIBLE : View.GONE;
        guide.setVisibility(visibility); target.setVisibility(visibility);
        for (View corner : corners) corner.setVisibility(visibility);
        bubble.setBackground(circle(active ? 0xff16a34a : 0xff475569, Color.WHITE, 2));
    }

    private void refresh() {
        position(target, targetX, targetY);
        position(corners[0], left, top); position(corners[1], right, top);
        position(corners[2], left, bottom); position(corners[3], right, bottom);
        guide.invalidate();
    }

    private void position(View view, float x, float y) {
        WindowManager.LayoutParams p = (WindowManager.LayoutParams) view.getLayoutParams();
        p.x = Math.round(x - p.width / 2f); p.y = Math.round(y - p.height / 2f);
        wm.updateViewLayout(view, p);
    }

    private void clampTable() {
        left = clamp(left, 0, screenW); right = clamp(right, 0, screenW);
        top = clamp(top, 0, screenH); bottom = clamp(bottom, 0, screenH);
    }

    private void save() {
        prefs.edit().putFloat("left", left).putFloat("top", top).putFloat("right", right)
                .putFloat("bottom", bottom).putFloat("x", targetX).putFloat("y", targetY).apply();
    }

    private WindowManager.LayoutParams base(int width, int height) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(width, height, TYPE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.LEFT;
        return p;
    }

    private android.graphics.drawable.Drawable circle(int fill, int stroke, int strokeDp) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL); d.setColor(fill);
        d.setStroke(dp(strokeDp), stroke); return d;
    }

    private final class GuideView extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
        GuideView() {
            super(OverlayService.this);
            line.setColor(0xcc00e5ff); line.setStrokeWidth(dp(1));
            frame.setColor(0xfff59e0b); frame.setStyle(Paint.Style.STROKE); frame.setStrokeWidth(dp(2));
        }
        @Override protected void onDraw(Canvas c) {
            c.drawRect(left, top, right, bottom, frame);
            float midX = (left + right) / 2f;
            float[][] pockets = {{left,top},{midX,top},{right,top},{left,bottom},{midX,bottom},{right,bottom}};
            for (float[] pocket : pockets) {
                c.drawLine(targetX, targetY, pocket[0], pocket[1], line);
                c.drawCircle(pocket[0], pocket[1], dp(7), frame);
            }
        }
        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            if (oldW > 0 && oldH > 0) {
                float sx = w / (float) oldW, sy = h / (float) oldH;
                left *= sx; right *= sx; targetX *= sx;
                top *= sy; bottom *= sy; targetY *= sy;
            }
            screenW = w; screenH = h;
            post(() -> { refresh(); save(); });
        }
    }

    private interface Move { void to(float x, float y); }
    private final class Drag implements View.OnTouchListener {
        private final Move move; private final Runnable click;
        Drag(Move move, Runnable click) { this.move = move; this.click = click; }
        @Override public boolean onTouch(View v, MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
                move.to(e.getRawX(), e.getRawY()); return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP) { if (click != null) click.run(); return true; }
            return false;
        }
    }

    private float clamp(float n, float min, float max) { return Math.max(min, Math.min(max, n)); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override public int onStartCommand(Intent intent, int flags, int id) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        View[] views = {guide, target, bubble, corners[0], corners[1], corners[2], corners[3]};
        for (View v : views) if (v != null && v.isAttachedToWindow()) wm.removeView(v);
        super.onDestroy();
    }
}
