package com.nordicsemi.nrfUARTv2;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

public class DeviceListFragmentV18 extends DeviceListFragment {
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {

                            addDevice(device, rssi);
                        }
                    });
                }
            };


    @Override
    public void startScan() {
        mBluetoothAdapter.startLeScan(mLeScanCallback);
    }

    @Override
    public void stopScan() {
        mBluetoothAdapter.stopLeScan(mLeScanCallback);
    }

}
