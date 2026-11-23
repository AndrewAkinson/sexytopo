package org.hwyl.sexytopo.comms.scanner;

import java.util.HashMap;
import java.util.Map;
import org.hwyl.sexytopo.R;
import org.hwyl.sexytopo.model.survey.Station;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Developer mode's pretend scanner. It answers hello, and on request sends a section for the active
 * station, an oval passage with a square drawn round it that grows with each revision, so the whole
 * link but the radio can be tried, on the emulator too.
 */
public class TestScannerConnection implements ScannerConnection {

    private static final int POINTS = 360;

    private final ScannerService service;
    private final ScannerFrames.Reader reader = new ScannerFrames.Reader();
    private final Map<String, Integer> acknowledged = new HashMap<>();
    private boolean connected = false;

    public TestScannerConnection(ScannerService service) {
        this.service = service;
    }

    @Override
    public void connect() {
        connected = true;
        service.runOnMainThread(() -> service.getLink().onConnected(this));
    }

    @Override
    public void disconnect() {
        connected = false;
        service.runOnMainThread(() -> service.getLink().onDisconnected(this));
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void send(byte[] frame) {
        for (ScannerFrames.Result result : reader.feed(frame)) {
            JSONObject message = result.message;
            if (message == null) {
                continue;
            }
            String type = message.optString(ScannerMessages.TYPE);
            if (ScannerMessages.HELLO.equals(type)) {
                reply(hello());
            } else if (ScannerMessages.REPLY.equals(type)
                    && ScannerSession.OK.equals(message.optString(ScannerMessages.RESULT_TAG))) {
                acknowledged.put(
                        message.optString(ScannerMessages.SCAN_TAG),
                        message.optInt(ScannerMessages.REVISION_TAG));
            }
        }
    }

    /** Sends the next revision of a section for the survey's active station. */
    public void sendTestSection() {
        Station station = service.getSurvey().getActiveStation();
        String scan = "test-" + station.getName();
        int basedOn = acknowledged.containsKey(scan) ? acknowledged.get(scan) : 0;
        try {
            reply(section(scan, station.getName(), basedOn + 1, basedOn));
        } catch (JSONException e) {
            service.log(R.string.device_scanner_error, e.getMessage());
        }
    }

    private void reply(JSONObject message) {
        byte[] frame = ScannerFrames.toFrame(message);
        // as if it came over the air, after SexyTopo has finished what it's doing
        service.runOnMainThread(
                () -> {
                    if (connected) {
                        service.getLink().onBytes(frame);
                    }
                });
    }

    private static JSONObject hello() {
        try {
            return new JSONObject()
                    .put(ScannerMessages.TYPE, ScannerMessages.HELLO)
                    .put(ScannerMessages.PROTOCOL_TAG, ScannerMessages.PROTOCOL)
                    .put("firmware", "test")
                    .put("board", "TEST00");
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JSONObject section(String scan, String station, int revision, int basedOn)
            throws JSONException {
        JSONArray points = new JSONArray();
        for (int i = 0; i < POINTS; i++) {
            double degrees = i * 360.0 / POINTS;
            double radians = Math.toRadians(degrees);
            // an oval 4 m wide and 3 m high
            double x = 2000 * Math.sin(radians);
            double y = 1500 * Math.cos(radians);
            points.put(new JSONArray().put(degrees).put(Math.round(Math.hypot(x, y))).put(40));
        }
        JSONObject scanJson =
                new JSONObject()
                        .put("format", "XScan scan 1")
                        .put(ScannerMessages.SCAN_TAG, scan)
                        .put(ScannerMessages.STATION_TAG, station)
                        .put("orientation", "vertical")
                        .put("facing", "on")
                        .put("samples", POINTS)
                        .put("returns", POINTS)
                        .put("points", points);

        double half = 0.5 + 0.25 * revision;
        JSONArray square = new JSONArray();
        double[][] corners = {
            {-half, -half}, {half, -half}, {half, half}, {-half, half}, {-half, -half}
        };
        for (double[] corner : corners) {
            square.put(new JSONObject().put("x", corner[0]).put("y", corner[1]));
        }
        JSONObject sketch =
                new JSONObject()
                        .put("format", "XScan sketch 1")
                        .put(ScannerMessages.SCAN_TAG, scan)
                        .put(ScannerMessages.REVISION_TAG, revision)
                        .put(
                                ScannerMessages.SKETCH_TAG,
                                new JSONObject()
                                        .put(
                                                "paths",
                                                new JSONArray()
                                                        .put(
                                                                new JSONObject()
                                                                        .put("colour", "BLACK")
                                                                        .put("points", square))));
        return new JSONObject()
                .put(ScannerMessages.TYPE, ScannerMessages.SECTION)
                .put(ScannerMessages.SCAN_TAG, scanJson)
                .put(ScannerMessages.SKETCH_TAG, sketch)
                .put(ScannerMessages.BASED_ON_TAG, basedOn);
    }
}
