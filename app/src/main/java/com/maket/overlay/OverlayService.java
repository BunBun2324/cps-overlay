package com.maket.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
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
    private static final String PREFS_NAME = "cps_data";
    private static final String KEY_PEAK = "peak_cps";
    private static final String KEY_TOTAL = "total_clicks";

    private WindowManager windowManager;
    private View overlayView;
    private TextView tvCps;
    private TextView tvPeak;
    private TextView tvTotal;

    private int totalClicks = 0;
    private float peakCps = 0;
    private final long[] tapTimes = new long[1000];
    private int tapHead = 0;
    private int tapCount = 0;

    private final Handler handler = new Handler();

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            int cps = 0;
            for (int i = 0; i < tapCount; i++) {
                if (now - tapTimes[i] <= 1000) {
                    cps++;
                }
            }
            if (cps > peakCps) {
                peakCps = cps;
            }
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
            handler.postDelayed(this, 10 * 60 * 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        loadData();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay, null);
        tvCps = overlayView.findViewById(R.id.tv_cps);
        tvPeak = overlayView.findViewById(R.id.tv_peak);
        tvTotal = overlayView.findViewById(R.id.tv_total);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
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
                int action = e.getAction();
                int maskedAction = e.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN
                        || maskedAction == MotionEvent.ACTION_OUTSIDE) {
                    tapTimes[tapHead % 1000] = System.currentTimeMillis();
                    tapHead++;
                    if (tapCount < 1000) {
                        tapCount++;
                    }
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
                "CPS Overlay",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("CPS counter overlay is running");
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
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putFloat(KEY_PEAK, peakCps);
        editor.putInt(KEY_TOTAL, totalClicks);
        editor.apply();
    }

    private void loadData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        peakCps = prefs.getFloat(KEY_PEAK, 0f);
        totalClicks = prefs.getInt(KEY_TOTAL, 0);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}            @Override
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
                "CPS Overlay",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("CPS counter overlay is running");
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
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putFloat(KEY_PEAK, peakCps);
        editor.putInt(KEY_TOTAL, totalClicks);
        editor.apply();
    }

    private void loadData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        peakCps = prefs.getFloat(KEY_PEAK, 0f);
        totalClicks = prefs.getInt(KEY_TOTAL, 0);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}                    tapHead++;
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
