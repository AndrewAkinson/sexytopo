package org.hwyl.sexytopo.control.io.thirdparty.survextherion;

import org.hwyl.sexytopo.model.survey.Trip;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public enum SurveyFormat {

    SURVEX {
        @Override
        public boolean parseExploTeamLine(
                String effective, Map<String, List<Trip.Role>> teamMap) {
            return false;
        }
    },

    THERION {
        @Override
        public boolean parseExploTeamLine(
                String effective, Map<String, List<Trip.Role>> teamMap) {
            if (!effective.startsWith("explo-team ")) {
                return false;
            }
            String name = SurvexTherionImporter.extractQuotedValue(
                    effective, "explo-team ");
            if (!name.isEmpty()) {
                List<Trip.Role> roles = teamMap.get(name);
                if (roles == null) {
                    roles = new ArrayList<>();
                    teamMap.put(name, roles);
                }
                if (!roles.contains(Trip.Role.EXPLORATION)) {
                    roles.add(Trip.Role.EXPLORATION);
                }
            }
            return true;
        }
    };

    /**
     * Try to parse a team-related line during import.
     * Returns true if the line was consumed (e.g. explo-team in Therion).
     */
    public abstract boolean parseExploTeamLine(
            String effective, Map<String, List<Trip.Role>> teamMap);
}
