package org.hwyl.sexytopo.comms.scanner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hwyl.sexytopo.model.survey.Station;
import org.hwyl.sexytopo.model.survey.Survey;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Works out the messages that keep the scanner's list of stations, and the stations of its scans,
 * up to date, by comparing the survey with a copy taken last time. Station objects are compared,
 * not names, so a rename is never taken for a delete and an add.
 */
public class StationWatcher {

    private Survey survey = null;
    private Map<Station, String> names = new LinkedHashMap<>();
    private Map<String, Station> scanStations = new LinkedHashMap<>();

    /** The survey message, starting the copy afresh. */
    public JSONObject start(Survey survey) throws JSONException {
        this.survey = survey;
        this.names = namesOf(survey);
        this.scanStations = ScannerMessages.scanStations(survey);
        return ScannerMessages.survey(survey);
    }

    /**
     * The messages for what has changed since last time: the whole survey if another one has been
     * opened, otherwise deletions, then renames, then additions, so a name freed by one change can
     * be taken by the next, then scans that have changed station.
     */
    public List<JSONObject> changes(Survey current) throws JSONException {
        List<JSONObject> messages = new ArrayList<>();
        if (current != survey) {
            messages.add(start(current));
            return messages;
        }
        Map<Station, String> now = namesOf(current);
        for (Map.Entry<Station, String> before : names.entrySet()) {
            if (!now.containsKey(before.getKey())) {
                messages.add(ScannerMessages.stationDeleted(before.getValue()));
            }
        }
        for (Map.Entry<Station, String> before : names.entrySet()) {
            String name = now.get(before.getKey());
            if (name != null && !name.equals(before.getValue())) {
                messages.add(ScannerMessages.stationRenamed(before.getValue(), name));
            }
        }
        for (Station station : now.keySet()) {
            if (!names.containsKey(station)) {
                messages.add(ScannerMessages.stationAdded(station));
            }
        }
        names = now;

        Map<String, Station> scansNow = ScannerMessages.scanStations(current);
        Set<String> scanNames = new LinkedHashSet<>(scanStations.keySet());
        scanNames.addAll(scansNow.keySet());
        for (String scan : scanNames) {
            Station before = scanStations.get(scan);
            Station after = scansNow.get(scan);
            // a deleted station's scan is already taken off it by station_deleted
            boolean stationDeleted = after == null && !now.containsKey(before);
            if (after != before && !stationDeleted) {
                messages.add(
                        ScannerMessages.scanStation(scan, after == null ? null : after.getName()));
            }
        }
        scanStations = scansNow;
        return messages;
    }

    private static Map<Station, String> namesOf(Survey survey) {
        Map<Station, String> names = new LinkedHashMap<>();
        for (Station station : survey.getAllStationsInChronoOrder()) {
            names.put(station, station.getName());
        }
        return names;
    }
}
