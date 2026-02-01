package com.example.balance;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.shimmerresearch.android.manager.ShimmerBluetoothManagerAndroid;
import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;

import java.io.BufferedWriter;
import java.io.IOException;

public class ForegroundService extends Service {
    private static final String CHANNEL_ID = "ShimmerServiceChannel";
    private final IBinder binder = new LocalBinder();

    public ShimmerBluetoothManagerAndroid btManager;
    public ShimmerDevice shimmerDevice;
    public BufferedWriter writeCSV = null;
    public boolean isStreaming = false;

    public boolean xAxis, yAxis, zAxis, xGyro, yGyro, zGyro, emg;

    private ServiceHandler mServiceHandler;

    public interface ServiceHandler {
        void onStateChanged(ShimmerBluetooth.BT_STATE state, String mac);
        void onDataReceived(ObjectCluster oc);
    }

    public void setHandler(ServiceHandler handler) {
        this.mServiceHandler = handler;
    }

    public class LocalBinder extends Binder {
        ForegroundService getService() { return ForegroundService.this; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        try {
            btManager = new ShimmerBluetoothManagerAndroid(this, mHandler);
        } catch (Exception e) {
            Log.e("ShimmerService", "Error: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent notificationIntent = new Intent(this, CollectActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Shimmer Streaming")
                .setContentText("Connection is active in the background")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ShimmerBluetooth.MSG_IDENTIFIER_DATA_PACKET:
                    if (msg.obj instanceof ObjectCluster) {
                        ObjectCluster oc = (ObjectCluster) msg.obj;
                        processAndWriteData(oc);
                        if (mServiceHandler != null) mServiceHandler.onDataReceived(oc);
                    }
                    break;
                case ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE:
                    if (msg.obj instanceof ObjectCluster) {
                        ObjectCluster oc = (ObjectCluster) msg.obj;
                        if (oc.mState == ShimmerBluetooth.BT_STATE.CONNECTED) {
                            shimmerDevice = btManager.getShimmerDeviceBtConnectedFromMac(oc.getMacAddress());
                        }
                        if (mServiceHandler != null) mServiceHandler.onStateChanged(oc.mState, oc.getMacAddress());
                    }
                    break;
            }
        }
    };

    private void processAndWriteData(ObjectCluster oc) {
        if (!isStreaming || writeCSV == null) return;

        try {
            FormatCluster tsCluster = ObjectCluster.returnFormatCluster(
                    oc.getCollectionOfFormatClusters(Configuration.Shimmer3.ObjectClusterSensorName.TIMESTAMP), "CAL");
            if (tsCluster == null) return;

            StringBuilder row = new StringBuilder();
            row.append(tsCluster.mData);

            appendSensorData(row, oc, Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_LN_X, xAxis);
            appendSensorData(row, oc, Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_LN_Y, yAxis);
            appendSensorData(row, oc, Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_LN_Z, zAxis);
            appendSensorData(row, oc, Configuration.Shimmer3.ObjectClusterSensorName.GYRO_X, xGyro);
            appendSensorData(row, oc, Configuration.Shimmer3.ObjectClusterSensorName.GYRO_Y, yGyro);
            appendSensorData(row, oc, Configuration.Shimmer3.ObjectClusterSensorName.GYRO_Z, zGyro);
            appendSensorData(row, oc, Configuration.Shimmer3.ObjectClusterSensorName.EMG_CH1_24BIT, emg);

            writeCSV.write(row.toString() + "\n");
        } catch (IOException e) {
            Log.e("ShimmerService", "Write Error: " + e.getMessage());
        }
    }

    private void appendSensorData(StringBuilder sb, ObjectCluster oc, String sensor, boolean active) {
        if (active) {
            FormatCluster fc = ObjectCluster.returnFormatCluster(oc.getCollectionOfFormatClusters(sensor), "CAL");
            sb.append(",").append(fc != null ? fc.mData : "0");
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Shimmer Channel", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
}