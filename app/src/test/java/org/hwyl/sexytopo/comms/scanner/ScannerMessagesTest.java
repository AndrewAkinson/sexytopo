package org.hwyl.sexytopo.comms.scanner;

import org.hwyl.sexytopo.control.io.basic.ScanJsonlTranslater;
import org.hwyl.sexytopo.control.util.SurveyUpdater;
import org.hwyl.sexytopo.model.geometry.Coord2D;
import org.hwyl.sexytopo.model.sketch.CrossSection;
import org.hwyl.sexytopo.model.survey.Survey;
import org.hwyl.sexytopo.testutils.BasicTestSurveyCreator;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public class ScannerMessagesTest {

    private static final String SCAN_AT_2 =
            "{\"format\":\"XScan scan 1\",\"scan\":\"b1\",\"station\":\"2\",\"points\":[]}";
    private static final String SCAN_AT_4 =
            "{\"format\":\"XScan scan 1\",\"scan\":\"b2\",\"station\":\"4\",\"points\":[]}";

    @Test
    public void testSurveyGivesStationsOldestFirstAndScansWithCurrentNames() throws Exception {
        Survey survey = BasicTestSurveyCreator.createStraightNorth();
        survey.setScans(ScanJsonlTranslater.toScans(survey, SCAN_AT_2 + "\n" + SCAN_AT_4));
        for (String name : new String[] {"2", "4"}) {
            survey.getPlanSketch()
                    .addCrossSection(
                            new CrossSection(survey.getStationByName(name), 0), Coord2D.ORIGIN);
        }
        SurveyUpdater.renameStation(survey, survey.getStationByName("2"), "2a");
        SurveyUpdater.deleteStation(survey, survey.getStationByName("4"));

        JSONObject message = ScannerMessages.survey(survey);
        Assert.assertEquals("survey", message.getString("type"));
        Assert.assertEquals("[\"1\",\"2a\",\"3\"]", message.getJSONArray("stations").toString());
        // the deleted station's scan stays in SexyTopo but isn't offered to the scanner
        Assert.assertEquals(1, message.getJSONArray("scans").length());
        JSONObject scan = message.getJSONArray("scans").getJSONObject(0);
        Assert.assertEquals("b1", scan.getString("scan"));
        Assert.assertEquals("2a", scan.getString("station"));
    }

    @Test
    public void testMessagesHaveTheirFields() throws Exception {
        // message, expected type, a field and its value
        Object[][] cases = {
            {ScannerMessages.hello("1.15.0"), "hello", "app", "SexyTopo 1.15.0"},
            {ScannerMessages.hello("1.15.0"), "hello", "protocol", 1},
            {ScannerMessages.stationRenamed("5", "5a"), "station_renamed", "to", "5a"},
            {ScannerMessages.stationDeleted("6"), "station_deleted", "name", "6"},
            {ScannerMessages.scanStation("b1", "6"), "scan_station", "station", "6"},
            {ScannerMessages.reply("b1", 2, "ok", null), "reply", "revision", 2},
            {
                ScannerMessages.reply("b1", 2, "error", "no_station"),
                "reply",
                "reason",
                "no_station"
            },
        };
        for (Object[] c : cases) {
            JSONObject message = (JSONObject) c[0];
            Assert.assertEquals(c[1], message.getString("type"));
            Assert.assertEquals(c[1] + " " + c[2], c[3], message.get((String) c[2]));
        }
        JSONObject unread = ScannerMessages.reply(null, 0, "error", "bad_checksum");
        Assert.assertFalse(unread.has("scan"));
        Assert.assertFalse(unread.has("revision"));
        Assert.assertFalse(ScannerMessages.reply("b1", 2, "ok", null).has("reason"));
        Assert.assertFalse(ScannerMessages.scanStation("b1", null).has("station"));
    }
}
