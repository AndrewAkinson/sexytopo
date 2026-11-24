package org.hwyl.sexytopo.comms.scanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hwyl.sexytopo.R;
import org.hwyl.sexytopo.model.survey.Survey;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * SexyTopo's side of the conversation with a cross-section scanner (CrossSectionScanner
 * docs/LINK.md), whatever connection it comes over. Everything here runs on the main thread, as it
 * changes the survey.
 */
public class ScannerLink {

    public static final long PART_FRAME_TIMEOUT_MS = 10000;

    private static final String FIRMWARE_TAG = "firmware";
    private static final String BOARD_TAG = "board";

    /** What the link needs from the app around it. */
    public interface Host {
        Survey getSurvey();

        String getAppVersion();

        /** Whether new vertical sections go on the elevation rather than the plan. */
        boolean isVerticalSectionOnElevation();

        /** Milliseconds from any fixed point, for timing out part frames. */
        long now();

        /** The survey has been changed by the scanner, so views redraw and it's autosaved. */
        void onSurveyChanged();

        void onConflict(Conflict conflict);

        void onStatusChanged();

        void log(int stringId, Object... args);
    }

    /** A drawing the scanner sent for a station whose drawing has been edited here since. */
    public static class Conflict {
        public final String scanName;
        public final String stationName;
        private final ScannerSession.Outcome outcome;

        private Conflict(String scanName, String stationName, ScannerSession.Outcome outcome) {
            this.scanName = scanName;
            this.stationName = stationName;
            this.outcome = outcome;
        }
    }

    private final Host host;
    private final ScannerFrames.Reader reader = new ScannerFrames.Reader();
    private final StationWatcher watcher = new StationWatcher();
    private final List<Conflict> conflicts = new ArrayList<>();

    private ScannerConnection connection = null;
    private boolean ready = false;
    private long lastBytesAt = 0;
    private String firmware = null;
    private String board = null;
    private String lastSection = null;

    public ScannerLink(Host host) {
        this.host = host;
    }

    public void onConnected(ScannerConnection connection) {
        this.connection = connection;
        ready = false;
        reader.clear();
        send(() -> ScannerMessages.hello(host.getAppVersion()));
        host.onStatusChanged();
    }

    /** Ignored if from a connection already replaced by another. */
    public void onDisconnected(ScannerConnection from) {
        if (from != connection) {
            return;
        }
        connection = null;
        ready = false;
        reader.clear();
        host.onStatusChanged();
    }

    public void onBytes(byte[] data) {
        long now = host.now();
        if (reader.waiting() > 0 && now - lastBytesAt > PART_FRAME_TIMEOUT_MS) {
            reader.clear();
        }
        lastBytesAt = now;
        for (ScannerFrames.Result result : reader.feed(data)) {
            if (result.message == null) {
                host.log(R.string.device_scanner_frame_dropped, result.dropped);
                send(() -> ScannerSession.onDropped(result.dropped));
            } else {
                handle(result.message);
            }
        }
    }

    /** Called whenever the survey changes, to keep the scanner's stations up to date. */
    public void onSurveyUpdated() {
        if (!ready) {
            return;
        }
        try {
            for (JSONObject message : watcher.changes(host.getSurvey())) {
                send(() -> message);
            }
        } catch (JSONException e) {
            host.log(R.string.device_scanner_error, e.getMessage());
        }
    }

    private void handle(JSONObject message) {
        String type = message.optString(ScannerMessages.TYPE);
        if (ScannerMessages.HELLO.equals(type)) {
            onHello(message);
        } else if (ScannerMessages.SECTION.equals(type)) {
            onSection(message);
        }
        // anything else is from a later protocol, so ignored
    }

    private void onHello(JSONObject message) {
        int protocol = message.optInt(ScannerMessages.PROTOCOL_TAG, -1);
        if (protocol != ScannerMessages.PROTOCOL) {
            host.log(R.string.device_scanner_protocol_mismatch, protocol, ScannerMessages.PROTOCOL);
            if (connection != null) {
                connection.disconnect();
            }
            return;
        }
        firmware = message.optString(FIRMWARE_TAG);
        board = message.optString(BOARD_TAG);
        ready = true;
        host.log(R.string.device_scanner_hello, board, firmware);
        send(() -> watcher.start(host.getSurvey()));
        host.onStatusChanged();
    }

    private void onSection(JSONObject message) {
        if (!ready) {
            return;
        }
        try {
            ScannerSession.Outcome outcome =
                    new ScannerSession(host.getSurvey(), host.isVerticalSectionOnElevation())
                            .onSection(message);
            JSONObject reply = outcome.reply;
            send(() -> reply);
            String scan = reply.optString(ScannerMessages.SCAN_TAG);
            String station = stationOf(message);
            String result = reply.getString(ScannerMessages.RESULT_TAG);
            if (ScannerSession.OK.equals(result)) {
                lastSection = scan;
                host.log(R.string.device_scanner_section_taken, scan, station);
                host.onSurveyChanged();
            } else if (outcome.isConflict()) {
                conflicts.add(new Conflict(scan, station, outcome));
                host.log(R.string.device_scanner_section_conflict, scan, station);
                host.onConflict(conflicts.get(conflicts.size() - 1));
            } else {
                host.log(
                        R.string.device_scanner_section_refused,
                        scan,
                        reply.optString(ScannerMessages.REASON_TAG));
            }
            host.onStatusChanged();
        } catch (JSONException e) {
            host.log(R.string.device_scanner_error, e.getMessage());
        }
    }

    private static String stationOf(JSONObject section) {
        JSONObject scan = section.optJSONObject(ScannerMessages.SCAN_TAG);
        return scan == null ? "" : scan.optString(ScannerMessages.STATION_TAG);
    }

    /**
     * Settles a conflict. Using the scanner's drawing puts it in the section and tells the scanner,
     * if still connected; keeping SexyTopo's leaves the scan as a conflict on the scanner.
     */
    public void resolve(Conflict conflict, boolean useScanners) {
        // two screens may have asked, so only the first answer counts
        if (!conflicts.remove(conflict) || !useScanners) {
            return;
        }
        try {
            JSONObject reply = conflict.outcome.takeScannersDrawing();
            send(() -> reply);
            lastSection = conflict.scanName;
            host.onSurveyChanged();
            host.onStatusChanged();
        } catch (JSONException e) {
            host.log(R.string.device_scanner_error, e.getMessage());
        }
    }

    public List<Conflict> getConflicts() {
        return Collections.unmodifiableList(new ArrayList<>(conflicts));
    }

    public boolean isConnected() {
        return connection != null && connection.isConnected();
    }

    /** Connected and past hello, so sections are being taken. */
    public boolean isReady() {
        return ready && isConnected();
    }

    public String getFirmware() {
        return firmware;
    }

    public String getBoard() {
        return board;
    }

    public String getLastSection() {
        return lastSection;
    }

    private interface MessageSource {
        JSONObject get() throws JSONException;
    }

    private void send(MessageSource source) {
        if (connection == null) {
            return;
        }
        try {
            connection.send(ScannerFrames.toFrame(source.get()));
        } catch (JSONException e) {
            host.log(R.string.device_scanner_error, e.getMessage());
        }
    }
}
