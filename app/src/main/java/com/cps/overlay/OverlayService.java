package com.cps.overlay;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import java.util.LinkedList;

public class OverlayService extends Service {

    private WindowManager windowManager;
    private View overlayView;
    private TextView tvCps, tvPeak, tvTotal;

    private final LinkedList<Long> tapTimes = new LinkedList<>();
    private int totalClicks = 0;
    private float peakCps = 0;
    private float sumCps = 0;
    private int secondsCount = 0;

    private final Handler handler = new Handler();
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            // remove taps older than 1 second
            while (!tapTimes.isEmpty() && now - tapTimes.getFirst() > 1000) {
                tapTimes.removeFirst();
            }
            float cps = tapTimes.size();
            if (cps > peakCps) peakCps = cps;
            sumCps += cps;
            secondsCount++;

            tvCps.setText(String.format("%.1f", cps));
            tvPeak.setText(String.format("%.1f", peakCps));
            tvTotal.setText(String.valueOf(totalClicks));

            handler.postDelayed(this, 100);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay, null);
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
        params.y = 16;

        windowManager.addView(overlayView, params);

        // count every touch on screen including game touches
        overlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    tapTimes.add(System.currentTimeMillis());
                    totalClicks++;
                }
                return false; // pass touch through to game
            }
        });

        handler.post(ticker);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(ticker);
        if (overlayView != null) windowManager.removeView(overlayView);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
