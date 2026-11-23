package org.hwyl.sexytopo.model.scan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hwyl.sexytopo.model.survey.Station;

/**
 * A survey's scans, one for each station that has one. Held against the Station rather than its
 * name, so a scan follows its station through a rename. Lines that match no station, or can't be
 * read, are kept as they are so nothing in the file is lost.
 */
public class SurveyScans {

    private final Map<Station, SectionScan> stationsToScans = new LinkedHashMap<>();
    private final List<String> unmatchedLines = new ArrayList<>();

    public SectionScan get(Station station) {
        return stationsToScans.get(station);
    }

    /** Sets the station's scan, returning the one it replaces or null. */
    public SectionScan put(Station station, SectionScan scan) {
        return stationsToScans.put(station, scan);
    }

    /** The station a scan is held against, or null if none. */
    public Station findStation(String scanName) {
        for (Map.Entry<Station, SectionScan> entry : stationsToScans.entrySet()) {
            if (entry.getValue().getScanName().equals(scanName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public SectionScan remove(Station station) {
        return stationsToScans.remove(station);
    }

    public Map<Station, SectionScan> getStationsToScans() {
        return Collections.unmodifiableMap(stationsToScans);
    }

    public void addUnmatchedLine(String line) {
        unmatchedLines.add(line);
    }

    public List<String> getUnmatchedLines() {
        return Collections.unmodifiableList(unmatchedLines);
    }

    public boolean isEmpty() {
        return stationsToScans.isEmpty() && unmatchedLines.isEmpty();
    }
}
