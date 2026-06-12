package com.maket.overlay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int REQ_NOTIF = 5678;
    private static final int REQ_OVERLAY = 1234;
    private static final int REQ_BACKUP = 9999;
    private static final int REQ_RESTORE = 8888;
    private static final String PREFS_NAME = "cps_data";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(64, 64, 64, 64);

        Button btnStart = new Button(this);
        btnStart.setText("Start Overlay Service");
        btnStart.setOnClickListener(v -> checkPermissions());

        Button btnBackup = new Button(this);
        btnBackup.setText("Backup Stats (Export)");
        btnBackup.setOnClickListener(v -> triggerBackup());

        Button btnRestore = new Button(this);
        btnRestore.setText("Restore Stats (Import)");
        btnRestore.setOnClickListener(v -> triggerRestore());

        layout.addView(btnStart);
        layout.addView(btnBackup);
        layout.addView(btnRestore);
        setContentView(layout);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                return;
            }
        }
        checkOverlayPermission();
    }

    private void checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, REQ_OVERLAY);
        } else {
            safeStartService(null);
        }
    }

    private void safeStartService(String customAction) {
        Intent intent = new Intent(this, OverlayService.class);
        if (customAction != null) {
            intent.setAction(customAction);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void triggerBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "cps_backup.json");
        startActivityForResult(intent, REQ_BACKUP);
    }

    private void triggerRestore() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQ_RESTORE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_NOTIF) {
            checkOverlayPermission();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                safeStartService(null);
            }
        } else if (req == REQ_BACKUP && res == RESULT_OK && data != null) {
            executeExport(data.getData());
        } else if (req == REQ_RESTORE && res == RESULT_OK && data != null) {
            executeImport(data.getData());
        }
    }

    private void executeExport(Uri uri) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            JSONObject json = new JSONObject();
            json.put("peak_cps", (double) prefs.getFloat("peak_cps", 0f));
            json.put("total_clicks", prefs.getInt("total_clicks", 0));

            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) {
                os.write(json.toString(4).getBytes());
                os.close();
                Toast.makeText(this, "Backup Saved!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Backup Failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void executeImport(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is != null) {
                byte[] bytes = new byte[is.available()];
                is.read(bytes);
                is.close();

                JSONObject json = new JSONObject(new String(bytes));
                float peakCps = (float) json.getDouble("peak_cps");
                int totalClicks = json.getInt("total_clicks");

                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                editor.putFloat("peak_cps", peakCps);
                editor.putInt("total_clicks", totalClicks);
                editor.apply();

                Toast.makeText(this, "Stats Restored!", Toast.LENGTH_SHORT).show();
                safeStartService("ACTION_LOAD");
            }
        } catch (Exception e) {
            Toast.makeText(this, "Restore Failed", Toast.LENGTH_SHORT).show();
        }
    }
}
