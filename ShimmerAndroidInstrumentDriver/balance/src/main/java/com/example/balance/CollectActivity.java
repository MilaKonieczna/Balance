package com.example.balance;

import static com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog.EXTRA_DEVICE_ADDRESS;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.*;

import com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog;
import com.shimmerresearch.android.manager.ShimmerBluetoothManagerAndroid;
import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.exceptions.ShimmerException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class CollectActivity extends AppCompatActivity implements ForegroundService.ServiceHandler {

    private EditText patientET;
    private CheckBox xAxisCB, yAxisCB, zAxisCB, xGyroCB, yGyroCB, zGyroCB, emgCB;
    private Button connectButton, startButton, visualizeButton;
    private TextView clock;
    private ScrollView scrollView;

    ForegroundService mService;
    boolean mBound = false;
    boolean isConnected = false;
    int timeSeconds = 10;
    Handler timerHandler = new Handler();
    Runnable timerRunnable;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            ForegroundService.LocalBinder binder = (ForegroundService.LocalBinder) service;
            mService = binder.getService();
            mService.setHandler(CollectActivity.this);
            mBound = true;
            syncUIWithService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        patientET = findViewById(R.id.patient);
        xAxisCB = findViewById(R.id.Xaxis); yAxisCB = findViewById(R.id.Yaxis); zAxisCB = findViewById(R.id.Zaxis);
        xGyroCB = findViewById(R.id.Xgyro); yGyroCB = findViewById(R.id.Ygyro); zGyroCB = findViewById(R.id.Zgyro);
        emgCB = findViewById(R.id.EMG);
        connectButton = findViewById(R.id.connect);
        startButton = findViewById(R.id.start);
        clock = findViewById(R.id.clock);
        visualizeButton = findViewById(R.id.visualize);
        scrollView = findViewById(R.id.scroll);

        requestBlePermissions();

        visualizeButton.setEnabled(true);
        Intent intent = new Intent(this, ForegroundService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void requestBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS
            }, 101);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
        }
    }

    private void syncUIWithService() {
        if (mService != null && mService.shimmerDevice != null) {
            onStateChanged(ShimmerBluetooth.BT_STATE.CONNECTED, mService.shimmerDevice.getMacId());
        }
    }

    @Override
    public void onStateChanged(ShimmerBluetooth.BT_STATE state, String mac) {
        runOnUiThread(() -> {
            if (state == ShimmerBluetooth.BT_STATE.CONNECTED) {
                isConnected = true;
                connectButton.setText("Disconnect");
                connectButton.setBackgroundResource(R.drawable.disconnect_drawable);
                visualizeButton.setEnabled(!mService.isStreaming);
                startButton.setEnabled(true);
                startButton.setAlpha(1.0f);
            } else if (state == ShimmerBluetooth.BT_STATE.DISCONNECTED) {
                isConnected = false;
                connectButton.setText("Connect");
                startButton.setEnabled(false);
                startButton.setAlpha(0.5f);
                visualizeButton.setEnabled(true);
            }
        });
    }

    @Override
    public void onDataReceived(ObjectCluster oc) {
    }


    public void startStreaming(View v) {
    if (!mBound || mService == null) {
        Toast.makeText(this, "Service not ready yet", Toast.LENGTH_SHORT).show();
        return;

        } else {
            startStreamingLogic();
        }
    }

    private void startStreamingLogic() {
        mService.xAxis = xAxisCB.isChecked();
        mService.yAxis = yAxisCB.isChecked();
        mService.zAxis = zAxisCB.isChecked();
        mService.xGyro = xGyroCB.isChecked();
        mService.yGyro = yGyroCB.isChecked();
        mService.zGyro = zGyroCB.isChecked();
        mService.emg = emgCB.isChecked();


        File dir = new File(getExternalFilesDir(null), "Balance");
        if (!dir.exists()) dir.mkdirs();

        int fileCounter = 1;
        File file;
        do {
            file = new File(dir, "shimmer_data_" + fileCounter + ".csv");
            fileCounter++;
        } while (file.exists());

        try {
            mService.writeCSV = new BufferedWriter(new FileWriter(file));

            mService.writeCSV.write("Patient:," + patientET.getText().toString() + "\n");

            StringBuilder header = new StringBuilder("Timestamp");
            if (xAxisCB.isChecked()) header.append(",AccelX");
            if (yAxisCB.isChecked()) header.append(",AccelY");
            if (zAxisCB.isChecked()) header.append(",AccelZ");
            if (xGyroCB.isChecked()) header.append(",GyroX");
            if (yGyroCB.isChecked()) header.append(",GyroY");
            if (zGyroCB.isChecked()) header.append(",GyroZ");
            if (emgCB.isChecked()) header.append(",EMG");

            mService.writeCSV.write(header.toString() + "\n");

            mService.isStreaming = true;
            mService.shimmerDevice.startStreaming();

            startButton.setText("Stop");
            visualizeButton.setEnabled(false);
            startTimer();
        } catch (Exception e) {
            Log.e("CollectActivity", "Start Error: " + e.getMessage());
        }
    }

    private void stopStreamingLogic() {
        mService.isStreaming = false;

        visualizeButton.setEnabled(true);
        visualizeButton.setAlpha(1f);

        try {
            if (mService.shimmerDevice != null) mService.shimmerDevice.stopStreaming();
            if (mService.writeCSV != null) {
                mService.writeCSV.flush();
                mService.writeCSV.close();
            }
        } catch (Exception e) { e.printStackTrace(); }

        startButton.setText("Start");
        stopTimer();
    }

    public void connectDevice(View v) {
        if (!isConnected) {
            Intent intent = new Intent(this, ShimmerBluetoothDialog.class);
            startActivityForResult(intent, ShimmerBluetoothDialog.REQUEST_CONNECT_SHIMMER);
        } else {
            if (mService != null && mService.shimmerDevice != null) {
                try { mService.shimmerDevice.disconnect(); } catch (Exception e) {}
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ShimmerBluetoothDialog.REQUEST_CONNECT_SHIMMER && resultCode == Activity.RESULT_OK) {
            String mac = data.getStringExtra(EXTRA_DEVICE_ADDRESS);
            showBtTypeDialog(mac);
        }
    }

    private void showBtTypeDialog(String mac) {
        new AlertDialog.Builder(this)
                .setTitle("Bluetooth Type")
                .setPositiveButton("Classic", (d, w) -> mService.btManager.connectShimmerThroughBTAddress(mac, ShimmerBluetoothManagerAndroid.BT_TYPE.BT_CLASSIC))
                .setNegativeButton("BLE", (d, w) -> mService.btManager.connectShimmerThroughBTAddress(mac, ShimmerBluetoothManagerAndroid.BT_TYPE.BLE))
                .show();
    }

    private void startTimer() {
        timeSeconds = 10;
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!mService.isStreaming) return;
                timeSeconds--;
                int min = timeSeconds / 60;
                int sec = timeSeconds % 60;
                clock.setText(String.format("%d:%02d", min, sec));
                if (timeSeconds > 0) timerHandler.postDelayed(this, 1000);
                else stopStreamingLogic();
            }
        };
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    private void stopTimer() {
        timerHandler.removeCallbacks(timerRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBound) unbindService(connection);
    }

    public void visualize(View v) {
        startActivity(new Intent(this, VisualizeActivity.class));
    }
}