package org.hwyl.sexytopo.comms.scanner;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import org.hwyl.sexytopo.SexyTopoConstants;
import org.hwyl.sexytopo.control.Log;
import org.hwyl.sexytopo.control.SurveyManager;
import org.hwyl.sexytopo.control.activity.SexyTopoActivity;
import org.hwyl.sexytopo.model.survey.Survey;

/**
 * Holds the link to the cross-section scanner for as long as the app runs, beside the instrument's
 * connection, and connects it to the rest of the app.
 */
public class ScannerService implements ScannerLink.Host {

    // remembered in place of a Bluetooth address when developer mode's test scanner is chosen
    public static final String TEST_ADDRESS = "test";

    @SuppressLint("StaticFieldLeak") // only the application context is kept
    private static ScannerService instance;

    private final Context context;
    private final ScannerLink link;
    private final Handler mainThread = new Handler(Looper.getMainLooper());
    private ScannerConnection connection = null;

    public static synchronized ScannerService getInstance(Context context) {
        if (instance == null) {
            instance = new ScannerService(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * The service if it has been started, so screens can check for conflicts without starting it.
     */
    public static ScannerService getExisting() {
        return instance;
    }

    private ScannerService(Context context) {
        this.context = context;
        this.link = new ScannerLink(this);
        LocalBroadcastManager.getInstance(context)
                .registerReceiver(
                        new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                link.onSurveyUpdated();
                            }
                        },
                        new IntentFilter(SexyTopoConstants.SURVEY_UPDATED_EVENT));
    }

    public ScannerLink getLink() {
        return link;
    }

    public ScannerConnection getConnection() {
        return connection;
    }

    /** Connects to the scanner at this address (or the test scanner), closing any other first. */
    public void connect(String address) {
        disconnect();
        connection = newConnection(address);
        if (connection != null) {
            connection.connect();
        }
    }

    private ScannerConnection newConnection(String address) {
        if (TEST_ADDRESS.equals(address)) {
            return new TestScannerConnection(this);
        }
        return null; // Bluetooth comes in Link 4b
    }

    public void disconnect() {
        if (connection != null) {
            connection.disconnect();
            connection = null;
        }
    }

    /** Connections call the link through this, as their callbacks come on other threads. */
    public void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainThread.post(runnable);
        }
    }

    public Context getContext() {
        return context;
    }

    @Override
    public Survey getSurvey() {
        return SurveyManager.getCurrentSurvey();
    }

    @Override
    public String getAppVersion() {
        return SexyTopoActivity.getVersionName(context);
    }

    @Override
    public long now() {
        return SystemClock.elapsedRealtime();
    }

    @Override
    public void onSurveyChanged() {
        SurveyManager manager = SurveyManager.getInstance(context);
        manager.broadcastSurveyUpdated();
        manager.autosave();
    }

    @Override
    public void onConflict(ScannerLink.Conflict conflict) {
        broadcast(SexyTopoConstants.SCANNER_CONFLICT_EVENT);
    }

    @Override
    public void onStatusChanged() {
        broadcast(SexyTopoConstants.SCANNER_STATUS_EVENT);
    }

    @Override
    public void log(int stringId, Object... args) {
        Log.device(stringId, args);
    }

    private void broadcast(String event) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(event));
    }
}
