package org.hwyl.sexytopo.comms.scanner;

import java.util.Map;
import org.hwyl.sexytopo.model.scan.SectionScan;
import org.hwyl.sexytopo.model.survey.Station;
import org.hwyl.sexytopo.model.survey.Survey;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** The messages SexyTopo sends the cross-section scanner, and the names in those it receives. */
public class ScannerMessages {

    public static final int PROTOCOL = 1;

    public static final String TYPE = "type";
    public static final String HELLO = "hello";
    public static final String SURVEY = "survey";
    public static final String STATION_ADDED = "station_added";
    public static final String STATION_RENAMED = "station_renamed";
    public static final String STATION_DELETED = "station_deleted";
    public static final String SECTION = "section";
    public static final String REPLY = "reply";

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
        for (Map.Entry<Station, SectionScan> entry :
                survey.getScans().getStationsToScans().entrySet()) {
            if (survey.getStationByName(entry.getKey().getName()) == entry.getKey()) {
                scans.put(
                        new JSONObject()
                                .put(SCAN_TAG, entry.getValue().getScanName())
                                .put(STATION_TAG, entry.getKey().getName()));
            }
        }
        return message(SURVEY)
                .put(NAME_TAG, survey.getName())
                .put(STATIONS_TAG, stations)
                .put(SCANS_TAG, scans);
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
