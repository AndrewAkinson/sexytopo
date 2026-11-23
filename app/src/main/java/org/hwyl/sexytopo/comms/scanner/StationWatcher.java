package org.hwyl.sexytopo.comms.scanner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hwyl.sexytopo.model.survey.Station;
import org.hwyl.sexytopo.model.survey.Survey;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Works out the messages that keep the scanner's list of stations up to date, by comparing the
 * survey with a copy of its stations taken last time. Station objects are compared, not names, so a
 * rename is never taken for a delete and an add.
 */
public class StationWatcher {

    private Survey survey = null;
    private Map<Station, String> names = new LinkedHashMap<>();

    /** The survey message, starting the copy afresh. */
    public JSONObject start(Survey survey) throws JSONException {
        this.survey = survey;
        this.names = namesOf(survey);
        return ScannerMessages.survey(survey);
    }

    /**
     * The messages for what has changed since last time: the whole survey if another one has been
     * opened, otherwise deletions, then renames, then additions, so a name freed by one change can
     * be taken by the next.
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
