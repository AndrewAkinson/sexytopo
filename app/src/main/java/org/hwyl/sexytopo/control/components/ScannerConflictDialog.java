package org.hwyl.sexytopo.control.components;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.appcompat.app.AlertDialog;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;
import org.hwyl.sexytopo.R;
import org.hwyl.sexytopo.SexyTopoConstants;
import org.hwyl.sexytopo.comms.scanner.ScannerLink;
import org.hwyl.sexytopo.comms.scanner.ScannerService;

/**
 * Asks, on whichever screen is showing, whether to keep SexyTopo's drawing or use the scanner's
 * when the scanner sends one for a section edited here since. One question at a time.
 */
public class ScannerConflictDialog {

    private final Activity activity;
    private final BroadcastReceiver receiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    showNext();
                }
            };
    private AlertDialog dialog = null;

    public ScannerConflictDialog(Activity activity) {
        this.activity = activity;
    }

    /** Call from onResume. */
    public void start() {
        LocalBroadcastManager.getInstance(activity)
                .registerReceiver(
                        receiver, new IntentFilter(SexyTopoConstants.SCANNER_CONFLICT_EVENT));
        showNext();
    }

    /** Call from onPause; an unanswered question is asked again on the next screen. */
    public void stop() {
        LocalBroadcastManager.getInstance(activity).unregisterReceiver(receiver);
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    private void showNext() {
        ScannerService service = ScannerService.getExisting();
        if (dialog != null || service == null || activity.isFinishing()) {
            return;
        }
        List<ScannerLink.Conflict> conflicts = service.getLink().getConflicts();
        if (conflicts.isEmpty()) {
            return;
        }
        ScannerLink.Conflict conflict = conflicts.get(0);
        dialog =
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.device_scanner_conflict_title)
                        .setMessage(
                                activity.getString(
                                        R.string.device_scanner_conflict_message,
                                        conflict.stationName,
                                        conflict.scanName))
                        .setCancelable(false)
                        .setNegativeButton(
                                R.string.device_scanner_conflict_keep,
                                (d, which) -> answer(service, conflict, false))
                        .setPositiveButton(
                                R.string.device_scanner_conflict_use,
                                (d, which) -> answer(service, conflict, true))
                        .show();
    }

    private void answer(ScannerService service, ScannerLink.Conflict conflict, boolean use) {
        dialog = null;
        service.getLink().resolve(conflict, use);
        showNext();
    }
}
