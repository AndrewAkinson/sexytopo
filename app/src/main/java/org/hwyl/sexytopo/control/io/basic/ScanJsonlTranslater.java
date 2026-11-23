package org.hwyl.sexytopo.control.io.basic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hwyl.sexytopo.R;
import org.hwyl.sexytopo.control.Log;
import org.hwyl.sexytopo.model.scan.SectionScan;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Reads a survey's cross-section scanner file: one scan per line, in the scanner's exchange format
 */
public class ScanJsonlTranslater {

    public static final String FORMAT = "XScan scan 1";

    private static final String FORMAT_TAG = "format";
    private static final String SCAN_TAG = "scan";
    private static final String STATION_TAG = "station";
    private static final String FACING_TAG = "facing";
    private static final String POINTS_TAG = "points";
    private static final String FACING_BACK = "back";

    /** The scans by station name. Lines that can't be read are logged and skipped. */
    public static Map<String, SectionScan> toScans(String text) {
        Map<String, SectionScan> scans = new HashMap<>();
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            try {
                SectionScan scan = toScan(new JSONObject(line));
                // the last line for a station is its latest scan
                scans.put(scan.getStationName(), scan);
            } catch (JSONException | IllegalArgumentException e) {
                Log.e(R.string.file_load_scan_line_error, i + 1, e.getMessage());
            }
        }
        return scans;
    }

    static SectionScan toScan(JSONObject json) throws JSONException {
        String format = json.getString(FORMAT_TAG);
        if (!format.equals(FORMAT)) {
            throw new IllegalArgumentException(format);
        }
        SectionScan.Facing facing =
                FACING_BACK.equals(json.optString(FACING_TAG))
                        ? SectionScan.Facing.BACK
                        : SectionScan.Facing.ON;

        JSONArray array = json.getJSONArray(POINTS_TAG);
        List<SectionScan.Point> points = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONArray point = array.getJSONArray(i);
            points.add(
                    new SectionScan.Point(
                            (float) point.getDouble(0),
                            (float) point.getDouble(1),
                            point.getInt(2)));
        }
        return new SectionScan(
                json.getString(SCAN_TAG), json.getString(STATION_TAG), facing, points);
    }
}
