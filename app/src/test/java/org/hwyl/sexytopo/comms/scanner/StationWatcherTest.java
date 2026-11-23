package org.hwyl.sexytopo.comms.scanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.hwyl.sexytopo.control.util.SurveyUpdater;
import org.hwyl.sexytopo.model.survey.Leg;
import org.hwyl.sexytopo.model.survey.Survey;
import org.hwyl.sexytopo.testutils.BasicTestSurveyCreator;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

/** Straight north survey: stations 1, 2, 3 and 4. */
public class StationWatcherTest {

    private static List<String> describe(List<JSONObject> messages) {
        List<String> described = new ArrayList<>();
        for (JSONObject message : messages) {
            String type = message.optString("type");
            if (type.equals("station_renamed")) {
                described.add(
                        type + " " + message.optString("from") + " " + message.optString("to"));
            } else if (type.equals("survey")) {
                described.add(type);
            } else {
                described.add(type + " " + message.optString("name"));
            }
        }
        return described;
    }

    private interface Change {
        void apply(Survey survey);
    }

    @Test
    public void testChangesFound() throws Exception {
        // what is done to the survey, the messages expected
        Object[][] cases = {
            {"nothing", (Change) s -> {}, new String[] {}},
            {
                "add",
                (Change) s -> SurveyUpdater.updateWithNewStation(s, new Leg(5, 0, 0)),
                new String[] {"station_added 5"}
            },
            {
                "rename",
                (Change) s -> SurveyUpdater.renameStation(s, s.getStationByName("2"), "2a"),
                new String[] {"station_renamed 2 2a"}
            },
            {
                "delete",
                (Change) s -> SurveyUpdater.deleteStation(s, s.getStationByName("4")),
                new String[] {"station_deleted 4"}
            },
            {
                "delete then reuse the name",
                (Change)
                        s -> {
                            SurveyUpdater.deleteStation(s, s.getStationByName("4"));
                            SurveyUpdater.renameStation(s, s.getStationByName("3"), "4");
                        },
                new String[] {"station_deleted 4", "station_renamed 3 4"}
            },
            {
                "rename and add",
                (Change)
                        s -> {
                            SurveyUpdater.renameStation(s, s.getStationByName("2"), "2a");
                            SurveyUpdater.updateWithNewStation(s, new Leg(5, 0, 0));
                        },
                new String[] {"station_renamed 2 2a", "station_added 5"}
            },
        };
        for (Object[] c : cases) {
            Survey survey = BasicTestSurveyCreator.createStraightNorth();
            StationWatcher watcher = new StationWatcher();
            watcher.start(survey);
            ((Change) c[1]).apply(survey);
            List<String> expected = new ArrayList<>(Arrays.asList((String[]) c[2]));
            Assert.assertEquals((String) c[0], expected, describe(watcher.changes(survey)));
            Assert.assertTrue((String) c[0], watcher.changes(survey).isEmpty());
        }
    }

    @Test
    public void testAnotherSurveySendsTheWholeSurvey() throws Exception {
        StationWatcher watcher = new StationWatcher();
        watcher.start(BasicTestSurveyCreator.createStraightNorth());
        Survey other = BasicTestSurveyCreator.createStraightNorth();
        List<JSONObject> messages = watcher.changes(other);
        Assert.assertEquals(1, messages.size());
        Assert.assertEquals("survey", messages.get(0).getString("type"));
        Assert.assertEquals(4, messages.get(0).getJSONArray("stations").length());
        Assert.assertTrue(watcher.changes(other).isEmpty());
    }
}
