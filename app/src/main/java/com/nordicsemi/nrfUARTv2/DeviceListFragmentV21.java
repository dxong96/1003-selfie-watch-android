package com.nordicsemi.nrfUARTv2;

import android.Manifest;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DeviceListFragmentV21 extends DeviceListFragment {
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_COARSE = 1;

    private final ScanSettings settings = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
    private final List<ScanFilter> filters = new ArrayList<>();
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            super.onScanResult(callbackType, result);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    addDevice(result.getDevice(), result.getRssi());
                }
            });
        }
    };


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (mBluetoothAdapter != null) {
            scanner = mBluetoothAdapter.getBluetoothLeScanner();
        }
    }

    @Override
    public void startScan() {
        boolean interrupted = requestPermission();
        if (interrupted) {
            return;
        }

        scanner.startScan(filters, settings, scanCallback);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_COARSE:
                scanLeDevice(true);
        }
    }

    private boolean requestPermission() {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED){
            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),
                    Manifest.permission.ACCESS_COARSE_LOCATION)) {
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.permission_location_rationale_ble_title)
                        .setMessage(R.string.permission_location_rationale_ble)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            } else {
                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        MY_PERMISSIONS_REQUEST_ACCESS_COARSE);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void stopScan() {
        scanner.stopScan(scanCallback);
    }

    @Override
    public void onPause() {
        super.onPause();
        scanLeDevice(false);
    }
}
