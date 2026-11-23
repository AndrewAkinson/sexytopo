package org.hwyl.sexytopo.comms.scanner;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import androidx.annotation.NonNull;
import java.util.UUID;
import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.observer.ConnectionObserver;
import org.hwyl.sexytopo.R;
import org.hwyl.sexytopo.comms.ReconnectionPolicy;
import org.hwyl.sexytopo.control.Log;

/**
 * The scanner over Bluetooth LE, in the Nordic UART Service layout (CrossSectionScanner
 * docs/LINK.md). Frames are split to the MTU by the Nordic library, and joined again by the link.
 */
public class BleScannerConnection implements ScannerConnection, ConnectionObserver {

    private static final UUID SERVICE_UUID =
            UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID RX_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID TX_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    private static final int MTU = 517;
    private static final int CONNECT_TIMEOUT_MS = 10000;

    private final ScannerService service;
    private final BluetoothDevice device;
    private final String name;
    private final Manager manager;
    private final ReconnectionPolicy reconnectionPolicy;

    public BleScannerConnection(ScannerService service, BluetoothDevice device, String name) {
        this.service = service;
        this.device = device;
        this.name = name;
        this.manager = new Manager();
        this.reconnectionPolicy = new ReconnectionPolicy(name, this::connect);
        manager.setConnectionObserver(this);
    }

    @Override
    public void connect() {
        reconnectionPolicy.noteUserRequestedConnect();
        manager.connect(device).timeout(CONNECT_TIMEOUT_MS).retry(3, 100).enqueue();
    }

    @Override
    public void disconnect() {
        reconnectionPolicy.noteUserRequestedDisconnect();
        manager.disconnect().enqueue();
    }

    @Override
    public boolean isConnected() {
        return manager.isReady();
    }

    @Override
    public void send(byte[] frame) {
        manager.write(frame);
    }

    @Override
    public void onDeviceConnecting(@NonNull BluetoothDevice device) {
        Log.device(R.string.device_ble_connecting_to, name);
    }

    @Override
    public void onDeviceConnected(@NonNull BluetoothDevice device) {
        Log.device(R.string.device_ble_connected_to, name);
    }

    @Override
    public void onDeviceFailedToConnect(@NonNull BluetoothDevice device, int reason) {
        Log.device(R.string.device_ble_failed_to_connect_to, name);
        service.runOnMainThread(() -> service.getLink().onDisconnected(this));
        reconnectionPolicy.onUnexpectedDisconnection();
    }

    @Override
    public void onDeviceReady(@NonNull BluetoothDevice device) {
        Log.device(R.string.device_ble_device_ready, name);
        reconnectionPolicy.noteReady();
        service.runOnMainThread(() -> service.getLink().onConnected(this));
    }

    @Override
    public void onDeviceDisconnecting(@NonNull BluetoothDevice device) {
        Log.device(R.string.device_ble_device_disconnecting, name);
    }

    @Override
    public void onDeviceDisconnected(@NonNull BluetoothDevice device, int reason) {
        // a timeout means it never connected, so saying it disconnected would overstate it
        Log.device(
                reason == ConnectionObserver.REASON_TIMEOUT
                        ? R.string.device_ble_device_not_connected
                        : R.string.device_ble_device_disconnected,
                name);
        service.runOnMainThread(() -> service.getLink().onDisconnected(this));
        // anything but success means it wasn't asked for
        if (reason != ConnectionObserver.REASON_SUCCESS) {
            reconnectionPolicy.onUnexpectedDisconnection();
        }
    }

    private class Manager extends BleManager {

        private BluetoothGattCharacteristic rx;
        private BluetoothGattCharacteristic tx;

        Manager() {
            super(service.getContext());
        }

        @Override
        protected boolean isRequiredServiceSupported(@NonNull BluetoothGatt gatt) {
            BluetoothGattService nus = gatt.getService(SERVICE_UUID);
            if (nus != null) {
                rx = nus.getCharacteristic(RX_UUID);
                tx = nus.getCharacteristic(TX_UUID);
            }
            return rx != null && tx != null;
        }

        @Override
        protected void initialize() {
            requestMtu(MTU).enqueue();
            setNotificationCallback(tx)
                    .with(
                            (device, data) -> {
                                byte[] bytes = data.getValue();
                                if (bytes != null) {
                                    service.runOnMainThread(() -> service.getLink().onBytes(bytes));
                                }
                            });
            enableNotifications(tx).enqueue();
        }

        @Override
        protected void onServicesInvalidated() {
            rx = null;
            tx = null;
        }

        void write(byte[] frame) {
            if (rx == null) {
                return;
            }
            writeCharacteristic(rx, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    .split()
                    .fail(
                            (device, status) ->
                                    Log.device(
                                            R.string.device_scanner_error,
                                            "write status " + status))
                    .enqueue();
        }
    }
}
