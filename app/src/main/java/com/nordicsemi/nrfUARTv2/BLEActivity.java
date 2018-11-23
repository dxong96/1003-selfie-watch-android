package com.nordicsemi.nrfUARTv2;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;

public abstract class BLEActivity extends AppCompatActivity implements DeviceListFragment.Callback {
    private final static String TAG = "BLEActivity";
    private static final int REQUEST_ENABLE_BT = 2;
    private static String previousDeviceAddress = null;

    private final static IntentFilter intentFilter;
    static {
        intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
    }

    protected final Handler mHandler = new Handler();
    protected BluetoothDevice mDevice = null;
    protected BluetoothAdapter mBtAdapter = null;
    protected UartService mService = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        tryEnableBT();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent serviceIntent = new Intent(this, UartService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, intentFilter);

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(BluetoothStateReceiver, filter);

    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(mServiceConnection);


        LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        unregisterReceiver(BluetoothStateReceiver);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
        }
    }

    private void tryToConnect() {
        if (previousDeviceAddress != null) {
            mService.connect(previousDeviceAddress);
        } else {
            showScanDialog();
        }
    }

    private void tryEnableBT() {
        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }

    //UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
                return;
            }
            tryToConnect();
        }

        public void onServiceDisconnected(ComponentName classname) {
            ////     mService.disconnect(mDevice);
            mService = null;
        }
    };

    //call this function at the main activity
    protected void showScanDialog() {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        Fragment prev = getSupportFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        // Create and show the dialog.
        DialogFragment newFragment;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            newFragment = new DeviceListFragmentV21();
        } else {
            newFragment = new DeviceListFragmentV18();
        }
        newFragment.show(ft, "dialog");
    }

    @Override
    public void onResult(String deviceAddress) {
        mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

        Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
//        ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - connecting");
        mService.connect(deviceAddress);
        previousDeviceAddress = deviceAddress;
    }

    private final BroadcastReceiver BluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.d(TAG, "bluetooth off");
                        mService.disconnect();
                        tryEnableBT();
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.d(TAG, "bluetooth on");
                        tryToConnect();
                        break;
                }
            }
        }
    };

    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, final Intent intent) {
            final String action = intent.getAction();

            if (action == null) {
                return;
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    switch (action) {
                        case UartService.ACTION_GATT_CONNECTED:
                            connected();
                            break;
                        case UartService.ACTION_GATT_DISCONNECTED:
                            disconnected();
                            break;
                        case UartService.ACTION_GATT_SERVICES_DISCOVERED:
                            mService.enableTXNotification();
                            break;
                        case UartService.ACTION_DATA_AVAILABLE:
                            final byte[] txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);
                            String text = null;
                            try {
                                text = new String(txValue, "UTF-8");
                            } catch (UnsupportedEncodingException e) {
                                e.printStackTrace();
                            }
                            dataAvailable(text.trim());
                            Log.d(TAG, "Data received: " + text.trim());
                            break;
                        case UartService.DEVICE_DOES_NOT_SUPPORT_UART:
                            notSupported();
                            break;
                    }
                }
            });
        }
    };

    protected abstract void notSupported();
    protected abstract void connected();
    protected abstract void disconnected();
    protected abstract void dataAvailable(String text);
}
