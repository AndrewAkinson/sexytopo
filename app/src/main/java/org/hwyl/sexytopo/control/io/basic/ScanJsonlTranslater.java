package org.hwyl.sexytopo.control.io.basic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hwyl.sexytopo.R;
import org.hwyl.sexytopo.control.Log;
import org.hwyl.sexytopo.model.scan.SectionScan;
import org.hwyl.sexytopo.model.scan.SurveyScans;
import org.hwyl.sexytopo.model.survey.Station;
import org.hwyl.sexytopo.model.survey.Survey;
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
    // SexyTopo's own record of what it took from the scanner, ignored by other readers
    private static final String SEXYTOPO_TAG = "sexytopo";
    private static final String REVISION_TAG = "revision";
    private static final String SKETCH_HASH_TAG = "sketch_hash";

    /**
     * The survey's scans, matched to its stations by name. A line that matches no station, can't be
     * read, or is replaced by a later line for the same station is kept as it is.
     */
    public static SurveyScans toScans(Survey survey, String text) {
        SurveyScans scans = new SurveyScans();
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            try {
                JSONObject json = new JSONObject(line);
                SectionScan scan = toScan(json, line);
                Station station = survey.getStationByName(json.getString(STATION_TAG));
                if (station == null) {
                    scans.addUnmatchedLine(line);
                    continue;
                }
                // the last line for a station is its latest scan
                SectionScan replaced = scans.put(station, scan);
                if (replaced != null) {
                    scans.addUnmatchedLine(replaced.getSource());
                }
            } catch (JSONException | IllegalArgumentException e) {
                Log.e(R.string.file_load_scan_line_error, i + 1, e.getMessage());
                scans.addUnmatchedLine(line);
            }
        }
        return scans;
    }

    /**
     * The scanner file text: unmatched lines as they were, then each scan with its station's
     * current name. Unmatched lines go first so a station's own scan stays its last line.
     */
    public static String toText(SurveyScans scans) throws JSONException {
        StringBuilder text = new StringBuilder();
        for (String line : scans.getUnmatchedLines()) {
            text.append(line).append('\n');
        }
        for (Map.Entry<Station, SectionScan> entry : scans.getStationsToScans().entrySet()) {
            JSONObject json = new JSONObject(entry.getValue().getSource());
            json.put(STATION_TAG, entry.getKey().getName());
            text.append(json).append('\n');
        }
        return text.toString();
    }

    /** A scan from one line of the exchange format, which must have a station. */
    public static SectionScan toScan(JSONObject json) throws JSONException {
        return toScan(json, json.toString());
    }

    private static SectionScan toScan(JSONObject json, String source) throws JSONException {
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
        SectionScan.Received received = null;
        JSONObject sexyTopo = json.optJSONObject(SEXYTOPO_TAG);
        if (sexyTopo != null) {
            received =
                    new SectionScan.Received(
                            sexyTopo.getInt(REVISION_TAG), sexyTopo.optString(SKETCH_HASH_TAG));
        }
        json.getString(STATION_TAG);
        return new SectionScan(json.getString(SCAN_TAG), facing, points, received, source);
    }

    /** The scan line with a record of the drawing revision SexyTopo took and its hash. */
    public static SectionScan withReceived(JSONObject json, int revision, String sketchHash)
            throws JSONException {
        JSONObject copy = new JSONObject(json.toString());
        JSONObject sexyTopo = new JSONObject();
        sexyTopo.put(REVISION_TAG, revision);
        sexyTopo.put(SKETCH_HASH_TAG, sketchHash);
        copy.put(SEXYTOPO_TAG, sexyTopo);
        return toScan(copy);
    }
}
