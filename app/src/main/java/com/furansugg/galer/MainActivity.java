package com.furansugg.galer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        int p = dp(24);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(p, p, p, p);
        box.setBackgroundColor(Color.rgb(15, 23, 42));

        TextView title = text("8Galer", 30, Color.WHITE);
        status = text("Izinkan overlay, lalu aktifkan.", 16, 0xffcbd5e1);
        Button start = new Button(this);
        start.setText("AKTIFKAN OVERLAY");
        start.setOnClickListener(v -> startOverlay());
        Button stop = new Button(this);
        stop.setText("MATIKAN OVERLAY");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            status.setText("Overlay dimatikan.");
        });
        box.addView(title, params(-1, -2, 0, 20));
        box.addView(status, params(-1, -2, 0, 28));
        box.addView(start, params(-1, -2, 0, 12));
        box.addView(stop, params(-1, -2, 0, 0));
        setContentView(box);
    }

    @Override protected void onResume() {
        super.onResume();
        if (Settings.canDrawOverlays(this)) status.setText("Siap. Aktifkan overlay, lalu buka game.");
    }

    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != getPackageManager().PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 8);
        }
        startForegroundService(new Intent(this, OverlayService.class));
        status.setText("Aktif. Tombol 8 mengatur tampil/sembunyi.");
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private LinearLayout.LayoutParams params(int w, int h, int bottom, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(0, top, 0, bottom);
        return p;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
