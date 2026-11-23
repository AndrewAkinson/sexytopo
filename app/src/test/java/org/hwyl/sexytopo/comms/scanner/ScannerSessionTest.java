package org.hwyl.sexytopo.comms.scanner;

import org.hwyl.sexytopo.control.io.basic.SketchJsonTranslater;
import org.hwyl.sexytopo.model.geometry.Coord2D;
import org.hwyl.sexytopo.model.sketch.CrossSection;
import org.hwyl.sexytopo.model.sketch.CrossSectionDetail;
import org.hwyl.sexytopo.model.sketch.Sketch;
import org.hwyl.sexytopo.model.survey.Station;
import org.hwyl.sexytopo.model.survey.Survey;
import org.hwyl.sexytopo.testutils.BasicTestSurveyCreator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

/** Straight north survey: 1, then 2, 3 and 4 each 5 m further north. */
public class ScannerSessionTest {

    private static JSONObject drawing(int revision, float x) throws Exception {
        JSONObject path =
                new JSONObject()
                        .put("colour", "BLACK")
                        .put(
                                "points",
                                new JSONArray()
                                        .put(new JSONObject().put("x", x).put("y", 0))
                                        .put(new JSONObject().put("x", 0).put("y", -1)));
        return new JSONObject()
                .put("format", "XScan sketch 1")
                .put("scan", "b1")
                .put("revision", revision)
                .put("sketch", new JSONObject().put("paths", new JSONArray().put(path)));
    }

    private static JSONObject scan(String name, String station) throws Exception {
        return new JSONObject()
                .put("format", "XScan scan 1")
                .put("scan", name)
                .put("station", station)
                .put("orientation", "vertical")
                .put("facing", "on")
                .put("points", new JSONArray().put(new JSONArray().put(0.5).put(1500).put(47)));
    }

    private static JSONObject section(JSONObject scan, JSONObject drawing, int basedOn)
            throws Exception {
        JSONObject section = new JSONObject().put("type", "section").put("scan", scan);
        if (drawing != null) {
            section.put("sketch", drawing);
        }
        return section.put("based_on", basedOn);
    }

    private static CrossSectionDetail sectionAt(Survey survey, String station) {
        return survey.getPlanSketch().getCrossSectionDetail(survey.getStationByName(station));
    }

    private static float firstX(CrossSectionDetail detail) {
        return detail.getSketch().getPathDetails().get(0).getPath().get(0).x;
    }

    @Test
    public void testNewSectionMadeBesideItsStation() throws Exception {
        // orientation, facing, expected orientation, expected angle
        Object[][] cases = {
            {"vertical", "on", CrossSection.Orientation.VERTICAL, 0f},
            {"vertical", "back", CrossSection.Orientation.VERTICAL, 180f},
            {"horizontal", "on", CrossSection.Orientation.HORIZONTAL, 0f},
        };
        for (Object[] c : cases) {
            Survey survey = BasicTestSurveyCreator.createStraightNorth();
            JSONObject scan = scan("b1", "2").put("orientation", c[0]).put("facing", c[1]);
            ScannerSession.Outcome outcome =
                    new ScannerSession(survey).onSection(section(scan, drawing(1, 2), 0));
            String what = c[0] + " facing " + c[1];
            Assert.assertEquals(what, "ok", outcome.reply.getString("result"));
            CrossSectionDetail detail = sectionAt(survey, "2");
            Assert.assertEquals(what, c[2], detail.getCrossSection().getOrientation());
            Assert.assertEquals(what, (float) c[3], detail.getCrossSection().getAngle(), 0.001f);
            Assert.assertEquals(what, new Coord2D(3, -5), detail.getPosition());
            Assert.assertEquals(what, 2f, firstX(detail), 0.001f);
            Assert.assertEquals(
                    what,
                    "b1",
                    survey.getScans().get(detail.getCrossSection().getStation()).getScanName());
            Assert.assertFalse(what, survey.isSaved());
        }
    }

    @Test
    public void testScanWithoutDrawingGetsAnEmptySection() throws Exception {
        Survey survey = BasicTestSurveyCreator.createStraightNorth();
        ScannerSession.Outcome outcome =
                new ScannerSession(survey).onSection(section(scan("b1", "2"), null, 0));
        Assert.assertEquals("ok", outcome.reply.getString("result"));
        Assert.assertEquals(0, outcome.reply.getInt("revision"));
        Assert.assertTrue(SketchJsonTranslater.isSketchEmpty(sectionAt(survey, "2").getSketch()));
    }

    @Test
    public void testDrawingReplacedOnlyIfNotEditedHere() throws Exception {
        // edit in SexyTopo between revisions, based_on of the second, expected result and first x
        Object[][] cases = {
            {false, 1, "ok", 5f},
            {true, 1, "conflict", 9f},
            {false, 0, "conflict", 2f},
        };
        for (Object[] c : cases) {
            Survey survey = BasicTestSurveyCreator.createStraightNorth();
            ScannerSession session = new ScannerSession(survey);
            session.onSection(section(scan("b1", "2"), drawing(1, 2), 0));
            if ((boolean) c[0]) {
                Sketch edited =
                        SketchJsonTranslater.toSubSketch(drawing(1, 9).getJSONObject("sketch"));
                sectionAt(survey, "2").setSketch(edited);
            }
            ScannerSession.Outcome outcome =
                    session.onSection(section(scan("b1", "2"), drawing(2, 5), (int) c[1]));
            String what = "edited " + c[0] + ", based on " + c[1];
            Assert.assertEquals(what, c[2], outcome.reply.getString("result"));
            Assert.assertEquals(what, (float) c[3], firstX(sectionAt(survey, "2")), 0.001f);
        }
    }

    @Test
    public void testConflictTakenWhenTheUserChoosesTheScanners() throws Exception {
        Survey survey = BasicTestSurveyCreator.createStraightNorth();
        ScannerSession session = new ScannerSession(survey);
        session.onSection(section(scan("b1", "2"), drawing(1, 2), 0));
        sectionAt(survey, "2")
                .setSketch(SketchJsonTranslater.toSubSketch(drawing(1, 9).getJSONObject("sketch")));

        ScannerSession.Outcome outcome =
                session.onSection(section(scan("b1", "2"), drawing(2, 5), 1));
        Assert.assertTrue(outcome.isConflict());
        JSONObject reply = outcome.takeScannersDrawing();
        Assert.assertEquals("ok", reply.getString("result"));
        Assert.assertEquals(5f, firstX(sectionAt(survey, "2")), 0.001f);

        // and the next revision follows on without a conflict
        outcome = session.onSection(section(scan("b1", "2"), drawing(3, 7), 2));
        Assert.assertEquals("ok", outcome.reply.getString("result"));
        Assert.assertEquals(7f, firstX(sectionAt(survey, "2")), 0.001f);
    }

    @Test
    public void testSectionMadeByHand() throws Exception {
        // drawing already in the hand-made section, expected result
        Object[][] cases = {
            {false, "ok"},
            {true, "conflict"},
        };
        for (Object[] c : cases) {
            Survey survey = BasicTestSurveyCreator.createStraightNorth();
            Station station = survey.getStationByName("2");
            Sketch existing =
                    (boolean) c[0]
                            ? SketchJsonTranslater.toSubSketch(
                                    drawing(1, 9).getJSONObject("sketch"))
                            : new Sketch();
            survey.getPlanSketch()
                    .addCrossSection(
                            new CrossSectionDetail(
                                    new CrossSection(station, 0), Coord2D.ORIGIN, existing));
            ScannerSession.Outcome outcome =
                    new ScannerSession(survey)
                            .onSection(section(scan("b1", "2"), drawing(1, 2), 0));
            Assert.assertEquals("drawing " + c[0], c[1], outcome.reply.getString("result"));
        }
    }

    @Test
    public void testRepeatChangesNothing() throws Exception {
        Survey survey = BasicTestSurveyCreator.createStraightNorth();
        ScannerSession session = new ScannerSession(survey);
        session.onSection(section(scan("b1", "2"), drawing(1, 2), 0));
        survey.setSaved(true);
        ScannerSession.Outcome outcome =
                session.onSection(section(scan("b1", "2"), drawing(1, 2), 0));
        Assert.assertEquals("ok", outcome.reply.getString("result"));
        Assert.assertTrue(survey.isSaved());
    }

    @Test
    public void testScanMovesToItsNewStation() throws Exception {
        Survey survey = BasicTestSurveyCreator.createStraightNorth();
        ScannerSession session = new ScannerSession(survey);
        session.onSection(section(scan("b1", "2"), drawing(1, 2), 0));
        session.onSection(section(scan("b1", "3"), drawing(2, 2), 1));
        Assert.assertNull(survey.getScans().get(survey.getStationByName("2")));
        Assert.assertEquals(
                "b1", survey.getScans().get(survey.getStationByName("3")).getScanName());
    }

    @Test
    public void testSectionsNotTaken() throws Exception {
        // what is wrong, the section, expected reason, whether the reply names the scan
        Object[][] cases = {
            {"unknown station", section(scan("b1", "99"), drawing(1, 2), 0), "no_station", true},
            {
                "unknown format",
                section(scan("b1", "2").put("format", "XScan scan 2"), null, 0),
                "unknown_format",
                true
            },
            {"no scan", new JSONObject().put("type", "section"), "unknown_format", false},
            {
                "bad points",
                section(scan("b1", "2").put("points", "x"), null, 0),
                "bad_points",
                true
            },
            {
                "bad sketch",
                section(scan("b1", "2"), drawing(1, 2).put("format", "other"), 0),
                "bad_sketch",
                true
            },
        };
        for (Object[] c : cases) {
            Survey survey = BasicTestSurveyCreator.createStraightNorth();
            JSONObject reply = new ScannerSession(survey).onSection((JSONObject) c[1]).reply;
            String what = (String) c[0];
            Assert.assertEquals(what, "error", reply.getString("result"));
            Assert.assertEquals(what, c[2], reply.getString("reason"));
            Assert.assertEquals(what, c[3], reply.has("scan"));
            Assert.assertTrue(what, survey.getScans().isEmpty());
            Assert.assertTrue(what, survey.getPlanSketch().getCrossSectionDetails().isEmpty());
        }
    }

    @Test
    public void testDroppedFramesGetAnErrorReply() throws Exception {
        // reason the frame was dropped, reason replied
        String[][] cases = {
            {ScannerFrames.DROPPED_BAD_CHECKSUM, "bad_checksum"},
            {ScannerFrames.DROPPED_BAD_HEADER, "bad_checksum"},
            {ScannerFrames.DROPPED_UNKNOWN_FORMAT, "unknown_format"},
        };
        for (String[] c : cases) {
            JSONObject reply = ScannerSession.onDropped(c[0]);
            Assert.assertEquals(c[0], c[1], reply.getString("reason"));
            Assert.assertFalse(c[0], reply.has("scan"));
        }
    }
}
