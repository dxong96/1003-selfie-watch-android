package com.nordicsemi.nrfUARTv2;

import android.app.Activity;
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
        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, intentFilter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(mServiceConnection);

        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, intentFilter);
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
            if (previousDeviceAddress != null) {
                mService.connect(previousDeviceAddress);
            }

        }

        public void onServiceDisconnected(ComponentName classname) {
            ////     mService.disconnect(mDevice);
            mService = null;
        }
    };

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
        ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - connecting");
        mService.connect(deviceAddress);
        previousDeviceAddress = deviceAddress;
    }

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
                            dataAvailable(text);
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
