package org.hwyl.sexytopo.control.activity;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import org.hwyl.sexytopo.R;
import org.hwyl.sexytopo.SexyTopoConstants;
import org.hwyl.sexytopo.comms.Communicator;
import org.hwyl.sexytopo.comms.Instrument;
import org.hwyl.sexytopo.comms.InstrumentType;
import org.hwyl.sexytopo.comms.scanner.ScannerConnection;
import org.hwyl.sexytopo.comms.scanner.ScannerService;
import org.hwyl.sexytopo.comms.scanner.ScannerType;
import org.hwyl.sexytopo.comms.scanner.TestScannerConnection;
import org.hwyl.sexytopo.control.Log;
import org.hwyl.sexytopo.control.util.GeneralPreferences;
import org.hwyl.sexytopo.control.util.LogUpdateReceiver;

public class DeviceActivity extends SexyTopoActivity {

    public static final String DISTO_X_PREFIX = "DistoX";
    public static final String SHETLAND_PREFIX = "Shetland";

    private static BluetoothAdapter bluetoothAdapter;

    private IntentFilter logFilter;
    private IntentFilter statusFilter;
    private IntentFilter scanFilter;
    private DeviceLogUpdateReceiver logUpdateReceiver;
    private StateChangeReceiver stateChangeReceiver;
    private ScanReceiver scanReceiver;
    private ScannerStatusReceiver scannerStatusReceiver;

    // the Bluetooth search is for the scanner rather than the instrument
    private boolean findingScanner = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);
        setupMaterialToolbar();

        applyEdgeToEdgeInsets(R.id.rootLayout, true, true);

        bluetoothAdapter = getBluetoothAdapter();

        setupSwitchListeners();
        try {
            updateStatuses();
        } catch (Exception exception) {
            Log.e(exception);
            Log.e("Error updating statuses: " + exception.getMessage());
        }

        scanFilter = new IntentFilter();
        scanFilter.addAction(BluetoothDevice.ACTION_FOUND);
        scanFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        scanReceiver = new ScanReceiver();

        statusFilter = new IntentFilter();
        statusFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        statusFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        stateChangeReceiver = new StateChangeReceiver();

        logFilter = new IntentFilter();
        logFilter.addAction(SexyTopoConstants.DEVICE_LOG_UPDATED_EVENT);

        logUpdateReceiver = new DeviceLogUpdateReceiver();
        scannerStatusReceiver = new ScannerStatusReceiver();
        setupScannerListeners();
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onResume() {
        super.onResume();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        logUpdateReceiver.update();
        updateStatuses();

        registerReceiver(scanReceiver, scanFilter);
        registerReceiver(stateChangeReceiver, statusFilter);

        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
        broadcastManager.registerReceiver(logUpdateReceiver, logFilter);
        broadcastManager.registerReceiver(
                scannerStatusReceiver, new IntentFilter(SexyTopoConstants.SCANNER_STATUS_EVENT));
        updateScanner();

        if (!requestComms().isConnected()) {
            updateComms();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(stateChangeReceiver);
            unregisterReceiver(scanReceiver);
            LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
            broadcastManager.unregisterReceiver(logUpdateReceiver);
            broadcastManager.unregisterReceiver(scannerStatusReceiver);
        } catch (Exception exception) { // this shouldn't ever happen
            Log.e("Error unregistering receiver: " + exception.getMessage());
        }
    }

    @Override
    protected boolean offersUndoLastLeg() {
        // a bad reading can be thrown away straight from here while surveying
        return true;
    }

    private void updateStatuses() {
        updateBluetooth();
        updatePairedStatus();
        updateConnectionStatus();
        updateAutoReconnectStatus();
        updateComms();
    }

    /** Shows the setting, which can also be changed from the settings screen. */
    private void updateAutoReconnectStatus() {
        CheckBox autoReconnectCheckbox = findViewById(R.id.autoReconnectCheckbox);
        autoReconnectCheckbox.setChecked(GeneralPreferences.isAutoReconnectOn());
    }

    private void setupSwitchListeners() {
        findViewById(R.id.bluetoothSwitch).setOnClickListener(this::toggleBluetooth);
        findViewById(R.id.connectionSwitch).setOnClickListener(this::toggleConnection);
        findViewById(R.id.autoReconnectCheckbox)
                .setOnClickListener(
                        view -> GeneralPreferences.setAutoReconnect(((CheckBox) view).isChecked()));
    }

    public void startConnection() {
        try {
            updateComms();
            requestComms().requestConnect();

        } catch (Exception exception) {
            showExceptionAndLog(exception);
        }
    }

    public void stopConnection() {
        Log.device(getString(R.string.device_connection_closing));

        try {
            requestComms().requestDisconnect();
        } catch (Exception e) {
            Log.device(R.string.device_connection_closing_error, e.getMessage());
        }
    }

    private BluetoothAdapter getBluetoothAdapter() {
        if (bluetoothAdapter == null) {
            try {
                BluetoothManager bluetoothManager =
                        (BluetoothManager) (getSystemService(Context.BLUETOOTH_SERVICE));
                bluetoothAdapter = bluetoothManager.getAdapter();
            } catch (Exception exception) {
                Log.e(exception);
            }
        }
        return bluetoothAdapter;
    }

    @SuppressWarnings("RedundantIfStatement")
    private void updateBluetooth() {

        if (bluetoothAdapter == null) {
            bluetoothAdapter = getBluetoothAdapter();
        }

        if (bluetoothAdapter == null) {
            showSimpleToast(R.string.device_bluetooth_no_adapter);
        }

        SwitchCompat bluetoothSwitch = findViewById(R.id.bluetoothSwitch);

        if (bluetoothAdapter == null) {
            bluetoothSwitch.setChecked(false);
            bluetoothSwitch.setEnabled(false);
        } else if (bluetoothAdapter.isEnabled()) {
            bluetoothSwitch.setChecked(true);
        } else {
            bluetoothSwitch.setChecked(false);
        }

        findViewById(R.id.pairButton).setEnabled(bluetoothSwitch.isChecked());
        findViewById(R.id.unpairButton).setEnabled(bluetoothSwitch.isChecked());
    }

    public void toggleBluetooth(View view) throws SecurityException {
        SwitchCompat bluetoothSwitch = (SwitchCompat) view;
        if (bluetoothSwitch.isChecked()) {
            BluetoothAdapter.getDefaultAdapter().enable();
        } else {
            BluetoothAdapter.getDefaultAdapter().disable();
        }
        updateBluetooth();
    }

    public void toggleConnection(View view) {
        SwitchCompat connectionSwitch = (SwitchCompat) view;
        if (connectionSwitch.isChecked()) {
            Log.device(getString(R.string.device_connection_requested));
            startConnection();
        } else {
            Log.device(getString(R.string.device_connection_stop_requested));
            stopConnection();
        }
    }

    // TODO need some way of calling this
    public void clearLog(View view) {
        Log.clearDeviceLog();
    }

    public void requestPair(View view) throws SecurityException {

        bluetoothAdapter = getBluetoothAdapter();

        if (bluetoothAdapter == null) {
            showSimpleToast(R.string.device_bluetooth_no_adapter);
            return;
        }

        if (bluetoothAdapter.isDiscovering()) {
            Log.device(R.string.device_scan_cancel);
            bluetoothAdapter.cancelDiscovery();

        } else {
            findingScanner = false;
            boolean started = bluetoothAdapter.startDiscovery();
            if (started) {
                Log.device(R.string.device_scan_start);
            } else {
                Log.device(R.string.device_scan_failure);
            }
        }
    }

    public void requestUnpair(View view) {
        BluetoothDevice device = getPairedDevice();
        stopConnection();
        unpair(device);
        updatePairedStatus();
    }

    private void updatePairedStatus() throws SecurityException {

        BluetoothDevice device = getPairedDevice();
        boolean isPaired = (device != null);

        SwitchCompat bluetoothSwitch = findViewById(R.id.bluetoothSwitch);
        Button pairButton = findViewById(R.id.pairButton);
        Button unpairButton = findViewById(R.id.unpairButton);

        TextView deviceList = findViewById(R.id.deviceList);
        int textColor =
                device == null
                        ? ContextCompat.getColor(this, android.R.color.darker_gray)
                        : ContextCompat.getColor(this, R.color.red);
        deviceList.setTextColor(textColor);
        if (isPaired) {
            pairButton.setEnabled(false);
            unpairButton.setEnabled(true);
            deviceList.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            deviceList.setText(getDeviceName(device));
        } else {
            pairButton.setEnabled(true);
            unpairButton.setEnabled(false);
            deviceList.setTextColor(ContextCompat.getColor(this, R.color.red));
            deviceList.setText(R.string.device_no_device);
        }

        // Allow connections iff we have one connected DistoX
        SwitchCompat connectionSwitch = findViewById(R.id.connectionSwitch);
        if (isPaired && bluetoothSwitch.isChecked()) {
            connectionSwitch.setEnabled(true);
        } else {
            connectionSwitch.setChecked(false);
            connectionSwitch.setEnabled(false);
        }
    }

    public void updateConnectionStatus() {
        // In theory this method could be called by the static
        // communicator after this activity finishes
        if (isFinishing() || isDestroyed()) {
            return;
        }

        SwitchCompat connectionSwitch = findViewById(R.id.connectionSwitch);
        connectionSwitch.setChecked(requestComms().isConnected());
    }

    private void unpair(BluetoothDevice device) throws SecurityException {

        if (device == null) {
            return;
        }

        if (isRemembered(device)) {
            Log.device(R.string.device_pairing_unpairing_device, getDeviceName(device));
            GeneralPreferences.forgetRememberedDevice();
            updateComms();
            Log.device(R.string.device_pairing_unpairing_success);
            return;
        }

        try {
            Log.device(R.string.device_pairing_unpairing_device, device.getName());
            Method method = device.getClass().getMethod("removeBond", (Class[]) null);
            method.invoke(device, (Object[]) null);
            updateComms();
            Log.device(R.string.device_pairing_unpairing_success);
        } catch (Exception e) {
            Log.device(R.string.device_pairing_unpairing_error, e.getMessage());
        }
    }

    private void updateComms() {
        BluetoothDevice device = getPairedDevice();
        String name = getDeviceName(device);
        InstrumentType instrumentType = InstrumentType.byName(name);

        Instrument instrument = getInstrument();

        boolean doWeNeedToUpdateInstrument = false;

        if (instrument == null && instrumentType.isUsable()) {
            doWeNeedToUpdateInstrument = true;
        } else if (instrument != null && instrumentType != instrument.getInstrumentType()) {
            doWeNeedToUpdateInstrument = true;
        }

        if (doWeNeedToUpdateInstrument) {
            instrument = new Instrument(device, name);
            setInstrument(instrument);

            try {
                Communicator communicator = instrumentType.getNewCommunicator(this, device);
                setComms(communicator);
                invalidateOptionsMenu();
            } catch (Exception exception) {
                Log.e(exception);
                Log.device(R.string.device_communicator_creation_error, exception.getMessage());
            }
        }
    }

    private static void pair(BluetoothDevice device) throws SecurityException {
        try {
            Log.device(R.string.device_pairing_attempt, device.getName());
            device.createBond();
            Log.device(R.string.device_pairing_successful);
        } catch (Exception e) {
            Log.device(R.string.device_pairing_error, e.getMessage());
        }
    }

    private void remember(BluetoothDevice device, String name) {
        Log.device(R.string.device_pairing_attempt, name);
        GeneralPreferences.setRememberedDevice(device.getAddress(), name);
        Log.device(R.string.device_pairing_successful);
        updateStatuses();
    }

    private static boolean isRemembered(BluetoothDevice device) {
        return device.getAddress().equals(GeneralPreferences.getRememberedDeviceAddress());
    }

    private static String getDeviceName(BluetoothDevice device) {
        if (device == null) {
            return null;
        } else if (isRemembered(device)) {
            return GeneralPreferences.getRememberedDeviceName();
        }

        try {
            return device.getName();
        } catch (SecurityException e) {
            return null;
        }
    }

    /**
     * The chosen instrument: either one SexyTopo has remembered because it doesn't bond, or one
     * from Android's list of paired devices.
     */
    private BluetoothDevice getPairedDevice() {

        BluetoothAdapter bluetoothAdapter = getBluetoothAdapter();
        if (bluetoothAdapter == null) {
            return null;
        }

        String rememberedAddress = GeneralPreferences.getRememberedDeviceAddress();
        if (rememberedAddress != null) {
            try {
                return bluetoothAdapter.getRemoteDevice(rememberedAddress);
            } catch (IllegalArgumentException exception) { // not a valid address
                GeneralPreferences.forgetRememberedDevice();
            }
        }

        Set<BluetoothDevice> allPairedDevices;
        try {
            allPairedDevices = bluetoothAdapter.getBondedDevices();
        } catch (SecurityException exception) {
            allPairedDevices = new HashSet<>(); // probably not got permissions yet
        }

        for (BluetoothDevice device : allPairedDevices) {
            InstrumentType instrumentType = InstrumentType.byDevice(device);
            if (instrumentType != InstrumentType.OTHER && instrumentType != InstrumentType.NONE) {
                return device; // we should only be paired with one, so return the first
            }
        }

        return null;
    }

    private void setupScannerListeners() {
        findViewById(R.id.scannerFindButton).setOnClickListener(view -> findScanner());
        findViewById(R.id.scannerForgetButton)
                .setOnClickListener(
                        view -> {
                            ScannerService.getInstance(this).disconnect();
                            GeneralPreferences.forgetRememberedScanner();
                            updateScanner();
                        });
        findViewById(R.id.scannerConnectSwitch)
                .setOnClickListener(
                        view -> {
                            ScannerService service = ScannerService.getInstance(this);
                            if (((SwitchCompat) view).isChecked()) {
                                service.connect(GeneralPreferences.getRememberedScannerAddress());
                            } else {
                                service.disconnect();
                            }
                            updateScanner();
                        });
        findViewById(R.id.scannerUseTestButton)
                .setOnClickListener(
                        view -> {
                            ScannerService.getInstance(this).disconnect();
                            GeneralPreferences.setRememberedScanner(
                                    ScannerService.TEST_ADDRESS,
                                    getString(R.string.device_scanner_test_name));
                            updateScanner();
                        });
        findViewById(R.id.scannerSendTestButton)
                .setOnClickListener(
                        view -> {
                            ScannerConnection connection =
                                    ScannerService.getInstance(this).getConnection();
                            if (connection instanceof TestScannerConnection) {
                                ((TestScannerConnection) connection).sendTestSection();
                            }
                        });
    }

    private void findScanner() throws SecurityException {
        bluetoothAdapter = getBluetoothAdapter();
        if (bluetoothAdapter == null) {
            showSimpleToast(R.string.device_bluetooth_no_adapter);
            return;
        }
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        findingScanner = bluetoothAdapter.startDiscovery();
        Log.device(
                findingScanner ? R.string.device_scanner_searching : R.string.device_scan_failure);
    }

    /** Shows the scanner card, if the scanner setting is on, as the link now stands. */
    private void updateScanner() {
        boolean on = GeneralPreferences.isSectionScannerOn();
        findViewById(R.id.scannerCard).setVisibility(on ? View.VISIBLE : View.GONE);
        if (!on) {
            if (ScannerService.getExisting() != null) {
                ScannerService.getExisting().disconnect();
            }
            return;
        }
        ScannerService service = ScannerService.getInstance(this);
        String address = GeneralPreferences.getRememberedScannerAddress();
        String name = GeneralPreferences.getRememberedScannerName();
        boolean connected = service.getConnection() != null;

        TextView nameView = findViewById(R.id.scannerName);
        nameView.setText(address == null ? getString(R.string.device_scanner_none) : name);
        findViewById(R.id.scannerForgetButton).setEnabled(address != null);

        SwitchCompat connectSwitch = findViewById(R.id.scannerConnectSwitch);
        connectSwitch.setEnabled(address != null);
        connectSwitch.setChecked(connected);

        TextView status = findViewById(R.id.scannerStatus);
        String text;
        if (service.getLink().isReady()) {
            text =
                    getString(
                            R.string.device_scanner_status_connected,
                            service.getLink().getBoard(),
                            service.getLink().getFirmware());
            String last = service.getLink().getLastSection();
            if (last != null) {
                text += "\n" + getString(R.string.device_scanner_status_last_section, last);
            }
        } else {
            text = getString(R.string.device_scanner_status_disconnected);
        }
        status.setText(text);

        findViewById(R.id.scannerTestButtons)
                .setVisibility(GeneralPreferences.isDevModeOn() ? View.VISIBLE : View.GONE);
        findViewById(R.id.scannerSendTestButton)
                .setEnabled(service.getConnection() instanceof TestScannerConnection);
    }

    private class ScannerStatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateScanner();
        }
    }

    private class StateChangeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            updateStatuses();
        }
    }

    private class ScanReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if (device == null) {
                    Log.device(R.string.device_scan_device_not_found);
                    return;
                }

                try {
                    String name = device.getName();
                    // scanners and instruments are each only taken by their own search
                    if (findingScanner) {
                        if (ScannerType.isScanner(name)) {
                            BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                            Log.device(R.string.device_scanner_found, name);
                            GeneralPreferences.setRememberedScanner(device.getAddress(), name);
                            updateScanner();
                        }
                        return;
                    } else if (ScannerType.isScanner(name)) {
                        return;
                    }
                    InstrumentType instrumentType = InstrumentType.byName(name);
                    if (instrumentType.isUsable()) {
                        Log.device(R.string.device_scan_detected, instrumentType.describe());
                        if (instrumentType.needsBond()) {
                            pair(device);
                        } else {
                            remember(device, name);
                        }
                        BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    } else {
                        Log.device(
                                R.string.device_pairing_incompatible,
                                name == null ? getString(R.string.device_unnamed) : name);
                    }

                } catch (SecurityException e) {
                    Log.e(e);
                    Log.device(R.string.device_access_failure);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                findingScanner = false;
                Log.device(R.string.device_scan_end);
            }
        }
    }

    private class DeviceLogUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            update();
        }

        public void update() {
            TextView logView = findViewById(R.id.deviceLog);
            final ScrollView scrollView = findViewById(R.id.scrollView);
            LogUpdateReceiver.update(Log.LogType.DEVICE, scrollView, logView);
            scrollView.postDelayed(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN), 1000);
        }
    }
}
