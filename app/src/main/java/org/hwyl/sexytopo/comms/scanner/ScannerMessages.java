package org.hwyl.sexytopo.comms.scanner;

import java.util.LinkedHashMap;
import java.util.Map;
import org.hwyl.sexytopo.control.io.basic.SketchJsonTranslater;
import org.hwyl.sexytopo.model.scan.SectionScan;
import org.hwyl.sexytopo.model.sketch.Sketch;
import org.hwyl.sexytopo.model.survey.Station;
import org.hwyl.sexytopo.model.survey.Survey;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** The messages SexyTopo sends the cross-section scanner, and the names in those it receives. */
public class ScannerMessages {

    public static final int PROTOCOL = 2;

    public static final String TYPE = "type";
    public static final String HELLO = "hello";
    public static final String SURVEY = "survey";
    public static final String STATION_ADDED = "station_added";
    public static final String STATION_RENAMED = "station_renamed";
    public static final String STATION_DELETED = "station_deleted";
    public static final String SCAN_STATION = "scan_station";
    public static final String SECTION = "section";
    public static final String REPLY = "reply";
    public static final String DRAWING = "drawing";
    public static final String DRAWING_REPLY = "drawing_reply";
    public static final String SKETCH_FORMAT = "XScan sketch 1";
    public static final String FORMAT_TAG = "format";

    public static final String PROTOCOL_TAG = "protocol";
    public static final String APP_TAG = "app";
    public static final String NAME_TAG = "name";
    public static final String STATIONS_TAG = "stations";
    public static final String SCANS_TAG = "scans";
    public static final String SCAN_TAG = "scan";
    public static final String STATION_TAG = "station";
    public static final String FROM_TAG = "from";
    public static final String TO_TAG = "to";
    public static final String SKETCH_TAG = "sketch";
    public static final String BASED_ON_TAG = "based_on";
    public static final String REVISION_TAG = "revision";
    public static final String RESULT_TAG = "result";
    public static final String REASON_TAG = "reason";

    public static JSONObject hello(String appVersion) throws JSONException {
        return message(HELLO).put(PROTOCOL_TAG, PROTOCOL).put(APP_TAG, "SexyTopo " + appVersion);
    }

    /** The survey's name, stations oldest first, and each scan with its station's name. */
    public static JSONObject survey(Survey survey) throws JSONException {
        JSONArray stations = new JSONArray();
        for (Station station : survey.getAllStationsInChronoOrder()) {
            stations.put(station.getName());
        }
        JSONArray scans = new JSONArray();
        for (Map.Entry<String, Station> entry : scanStations(survey).entrySet()) {
            scans.put(
                    new JSONObject()
                            .put(SCAN_TAG, entry.getKey())
                            .put(STATION_TAG, entry.getValue().getName()));
        }
        return message(SURVEY)
                .put(NAME_TAG, survey.getName())
                .put(STATIONS_TAG, stations)
                .put(SCANS_TAG, scans);
    }

    /**
     * Each scan by name with the station it is on. A scan counts as on its station only while the
     * station is in the survey and has a cross-section, so undoing the section takes it off.
     */
    static Map<String, Station> scanStations(Survey survey) {
        Map<String, Station> scanStations = new LinkedHashMap<>();
        for (Map.Entry<Station, SectionScan> entry :
                survey.getScans().getStationsToScans().entrySet()) {
            Station station = entry.getKey();
            if (survey.getStationByName(station.getName()) == station
                    && (survey.getPlanSketch().getCrossSectionDetail(station) != null
                            || survey.getElevationSketch().getCrossSectionDetail(station)
                                    != null)) {
                scanStations.put(entry.getValue().getScanName(), station);
            }
        }
        return scanStations;
    }

    public static JSONObject stationAdded(Station station) throws JSONException {
        return message(STATION_ADDED).put(NAME_TAG, station.getName());
    }

    public static JSONObject stationRenamed(String from, String to) throws JSONException {
        return message(STATION_RENAMED).put(FROM_TAG, from).put(TO_TAG, to);
    }

    public static JSONObject stationDeleted(String name) throws JSONException {
        return message(STATION_DELETED).put(NAME_TAG, name);
    }

    /** The station a scan is now on here, or none if station is null. */
    public static JSONObject scanStation(String scan, String station) throws JSONException {
        JSONObject message = message(SCAN_STATION).put(SCAN_TAG, scan);
        return station == null ? message : message.put(STATION_TAG, station);
    }

    /** A drawing edited here, for the scanner to take if it hasn't changed since basedOn. */
    public static JSONObject drawing(String scan, int revision, int basedOn, Sketch drawing)
            throws JSONException {
        JSONObject sketch =
                new JSONObject()
                        .put(FORMAT_TAG, SKETCH_FORMAT)
                        .put(SCAN_TAG, scan)
                        .put(REVISION_TAG, revision)
                        .put(SKETCH_TAG, SketchJsonTranslater.toSubSketchJson(drawing));
        return message(DRAWING)
                .put(SCAN_TAG, scan)
                .put(REVISION_TAG, revision)
                .put(BASED_ON_TAG, basedOn)
                .put(SKETCH_TAG, sketch);
    }

    /** A reply to a section. scan is null when the section couldn't be read. */
    public static JSONObject reply(String scan, int revision, String result, String reason)
            throws JSONException {
        JSONObject reply = message(REPLY);
        if (scan != null) {
            reply.put(SCAN_TAG, scan).put(REVISION_TAG, revision);
        }
        reply.put(RESULT_TAG, result);
        if (reason != null) {
            reply.put(REASON_TAG, reason);
        }
        return reply;
    }

    private static JSONObject message(String type) throws JSONException {
        return new JSONObject().put(TYPE, type);
    }
}
