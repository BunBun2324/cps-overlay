package com.maket.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.core.app.NotificationCompat;

public class OverlayService extends Service {

    private static final String CHANNEL_ID = "cps_channel";
    private static final int NOTIF_ID = 1;
    private static final String ACTION_STOP = "ACTION_STOP";
    private static final String ACTION_RESET = "ACTION_RESET";
    private static final String ACTION_LOAD = "ACTION_LOAD";
    private static final String PREFS_NAME = "cps_data";
    private static final String KEY_PEAK = "peak_cps";
    private static final String KEY_TOTAL = "total_clicks";
    private static final long DOUBLE_CLICK_TIMEOUT = 300;

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private View overlayView;
    private TextView tvCps;
    private TextView tvPeak;
    private TextView tvTotal;

    private int totalClicks = 0;
    private float peakCps = 0;
    private final long[] tapTimes = new long[1000];
    private int tapHead = 0;
    private int tapCount = 0;

    private long lastClickTime = 0;
    private boolean isDragging = false;
    private int initialX, initialY;
    private float initialRawX, initialRawY;

    private final Handler handler = new Handler();

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            int clicksInWindow = 0;
            for (int i = 0; i < tapCount; i++) {
                if (now - tapTimes[i] <= 4000) {
                    clicksInWindow++;
                }
            }
            float cps = clicksInWindow / 4.0f;
            if (cps > peakCps) {
                peakCps = cps;
            }
            tvCps.setText(String.format("%.2f", cps));
            tvPeak.setText(String.format("%.2f", peakCps));
            tvTotal.setText(String.valueOf(totalClicks));
            handler.postDelayed(this, 100);
        }
    };

    private final Runnable autoSave = new Runnable() {
        @Override
        public void run() {
            saveData();
            handler.postDelayed(this, 10 * 60 * 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        loadData();
        createNotificationChannel();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, buildNotification());
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay, null);
        tvCps = overlayView.findViewById(R.id.tv_cps);
        tvPeak = overlayView.findViewById(R.id.tv_peak);
        tvTotal = overlayView.findViewById(R.id.tv_total);

        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 16;
        params.y = 60;
        windowManager.addView(overlayView, params);

        overlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                int maskedAction = e.getActionMasked();

                switch (maskedAction) {
                    case MotionEvent.ACTION_DOWN:
                        long clickTime = System.currentTimeMillis();
                        if (clickTime - lastClickTime < DOUBLE_CLICK_TIMEOUT) {
                            isDragging = true;
                            initialX = params.x;
                            initialY = params.y;
                            initialRawX = e.getRawX();
                            initialRawY = e.getRawY();
                        } else {
                            isDragging = false;
                        }
                        lastClickTime = clickTime;
                        registerTap();
                        break;

                    case MotionEvent.ACTION_POINTER_DOWN:
                        if (!isDragging) {
                            registerTap();
                        }
                        break;

                    case MotionEvent.ACTION_OUTSIDE:
                        registerTap();
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (isDragging) {
                            int deltaX = (int) (e.getRawX() - initialRawX);
                            int deltaY = (int) (e.getRawY() - initialRawY);
                            params.x = initialX + deltaX;
                            params.y = initialY + deltaY;
                            windowManager.updateViewLayout(overlayView, params);
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isDragging = false;
                        break;
                }
                return true;
            }
        });

        handler.post(ticker);
        handler.postDelayed(autoSave, 10 * 60 * 1000);
    }

    private void registerTap() {
        tapTimes[tapHead % 1000] = System.currentTimeMillis();
        tapHead++;
        if (tapCount < 1000) {
            tapCount++;
        }
        totalClicks++;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                saveData();
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }
            if (ACTION_RESET.equals(action)) {
                totalClicks = 0;
                peakCps = 0;
                tapHead = 0;
                tapCount = 0;
                saveData();
            }
            if (ACTION_LOAD.equals(action)) {
                loadData();
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
        if (overlayView != null) {
            windowManager.removeView(overlayView);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "CPS Overlay",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("CPS counter is running");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, OverlayService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent resetIntent = new Intent(this, OverlayService.class);
        resetIntent.setAction(ACTION_RESET);
        PendingIntent resetPending = PendingIntent.getService(
            this, 1, resetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CPS Overlay Running")
            .setContentText("Tracking your clicks")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_revert, "Reset", resetPending)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPending)
            .build();
    }

    private void saveData() {
        SharedPreferences.Editor editor =
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putFloat(KEY_PEAK, peakCps);
        editor.putInt(KEY_TOTAL, totalClicks);
        editor.apply();
    }

    private void loadData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        peakCps = prefs.getFloat(KEY_PEAK, 0f);
        totalClicks = prefs.getInt(KEY_TOTAL, 0);
    }
}
