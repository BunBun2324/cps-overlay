package com.maket.overlay;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import androidx.core.app.NotificationCompat;
import java.io.*;

public class OverlayService extends Service {

    private static final String CHANNEL_ID = "cps_overlay_channel";
    private static final int NOTIF_ID = 1;
    private static final String ACTION_STOP = "ACTION_STOP";
    private static final String ACTION_RESET = "ACTION_RESET";
    private static final String PREFS_NAME = "cps_data";
    private static final String KEY_PEAK = "peak_cps";
    private static final String KEY_TOTAL = "total_clicks";

    private WindowManager windowManager;
    private View overlayView;
    private TextView tvCps, tvPeak, tvTotal;

    private int totalClicks = 0;
    private float peakCps = 0;
    private long[] tapTimes = new long[1000];
    private int tapHead = 0;
    private int tapCount = 0;

    private final Handler handler = new Handler();

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            int cps = 0;
            for (int i = 0; i < tapCount; i++) {
                if (now - tapTimes[i] <= 1000) cps++;
            }
            if (cps > peakCps) peakCps = cps;

            tvCps.setText(String.format("%.1f", (float) cps));
            tvPeak.setText(String.format("%.1f", peakCps));
            tvTotal.setText(String.valueOf(totalClicks));

            handler.postDelayed(this, 100);
        }
    };

    private final Runnable autoSave = new Runnable() {
        @Override
        public void run() {
            saveData();
            handler.postDelayed(this, 10 * 60 * 1000); // every 10 mins
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        loadData();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = getLayoutInflater().inflate(R.layout.overlay, null);
        tvCps   = overlayView.findViewById(R.id.tv_cps);
        tvPeak  = overlayView.findViewById(R.id.tv_peak);
        tvTotal = overlayView.findViewById(R.id.tv_total);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 16;
        params.y = 60;

        windowManager.addView(overlayView, params);

        overlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN ||
                    e.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                    tapTimes[tapHead % 1000] = System.currentTimeMillis();
                    tapHead++;
                    if (tapCount < 1000) tapCount++;
                    totalClicks++;
                }
                return false;
            }
        });

        handler.post(ticker);
        handler.postDelayed(autoSave, 10 * 60 * 1000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_STOP.equals(intent.getAction())) {
                saveData();
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }
            if (ACTION_RESET.equals(intent.getAction())) {
                totalClicks = 0;
                peakCps = 0;
                tapHead = 0;
                tapCount = 0;
                saveData();
                return START_STICKY;
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        saveData();
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(autoSave);
        if (overlayView != null) windowManager.removeView(overlayView);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "CP        if (overlayView != null) windowManager.removeView(overlayView);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
