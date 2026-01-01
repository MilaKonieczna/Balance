    package com.example.balance;

import static com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog.EXTRA_DEVICE_ADDRESS;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import com.clj.fastble.BleManager;
import com.shimmerresearch.android.guiUtilities.ShimmerBluetoothDialog;
import com.shimmerresearch.android.manager.ShimmerBluetoothManagerAndroid;
import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.exceptions.ShimmerException;


import java.io.BufferedWriter;
import java.io.File;
import android.support.v4.content.FileProvider;
import android.widget.Toast;

import java.io.FileWriter;
import java.io.IOException;

public class CollectActivity extends AppCompatActivity {


    private EditText patientET;
    private CheckBox xAxisCB, yAxisCB, zAxisCB, xGyroCB, yGyroCB, zGyroCB, emgCB;
    private File currentFile;

    BufferedWriter writeCSV = null;
    ShimmerBluetoothManagerAndroid btManager;
    ShimmerDevice shimmerDevice;
    String shimmerBtAdd;
    String patient;
    boolean xAxis;
    boolean yAxis;
    boolean zAxis;
    boolean xGyro;
    boolean yGyro;
    boolean zGyro;
    boolean emg;

    ShimmerBluetoothManagerAndroid.BT_TYPE preferredBtType;

    Button connectButton, startButton, visualizeButton;
    TextView clock;

    ScrollView scrollView;
    boolean isConnected = false;
    boolean isStreaming = false;
    int timeSeconds = 120;
    Handler timerHandler = new Handler();
    Runnable timerRunnable;
    final static String LOG_TAG = "BluetoothManagerExample";

    @SuppressLint("InlinedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        patientET = findViewById(R.id.patient);
        xAxisCB = findViewById(R.id.Xaxis);
        yAxisCB = findViewById(R.id.Yaxis);
        zAxisCB = findViewById(R.id.Zaxis);
        xGyroCB = findViewById(R.id.Xgyro);
        yGyroCB = findViewById(R.id.Ygyro);
        zGyroCB = findViewById(R.id.Zgyro);
        emgCB = findViewById(R.id.EMG);


        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);

        connectButton = findViewById(R.id.connect);
        startButton = findViewById(R.id.start);
        clock = findViewById(R.id.clock);
        visualizeButton = findViewById(R.id.visualize);
        scrollView = findViewById(R.id.scroll);

        visualizeButton.setEnabled(false);
        startButton.setEnabled(false);
        startButton.setAlpha(0.5f);

        updateClockText();

        createNotificationChannel();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
            }, 101);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
        }
    }


    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                }
            }
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != 101) {
            return;
        }

        boolean allPermissionsGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (!allPermissionsGranted) {
        } else {
            BleManager.getInstance().init(getApplication());
            try {
                btManager = new ShimmerBluetoothManagerAndroid(this, mHandler);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Couldn't create ShimmerBluetoothManagerAndroid. Error: " + e);
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(receiver != null) {
            unregisterReceiver(receiver);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        if (shimmerDevice != null) {
            if (shimmerDevice.isSDLogging()) {
                shimmerDevice.stopSDLogging();
                Log.d(LOG_TAG, "Stopped Shimmer Logging");
            } else if (shimmerDevice.isStreaming()) {
                try {
                    shimmerDevice.stopStreaming();
                } catch (ShimmerException e) {
                    e.printStackTrace();
                }
                Log.d(LOG_TAG, "Stopped Shimmer Streaming");
            } else {
                shimmerDevice.stopStreamingAndLogging();
                Log.d(LOG_TAG, "Stopped Shimmer Streaming and Logging");
            }
        }
        if(btManager != null) {
            btManager.disconnectAllDevices();
            Log.i(LOG_TAG, "Shimmer DISCONNECTED");
        }


        super.onStop();
    }

    @SuppressLint("HandlerLeak")
    Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ShimmerBluetooth.MSG_IDENTIFIER_DATA_PACKET:
                    if (msg.obj instanceof ObjectCluster) {
                        processDataPacket((ObjectCluster) msg.obj);
                    }
                    break;
                case ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE:
                    if (msg.obj instanceof ObjectCluster) {
                        ObjectCluster oc = (ObjectCluster) msg.obj;
                        ShimmerBluetooth.BT_STATE state = oc.mState;
                        String macAddress = oc.getMacAddress();

                        Log.d(LOG_TAG, "State Change: " + state.name() + " for " + macAddress);

                        switch (state) {
                            case CONNECTED:
                                isConnected = true;
                                shimmerDevice = btManager.getShimmerDeviceBtConnectedFromMac(macAddress);
                                runOnUiThread(() -> {
                                    connectButton.setText(R.string.disconnect);
                                    connectButton.setBackgroundResource(R.drawable.disconnect_drawable);
                                    startButton.setEnabled(true);
                                    startButton.setAlpha(1f);
                                });
                                break;

                            case DISCONNECTED:
                                isConnected = false;
                                shimmerDevice = null;
                                runOnUiThread(() -> {
                                    connectButton.setText(R.string.connect);
                                    connectButton.setBackgroundResource(R.drawable.connect_drawable);

                                    startButton.setEnabled(false);
                                    startButton.setAlpha(0.5f);

                                    if(isStreaming) {
                                        stopStreaming(null);
                                    }
                                });
                                break;
                        }
                    }
                    break;
            }
        }
    };

    private void toggleInputs(boolean enabled) {
        patientET.setEnabled(enabled);
        xAxisCB.setEnabled(enabled);
        yAxisCB.setEnabled(enabled);
        zAxisCB.setEnabled(enabled);
        xGyroCB.setEnabled(enabled);
        yGyroCB.setEnabled(enabled);
        zGyroCB.setEnabled(enabled);
        emgCB.setEnabled(enabled);
        patientET.setAlpha(enabled ? 1.0f : 0.5f);
    }

    private void processDataPacket(ObjectCluster oc) {

        FormatCluster tsCluster = ObjectCluster.returnFormatCluster(
                oc.getCollectionOfFormatClusters(Configuration.Shimmer3.ObjectClusterSensorName.TIMESTAMP),
                "CAL");

        if (tsCluster == null) return;

        double timestamp = tsCluster.mData;
        StringBuilder row = new StringBuilder();
        row.append(timestamp);

        try {
            if (xAxis) {
                FormatCluster accelX = ObjectCluster.returnFormatCluster(
                        oc.getCollectionOfFormatClusters(Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_LN_X), "CAL");
                row.append(",").append(accelX != null ? accelX.mData : "0");
            }
            if (yAxis) {
                FormatCluster accelY = ObjectCluster.returnFormatCluster(
                        oc.getCollectionOfFormatClusters(Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_LN_Y), "CAL");
                row.append(",").append(accelY != null ? accelY.mData : "0");
            }
            if (zAxis) {
                FormatCluster accelZ = ObjectCluster.returnFormatCluster(
                        oc.getCollectionOfFormatClusters(Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_LN_Z), "CAL");
                row.append(",").append(accelZ != null ? accelZ.mData : "0");
            }

            if (xGyro) {
                FormatCluster gx = ObjectCluster.returnFormatCluster(
                        oc.getCollectionOfFormatClusters(Configuration.Shimmer3.ObjectClusterSensorName.GYRO_X), "CAL");
                row.append(",").append(gx != null ? gx.mData : "0");
            }
            if (yGyro) {
                FormatCluster gy = ObjectCluster.returnFormatCluster(
                        oc.getCollectionOfFormatClusters(Configuration.Shimmer3.ObjectClusterSensorName.GYRO_Y), "CAL");
                row.append(",").append(gy != null ? gy.mData : "0");
            }
            if (zGyro) {
                FormatCluster gz = ObjectCluster.returnFormatCluster(
                        oc.getCollectionOfFormatClusters(Configuration.Shimmer3.ObjectClusterSensorName.GYRO_Z), "CAL");
                row.append(",").append(gz != null ? gz.mData : "0");
            }

            if (emg) {
                FormatCluster emgCluster = ObjectCluster.returnFormatCluster(
                        oc.getCollectionOfFormatClusters(Configuration.Shimmer3.ObjectClusterSensorName.EMG_CH1_24BIT), "CAL");
                row.append(",").append(emgCluster != null ? emgCluster.mData : "0");
            }

            if (writeCSV != null) {
                writeCSV.write(row.toString() + "\n");
            }

        } catch (IOException e) {
            Log.e(LOG_TAG, "Error writing CSV: " + e.getMessage());
        }
    }

    public void startStreaming(View v) {
        if (isStreaming) {
            stopStreaming(v);
        } else {
            startStreamingLogic();
        }
    }

    public void stopStreaming(View v) {
        if (!isStreaming) {
            return;
        }

        isStreaming = false;
        stopTimer();
        toggleInputs(true);

        try {
            if (shimmerDevice != null && shimmerDevice.isStreaming()) {
                shimmerDevice.stopStreaming();
            }

            if (writeCSV != null) {
                writeCSV.flush();
                writeCSV.close();
                writeCSV = null;

                if (currentFile != null && currentFile.exists()) {
                    showFileNotification(currentFile);
                    visualizeButton.setEnabled(true);

                    scrollView.post(() -> {
                        int startY = scrollView.getScrollY();
                        int endY = visualizeButton.getBottom();

                        ValueAnimator animator = ValueAnimator.ofInt(startY, endY);
                        animator.setDuration(1000);
                        animator.addUpdateListener(animation -> {
                            int scrollTo = (int) animation.getAnimatedValue();
                            scrollView.scrollTo(0, scrollTo);
                        });
                        animator.start();
                    });

                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error while stopping stream: " + e.getMessage());
        }

        startButton.setText(R.string.start_streaming);
        startButton.setBackgroundResource(R.drawable.start_drawable);
        startButton.setEnabled(true);
        startButton.setAlpha(1f);
    }

    public void showVisualize(View v) {
            startActivity(new Intent(this, VisualizeActivity.class));
    }

    private void startStreamingLogic() {
        if (shimmerDevice == null) {
            return;
        }

        patient = patientET.getText().toString();
        xAxis = xAxisCB.isChecked();
        yAxis = yAxisCB.isChecked();
        zAxis = zAxisCB.isChecked();
        xGyro = xGyroCB.isChecked();
        yGyro = yGyroCB.isChecked();
        zGyro = zGyroCB.isChecked();
        emg = emgCB.isChecked();

        if (!(xAxis || yAxis || zAxis || xGyro || yGyro || zGyro || emg)) {
            Toast.makeText(this, "Please select at least one signal to plot", Toast.LENGTH_SHORT).show();
            return;
        }

        toggleInputs(false);

        isStreaming = true;
        startButton.setText(R.string.stop_streaming);
        startButton.setBackgroundResource(R.drawable.stop_drawable);

        startTimer();

        File downloads = new File(getExternalFilesDir(null), "Balance");
        if (!downloads.exists()) {
            downloads.mkdirs();
        }

        File file;
        int fileNumber = 1;
        do {
            file = new File(downloads, "shimmer_data" + fileNumber + ".csv");
            fileNumber++;
        } while (file.exists());

        Log.d(LOG_TAG, "Stream will be saved to: " + file.getAbsolutePath());

        currentFile = file;
        try {
            writeCSV = new BufferedWriter(new FileWriter(file, false));
            writeCSV.write("Patient:," + patient + "\n");

            StringBuilder header = new StringBuilder("Timestamp");
            if (xAxis) header.append(",AccelX");
            if (yAxis) header.append(",AccelY");
            if (zAxis) header.append(",AccelZ");
            if (xGyro) header.append(",GyroX");
            if (yGyro) header.append(",GyroY");
            if (zGyro) header.append(",GyroZ");
            if (emg) header.append(",EMG");

            writeCSV.write(header.toString() + "\n");

        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to create CSV file: " + e.getMessage());
        }

        try {
            shimmerDevice.startStreaming();
        } catch (ShimmerException e) {
            e.printStackTrace();
        }
    }

    public void connectDevice(View v) {
        if (!isConnected) {
            Intent intent = new Intent(this, ShimmerBluetoothDialog.class);
            startActivityForResult(intent, ShimmerBluetoothDialog.REQUEST_CONNECT_SHIMMER);
        } else {
            disconnectDevice(v);
        }
    }

    public void disconnectDevice(View v) {
        if (shimmerDevice != null) {
            try {
                shimmerDevice.disconnect();
            } catch (ShimmerException e) {
                e.printStackTrace();
            }
        }
        isConnected = false;
        connectButton.setText(R.string.connect);
        connectButton.setBackgroundResource(R.drawable.connect_drawable);
        if(isStreaming) {
            stopStreaming(null);
        }
        startButton.setEnabled(false);
        startButton.setAlpha(0.5f);
    }


    private void startTimer() {
        stopTimer();

        timeSeconds = 10;
        updateClockText();

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isStreaming) {
                    return;
                }

                timeSeconds--;
                updateClockText();

                if (timeSeconds > 0 && isStreaming) {
                    timerHandler.postDelayed(this, 1000);
                } else {
                    runOnUiThread(() -> stopStreaming(null));
                }
            }
        };

        timerHandler.postDelayed(timerRunnable, 1000);
    }


    private void stopTimer() {
        timerHandler.removeCallbacks(timerRunnable);
        timeSeconds = 10;
        updateClockText();
    }

    private void updateClockText() {
        int min = timeSeconds / 60;
        int sec = timeSeconds % 60;
        clock.setText(String.format("%d:%02d", min, sec));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ShimmerBluetoothDialog.REQUEST_CONNECT_SHIMMER && resultCode == Activity.RESULT_OK && data != null) {

            if (btManager == null) {
                try {
                    btManager = new ShimmerBluetoothManagerAndroid(this, mHandler);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Couldn't create ShimmerBluetoothManagerAndroid. Error: " + e);
                    return;
                }
            }

            btManager.disconnectAllDevices();
            shimmerDevice = null;

            String macAdd = data.getStringExtra(EXTRA_DEVICE_ADDRESS);
            shimmerBtAdd = macAdd;

            showBtTypeConnectionOption(macAdd);
        }
    }

    public void showBtTypeConnectionOption(String macAddress) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose preferred Bluetooth type")
                .setCancelable(false)
                .setPositiveButton("BT CLASSIC", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        preferredBtType = ShimmerBluetoothManagerAndroid.BT_TYPE.BT_CLASSIC;
                        connectToShimmer(macAddress);
                    }
                })
                .setNegativeButton("BLE", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        preferredBtType = ShimmerBluetoothManagerAndroid.BT_TYPE.BLE;
                        connectToShimmer(macAddress);
                    }
                })
                .show();
    }

    private void connectToShimmer(String macAddress) {
        if (preferredBtType != null && macAddress != null && btManager != null) {
            btManager.connectShimmerThroughBTAddress(macAddress, preferredBtType);
        } else {
            Log.e(LOG_TAG, "Cannot connect: preferredBtType or macAddress is null");
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "shimmer_channel", "Shimmer Data", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void showFileNotification(File savedFile) {
        try {
            Uri contentUri = FileProvider.getUriForFile(
                    this,
                    "com.example.balance.fileprovider",
                    savedFile);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, "text/comma-separated-values");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            int flags;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
            } else {
                flags = PendingIntent.FLAG_UPDATE_CURRENT;
            }

            PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, flags);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "shimmer_channel")
                    .setSmallIcon(android.R.drawable.ic_menu_save)
                    .setContentTitle("Stream Saved")
                    .setContentText("Tap to view " + savedFile.getName())
                    .setContentIntent(pIntent)
                    .setAutoCancel(true);

            NotificationManagerCompat.from(this).notify(1, builder.build());

        } catch (Exception e) {
            Log.e(LOG_TAG, "Notification logic failed: " + e.getMessage());
        }
    }
}