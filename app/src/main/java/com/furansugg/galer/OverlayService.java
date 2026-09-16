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
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public final class OverlayService extends Service {
    private static final int TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    private static final int[] COLORS = {0xff00e5ff, 0xffffffff, 0xffffd600, 0xffff4081, 0xff69f0ae, 0xffff6d00};
    private static final int[] CORNERS = {0, 2, 3, 5};
    private final float[] pocketX = new float[6], pocketY = new float[6];
    private final View[] pocketHandles = new View[4];
    private WindowManager wm;
    private SharedPreferences prefs;
    private GuideView guide;
    private View target, targetB, bubble, panel;
    private Button modeButton, colorButton;
    private TextView sizeLabel;
    private float targetX, targetY, targetBX, targetBY;
    private boolean active = true, editing;
    private int screenW, screenH, colorIndex, mode, diameterDp;

    @Override public void onCreate() {
        super.onCreate();
        startForegroundNow();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        screenW = getResources().getDisplayMetrics().widthPixels;
        screenH = getResources().getDisplayMetrics().heightPixels;
        prefs = getSharedPreferences("layout", MODE_PRIVATE);
        loadPositions();
        addGuide();
        for (int i = 0; i < 4; i++) addPocketHandle(i);
        addTarget();
        addTargetB();
        addPanel();
        addBubble();
        refresh();
        setEditing(false);
    }

    private void loadPositions() {
        float left = screenW * .18f, right = screenW * .82f;
        float top = screenH * .20f, bottom = screenH * .80f, middle = (left + right) / 2f;
        float[] defaultsX = {left, middle, right, left, middle, right};
        float[] defaultsY = {top, top, top, bottom, bottom, bottom};
        for (int i = 0; i < 6; i++) {
            pocketX[i] = prefs.getFloat("px" + i, defaultsX[i]);
            pocketY[i] = prefs.getFloat("py" + i, defaultsY[i]);
        }
        updateMiddlePockets();
        targetX = prefs.getFloat("x", screenW * .5f);
        targetY = prefs.getFloat("y", screenH * .5f);
        targetBX = prefs.getFloat("bx", screenW * .7f);
        targetBY = prefs.getFloat("by", screenH * .5f);
        colorIndex = prefs.getInt("color", 0) % COLORS.length;
        mode = prefs.getInt("mode", 0);
        diameterDp = prefs.getInt("diameter", 38);
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
                .setContentText("Tap 8: toggle · tahan 8: pengaturan")
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

    private void addPocketHandle(final int handleIndex) {
        final int index = CORNERS[handleIndex];
        View v = new View(this);
        v.setBackground(circle(Color.TRANSPARENT, 0xfffbbf24, 2));
        v.setOnTouchListener((view, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
                pocketX[index] = clamp(e.getRawX(), 0, screenW);
                pocketY[index] = clamp(e.getRawY(), 0, screenH);
                updateMiddlePockets();
                refresh();
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP) { save(); return true; }
            return false;
        });
        pocketHandles[handleIndex] = v;
        wm.addView(v, base(dp(38), dp(38)));
    }

    private void addTarget() {
        target = new View(this);
        target.setBackgroundColor(Color.TRANSPARENT);
        target.setOnTouchListener((view, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
                targetX = clamp(e.getRawX(), 0, screenW);
                targetY = clamp(e.getRawY(), 0, screenH);
                refresh();
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP) { save(); return true; }
            return false;
        });
        wm.addView(target, base(dp(56), dp(56)));
    }

    private void addTargetB() {
        targetB = new View(this);
        targetB.setBackgroundColor(Color.TRANSPARENT);
        targetB.setOnTouchListener((view, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
                targetBX = clamp(e.getRawX(), 0, screenW);
                targetBY = clamp(e.getRawY(), 0, screenH);
                refresh();
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP) { save(); return true; }
            return false;
        });
        wm.addView(targetB, base(dp(56), dp(56)));
    }

    private void addPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(5), dp(8), dp(7));
        box.setBackgroundColor(0xdd0f172a);

        TextView drag = new TextView(this);
        drag.setText("8Galer  ·  geser panel");
        drag.setTextColor(0xffcbd5e1);
        drag.setTextSize(12);
        drag.setGravity(Gravity.CENTER);
        makeDraggable(drag, box);
        box.addView(drag, new LinearLayout.LayoutParams(-1, dp(28)));

        LinearLayout actions = new LinearLayout(this);
        modeButton = button("MODE");
        colorButton = button("WARNA");
        modeButton.setOnClickListener(v -> { mode = 1 - mode; applyMode(); save(); });
        colorButton.setOnClickListener(v -> cycleColor());
        actions.addView(modeButton, new LinearLayout.LayoutParams(0, dp(40), 1));
        actions.addView(colorButton, new LinearLayout.LayoutParams(0, dp(40), 1));
        box.addView(actions, new LinearLayout.LayoutParams(-1, dp(40)));

        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        Button minus = button("−"), plus = button("+");
        SeekBar slider = new SeekBar(this);
        slider.setMax(72);
        slider.setProgress(diameterDp - 8);
        sizeLabel = new TextView(this);
        sizeLabel.setTextColor(Color.WHITE);
        sizeLabel.setTextSize(12);
        sizeLabel.setGravity(Gravity.CENTER);
        minus.setOnClickListener(v -> setDiameter(diameterDp - 1, slider));
        plus.setOnClickListener(v -> setDiameter(diameterDp + 1, slider));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) setDiameter(progress + 8, null);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) { save(); }
        });
        sizeRow.addView(minus, new LinearLayout.LayoutParams(dp(44), dp(40)));
        sizeRow.addView(slider, new LinearLayout.LayoutParams(0, dp(40), 1));
        sizeRow.addView(plus, new LinearLayout.LayoutParams(dp(44), dp(40)));
        sizeRow.addView(sizeLabel, new LinearLayout.LayoutParams(dp(58), dp(40)));
        box.addView(sizeRow, new LinearLayout.LayoutParams(-1, dp(42)));

        panel = box;
        WindowManager.LayoutParams p = base(dp(300), dp(118));
        p.x = Math.max(0, (screenW - dp(300)) / 2); p.y = dp(12);
        wm.addView(box, p);
        updatePanel();
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text); b.setTextSize(11); b.setTextColor(Color.WHITE);
        b.setBackgroundColor(0xff334155);
        return b;
    }

    private void setDiameter(int value, SeekBar slider) {
        diameterDp = Math.max(8, Math.min(80, value));
        if (slider != null) slider.setProgress(diameterDp - 8);
        sizeLabel.setText(diameterDp + " dp");
        guide.invalidate();
        save();
    }

    private void makeDraggable(View handle, View moved) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY; int originX, originY;
            @Override public boolean onTouch(View v, MotionEvent e) {
                WindowManager.LayoutParams p = (WindowManager.LayoutParams) moved.getLayoutParams();
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = e.getRawX(); downY = e.getRawY(); originX = p.x; originY = p.y; return true;
                }
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    p.x = Math.round(clamp(originX + e.getRawX() - downX, 0, screenW - p.width));
                    p.y = Math.round(clamp(originY + e.getRawY() - downY, 0, screenH - p.height));
                    wm.updateViewLayout(moved, p); return true;
                }
                return e.getAction() == MotionEvent.ACTION_UP;
            }
        });
    }

    private void addBubble() {
        TextView v = new TextView(this);
        v.setText("8");
        v.setTextColor(Color.WHITE);
        v.setTextSize(18);
        v.setGravity(Gravity.CENTER);
        v.setBackground(circle(0xdd16a34a, 0xddffffff, 1));
        v.setElevation(dp(5));
        Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] longPressed = {false};
        final Runnable longPress = () -> {
            longPressed[0] = true;
            if (!active) setActive(true);
            setEditing(!editing);
        };
        v.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int originX, originY;
            boolean moved;
            @Override public boolean onTouch(View view, MotionEvent e) {
                WindowManager.LayoutParams p = (WindowManager.LayoutParams) view.getLayoutParams();
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = e.getRawX(); downY = e.getRawY(); originX = p.x; originY = p.y;
                    moved = false; longPressed[0] = false;
                    handler.postDelayed(longPress, 550);
                    return true;
                }
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (Math.hypot(dx, dy) > dp(7)) {
                        moved = true; handler.removeCallbacks(longPress);
                        p.x = Math.round(clamp(originX + dx, 0, screenW - p.width));
                        p.y = Math.round(clamp(originY + dy, 0, screenH - p.height));
                        wm.updateViewLayout(view, p);
                    }
                    return true;
                }
                if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                    handler.removeCallbacks(longPress);
                    if (e.getAction() == MotionEvent.ACTION_UP && !moved && !longPressed[0]) {
                        setActive(!active);
                    }
                    return true;
                }
                return false;
            }
        });
        bubble = v;
        WindowManager.LayoutParams p = base(dp(44), dp(44));
        p.x = dp(8); p.y = screenH / 3;
        wm.addView(v, p);
    }

    private void setActive(boolean enabled) {
        active = enabled;
        if (!active) editing = false;
        guide.setVisibility(active ? View.VISIBLE : View.GONE);
        target.setVisibility(active ? View.VISIBLE : View.GONE);
        targetB.setVisibility(active && mode == 1 ? View.VISIBLE : View.GONE);
        panel.setVisibility(active && editing ? View.VISIBLE : View.GONE);
        for (View handle : pocketHandles) handle.setVisibility(active && editing ? View.VISIBLE : View.GONE);
        applyMode();
        updateBubble();
    }

    private void setEditing(boolean enabled) {
        editing = enabled;
        panel.setVisibility(active && editing ? View.VISIBLE : View.GONE);
        applyMode();
        guide.invalidate();
        updateBubble();
    }

    private void updateBubble() {
        ((TextView) bubble).setText(editing ? "✓" : mode == 1 ? "Ⅱ" : "8");
        int color = !active ? 0xaa475569 : editing ? COLORS[colorIndex] : 0xdd16a34a;
        bubble.setBackground(circle(color, 0xddffffff, 1));
    }

    private void applyMode() {
        if (targetB == null || panel == null) return;
        targetB.setVisibility(active && mode == 1 ? View.VISIBLE : View.GONE);
        for (View handle : pocketHandles) handle.setVisibility(active && editing && mode == 0 ? View.VISIBLE : View.GONE);
        updatePanel();
        updateBubble();
        guide.invalidate();
    }

    private void updatePanel() {
        if (modeButton == null) return;
        modeButton.setText(mode == 0 ? "MODE: LUBANG" : "MODE: KORIDOR");
        colorButton.setText("WARNA");
        colorButton.setTextColor(COLORS[colorIndex]);
        sizeLabel.setText(diameterDp + " dp");
    }

    private void cycleColor() {
        colorIndex = (colorIndex + 1) % COLORS.length;
        guide.setLineColor(COLORS[colorIndex]);
        updateBubble();
        updatePanel();
        save();
    }

    private void updateMiddlePockets() {
        pocketX[1] = (pocketX[0] + pocketX[2]) / 2f;
        pocketY[1] = (pocketY[0] + pocketY[2]) / 2f;
        pocketX[4] = (pocketX[3] + pocketX[5]) / 2f;
        pocketY[4] = (pocketY[3] + pocketY[5]) / 2f;
    }

    private void refresh() {
        position(target, targetX, targetY);
        position(targetB, targetBX, targetBY);
        for (int i = 0; i < 4; i++) position(pocketHandles[i], pocketX[CORNERS[i]], pocketY[CORNERS[i]]);
        guide.invalidate();
    }

    private void position(View view, float x, float y) {
        WindowManager.LayoutParams p = (WindowManager.LayoutParams) view.getLayoutParams();
        p.x = Math.round(x - p.width / 2f);
        p.y = Math.round(y - p.height / 2f);
        wm.updateViewLayout(view, p);
    }

    private void save() {
        SharedPreferences.Editor e = prefs.edit().putFloat("x", targetX).putFloat("y", targetY)
                .putFloat("bx", targetBX).putFloat("by", targetBY)
                .putInt("color", colorIndex).putInt("mode", mode).putInt("diameter", diameterDp);
        for (int i = 0; i < 6; i++) e.putFloat("px" + i, pocketX[i]).putFloat("py" + i, pocketY[i]);
        e.apply();
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
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setColor(fill);
        d.setStroke(dp(strokeDp), stroke);
        return d;
    }

    private final class GuideView extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint marker = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
        GuideView() {
            super(OverlayService.this);
            setLineColor(COLORS[colorIndex]);
            line.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density));
            marker.setColor(0xaafbbf24);
            marker.setStyle(Paint.Style.STROKE);
            marker.setStrokeWidth(dp(1));
            frame.setColor(0x88fbbf24);
            frame.setStyle(Paint.Style.STROKE);
            frame.setStrokeWidth(dp(1));
            frame.setPathEffect(new DashPathEffect(new float[]{dp(8), dp(6)}, 0));
        }
        void setLineColor(int color) {
            line.setColor(color);
            invalidate();
        }
        @Override protected void onDraw(Canvas c) {
            float radius = dp(diameterDp) / 2f;
            if (mode == 1) {
                float dx = targetBX - targetX, dy = targetBY - targetY;
                float length = (float) Math.hypot(dx, dy);
                if (length > 1f) {
                    float ox = -dy / length * radius, oy = dx / length * radius;
                    c.drawLine(targetX + ox, targetY + oy, targetBX + ox, targetBY + oy, line);
                    c.drawLine(targetX - ox, targetY - oy, targetBX - ox, targetBY - oy, line);
                    c.drawLine(targetX, targetY, targetBX, targetBY, line);
                }
                c.drawCircle(targetX, targetY, radius, line);
                c.drawCircle(targetBX, targetBY, radius, line);
                return;
            }
            c.drawCircle(targetX, targetY, dp(19), line);
            for (int i = 0; i < 6; i++) {
                c.drawLine(targetX, targetY, pocketX[i], pocketY[i], line);
                if (editing) c.drawCircle(pocketX[i], pocketY[i], dp(12), marker);
            }
            if (editing) {
                c.drawLine(pocketX[0], pocketY[0], pocketX[1], pocketY[1], frame);
                c.drawLine(pocketX[1], pocketY[1], pocketX[2], pocketY[2], frame);
                c.drawLine(pocketX[2], pocketY[2], pocketX[5], pocketY[5], frame);
                c.drawLine(pocketX[5], pocketY[5], pocketX[4], pocketY[4], frame);
                c.drawLine(pocketX[4], pocketY[4], pocketX[3], pocketY[3], frame);
                c.drawLine(pocketX[3], pocketY[3], pocketX[0], pocketY[0], frame);
            }
        }
        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            if (oldW > 0 && oldH > 0) {
                float sx = w / (float) oldW, sy = h / (float) oldH;
                for (int i = 0; i < 6; i++) { pocketX[i] *= sx; pocketY[i] *= sy; }
                targetX *= sx; targetY *= sy;
                targetBX *= sx; targetBY *= sy;
                updateMiddlePockets();
            }
            screenW = w; screenH = h;
            post(() -> { refresh(); save(); });
        }
    }

    private float clamp(float n, float min, float max) { return Math.max(min, Math.min(max, n)); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override public int onStartCommand(Intent intent, int flags, int id) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        if (guide != null && guide.isAttachedToWindow()) wm.removeView(guide);
        if (target != null && target.isAttachedToWindow()) wm.removeView(target);
        if (targetB != null && targetB.isAttachedToWindow()) wm.removeView(targetB);
        if (bubble != null && bubble.isAttachedToWindow()) wm.removeView(bubble);
        if (panel != null && panel.isAttachedToWindow()) wm.removeView(panel);
        for (View v : pocketHandles) if (v != null && v.isAttachedToWindow()) wm.removeView(v);
        super.onDestroy();
    }
}
