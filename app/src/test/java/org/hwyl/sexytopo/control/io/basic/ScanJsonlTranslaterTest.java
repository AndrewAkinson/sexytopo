package org.hwyl.sexytopo.control.io.basic;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.hwyl.sexytopo.control.util.SurveyUpdater;
import org.hwyl.sexytopo.model.scan.SectionScan;
import org.hwyl.sexytopo.model.scan.SurveyScans;
import org.hwyl.sexytopo.model.survey.Station;
import org.hwyl.sexytopo.model.survey.Survey;
import org.hwyl.sexytopo.testutils.BasicTestSurveyCreator;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public class ScanJsonlTranslaterTest {

    private static final String FULL =
            "{\"format\":\"XScan scan 1\",\"scan\":\"b0004-001\",\"station\":\"2\","
                + "\"board\":{\"start_up\":4,\"scan\":1,\"ms_since_start_up\":54326},"
                + "\"lidar\":{\"model\":65,\"firmware\":\"1.02\",\"hardware\":18,\"serial\":\"53AB\"},"
                + "\"samples\":428,\"returns\":2,\"points\":[[359.312,82.25,53],[0.062,83,52]]}";
    private static final String REQUIRED_ONLY =
            "{\"format\":\"XScan scan"
                    + " 1\",\"scan\":\"b1\",\"station\":\"3\",\"points\":[[10,1000,40]]}";
    private static final String BACK =
            "{\"format\":\"XScan scan 1\",\"scan\":\"b2\",\"station\":\"4\",\"facing\":\"back\","
                    + "\"points\":[]}";
    private static final String NEWER_FOR_2 =
            "{\"format\":\"XScan scan 1\",\"scan\":\"b0009-002\",\"station\":\"2\",\"points\":[]}";
    private static final String UNKNOWN_STATION =
            "{\"format\":\"XScan scan 1\",\"scan\":\"b5\",\"station\":\"99\",\"points\":[]}";
    private static final String UNKNOWN_FORMAT =
            "{\"format\":\"XScan scan 2\",\"scan\":\"b3\",\"station\":\"4\",\"points\":[]}";
    private static final String NO_STATION =
            "{\"format\":\"XScan scan 1\",\"scan\":\"b4\",\"points\":[]}";
    private static final String BAD_JSON = "{\"format\":\"XScan scan 1\",\"scan\":";

    private static String lines(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    @Test
    public void testScansMatchedToStations() {
        // file text, expected station and scan name pairs, expected unmatched lines, what
        Object[][] cases = {
            {FULL, new String[] {"2", "b0004-001"}, new String[] {}, "a full line"},
            {REQUIRED_ONLY, new String[] {"3", "b1"}, new String[] {}, "required fields only"},
            {"\n" + FULL + "\n\n", new String[] {"2", "b0004-001"}, new String[] {}, "blank lines"},
            {
                lines(FULL, NEWER_FOR_2),
                new String[] {"2", "b0009-002"},
                new String[] {FULL},
                "last line wins, the replaced one kept"
            },
            {UNKNOWN_STATION, new String[] {}, new String[] {UNKNOWN_STATION}, "unknown station"},
            {UNKNOWN_FORMAT, new String[] {}, new String[] {UNKNOWN_FORMAT}, "unknown format"},
            {NO_STATION, new String[] {}, new String[] {NO_STATION}, "no station"},
            {BAD_JSON, new String[] {}, new String[] {BAD_JSON}, "bad JSON"},
            {"", new String[] {}, new String[] {}, "empty file"},
        };
        for (Object[] c : cases) {
            Survey survey = BasicTestSurveyCreator.createStraightNorth();
            SurveyScans scans = ScanJsonlTranslater.toScans(survey, (String) c[0]);
            String[] expected = (String[]) c[1];
            String what = (String) c[3];
            Assert.assertEquals(what, expected.length / 2, scans.getStationsToScans().size());
            for (int i = 0; i < expected.length; i += 2) {
                Station station = survey.getStationByName(expected[i]);
                Assert.assertEquals(what, expected[i + 1], scans.get(station).getScanName());
            }
            Assert.assertEquals(what, Arrays.asList((String[]) c[2]), scans.getUnmatchedLines());
        }
    }

    @Test
    public void testPointsAndFacingRead() {
        Survey survey = BasicTestSurveyCreator.createStraightNorth();
        SurveyScans scans = ScanJsonlTranslater.toScans(survey, lines(FULL, BACK));
        SectionScan full = scans.get(survey.getStationByName("2"));
        Assert.assertEquals(2, full.getPoints().size());
        Assert.assertEquals(359.312f, full.getPoints().get(0).degrees, 0.0001f);
        Assert.assertEquals(82.25f, full.getPoints().get(0).millimetres, 0.0001f);
        Assert.assertEquals(53, full.getPoints().get(0).quality);
        Assert.assertEquals(SectionScan.Facing.ON, full.getFacing());
        Assert.assertEquals(
                SectionScan.Facing.BACK, scans.get(survey.getStationByName("4")).getFacing());
    }

    @Test
    public void testWrittenWithCurrentStationNames() throws Exception {
        // what happens to the survey before saving, then the station each scan is written with
        Object[][] cases = {
            {"nothing", "2", "3"},
            {"rename", "2a", "3"},
            {"delete", "2", "3"},
        };
        for (Object[] c : cases) {
            Survey survey = BasicTestSurveyCreator.createStraightNorth();
            SurveyScans scans =
                    ScanJsonlTranslater.toScans(
                            survey, lines(UNKNOWN_STATION, FULL, REQUIRED_ONLY));
            if (c[0].equals("rename")) {
                SurveyUpdater.renameStation(survey, survey.getStationByName("2"), "2a");
            } else if (c[0].equals("delete")) {
                SurveyUpdater.deleteStation(survey, survey.getStationByName("4"));
            }
            List<String> written = Arrays.asList(ScanJsonlTranslater.toText(scans).split("\n"));
            String what = (String) c[0];
            Assert.assertEquals(what, 3, written.size());
            Assert.assertEquals(what, UNKNOWN_STATION, written.get(0));
            Assert.assertEquals(what, c[1], new JSONObject(written.get(1)).getString("station"));
            Assert.assertEquals(what, c[2], new JSONObject(written.get(2)).getString("station"));
        }
    }

    @Test
    public void testDeletedStationKeepsItsScanAndLastName() throws Exception {
        Survey survey = BasicTestSurveyCreator.createStraightNorth();
        SurveyScans scans = ScanJsonlTranslater.toScans(survey, BACK);
        Station station = survey.getStationByName("4");
        SurveyUpdater.renameStation(survey, station, "4a");
        SurveyUpdater.deleteStation(survey, station);
        String written = ScanJsonlTranslater.toText(scans).trim();
        Assert.assertEquals("4a", new JSONObject(written).getString("station"));
    }

    @Test
    public void testFieldsSexyTopoDoesNotReadAreKept() throws Exception {
        Survey survey = BasicTestSurveyCreator.createStraightNorth();
        String written =
                ScanJsonlTranslater.toText(ScanJsonlTranslater.toScans(survey, FULL)).trim();
        JSONObject expected = new JSONObject(FULL);
        JSONObject actual = new JSONObject(written);
        Assert.assertEquals(expected.length(), actual.length());
        for (Iterator<String> keys = expected.keys(); keys.hasNext(); ) {
            String key = keys.next();
            Assert.assertEquals(key, expected.get(key).toString(), actual.get(key).toString());
        }
    }

    @Test
    public void testRoundTripGivesTheSameScans() throws Exception {
        Survey survey = BasicTestSurveyCreator.createStraightNorth();
        String text = lines(UNKNOWN_STATION, FULL, REQUIRED_ONLY, BACK);
        String once = ScanJsonlTranslater.toText(ScanJsonlTranslater.toScans(survey, text));
        String twice = ScanJsonlTranslater.toText(ScanJsonlTranslater.toScans(survey, once));
        Assert.assertEquals(once, twice);
    }

    @Test
    public void testNoScansIsEmpty() {
        Survey survey = BasicTestSurveyCreator.createStraightNorth();
        Assert.assertTrue(survey.getScans().isEmpty());
        Assert.assertTrue(ScanJsonlTranslater.toScans(survey, "\n").isEmpty());
    }
}
