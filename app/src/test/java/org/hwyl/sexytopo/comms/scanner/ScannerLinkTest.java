package org.hwyl.sexytopo.comms.scanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.hwyl.sexytopo.control.io.basic.SketchJsonTranslater;
import org.hwyl.sexytopo.control.util.SurveyUpdater;
import org.hwyl.sexytopo.model.survey.Survey;
import org.hwyl.sexytopo.testutils.BasicTestSurveyCreator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** The link over a pretend connection, with a pretend app around it. */
public class ScannerLinkTest {

    private static class PretendHost implements ScannerLink.Host {
        Survey survey = BasicTestSurveyCreator.createStraightNorth();
        long now = 0;
        int surveyChanges = 0;
        List<ScannerLink.Conflict> conflicts = new ArrayList<>();

        public Survey getSurvey() {
            return survey;
        }

        public String getAppVersion() {
            return "1.15.0";
        }

        public boolean isVerticalSectionOnElevation() {
            return false;
        }

        public long now() {
            return now;
        }

        public void onSurveyChanged() {
            surveyChanges++;
        }

        public void onConflict(ScannerLink.Conflict conflict) {
            conflicts.add(conflict);
        }

        public void onStatusChanged() {}

        public void log(int stringId, Object... args) {}
    }

    private static class PretendConnection implements ScannerConnection {
        final ScannerFrames.Reader reader = new ScannerFrames.Reader();
        final List<JSONObject> sent = new ArrayList<>();
        boolean connected = true;

        public void connect() {}

        public void disconnect() {
            connected = false;
        }

        public boolean isConnected() {
            return connected;
        }

        public void send(byte[] frame) {
            for (ScannerFrames.Result result : reader.feed(frame)) {
                sent.add(result.message);
            }
        }

        List<String> types() {
            List<String> types = new ArrayList<>();
            for (JSONObject message : sent) {
                types.add(message.optString("type"));
            }
            return types;
        }

        JSONObject last() {
            return sent.get(sent.size() - 1);
        }
    }

    private PretendHost host;
    private PretendConnection connection;
    private ScannerLink link;

    @Before
    public void setUp() {
        host = new PretendHost();
        connection = new PretendConnection();
        link = new ScannerLink(host);
    }

    private void receive(JSONObject message) {
        link.onBytes(ScannerFrames.toFrame(message));
    }

    private static JSONObject hello(int protocol) throws Exception {
        return new JSONObject()
                .put("type", "hello")
                .put("protocol", protocol)
                .put("firmware", "0.4")
                .put("board", "A1B2C3");
    }

    private static JSONObject section(String scan, String station, int revision, int basedOn)
            throws Exception {
        JSONObject scanJson =
                new JSONObject()
                        .put("format", "XScan scan 1")
                        .put("scan", scan)
                        .put("station", station)
                        .put("orientation", "vertical")
                        .put("facing", "on")
                        .put(
                                "points",
                                new JSONArray().put(new JSONArray().put(0).put(1000).put(40)));
        JSONObject path =
                new JSONObject()
                        .put("colour", "BLACK")
                        .put(
                                "points",
                                new JSONArray()
                                        .put(new JSONObject().put("x", revision).put("y", 0))
                                        .put(new JSONObject().put("x", 0).put("y", 1)));
        JSONObject sketch =
                new JSONObject()
                        .put("format", "XScan sketch 1")
                        .put("scan", scan)
                        .put("revision", revision)
                        .put("sketch", new JSONObject().put("paths", new JSONArray().put(path)));
        return new JSONObject()
                .put("type", "section")
                .put("scan", scanJson)
                .put("sketch", sketch)
                .put("based_on", basedOn);
    }

    private void connectAndGreet() throws Exception {
        link.onConnected(connection);
        receive(hello(ScannerMessages.PROTOCOL));
    }

    /** Edits the drawing at station 2 here, as the cross-section editor does. */
    private void editHere(int x) throws Exception {
        host.survey
                .getPlanSketch()
                .getCrossSectionDetail(host.survey.getStationByName("2"))
                .setSketch(
                        SketchJsonTranslater.toSubSketch(
                                section("b1", "2", x, 0)
                                        .getJSONObject("sketch")
                                        .getJSONObject("sketch")));
    }

    private int agreedRevision() {
        return host.survey.getScans().get(host.survey.getStationByName("2")).getReceived().revision;
    }

    private List<JSONObject> sentOfType(String type) {
        List<JSONObject> found = new ArrayList<>();
        for (JSONObject message : connection.sent) {
            if (message.optString("type").equals(type)) {
                found.add(message);
            }
        }
        return found;
    }

    private static JSONObject drawingReply(String scan, int revision, String result)
            throws Exception {
        return new JSONObject()
                .put("type", "drawing_reply")
                .put("scan", scan)
                .put("revision", revision)
                .put("result", result);
    }

    @Test
    public void testStartSendsHelloThenSurvey() throws Exception {
        link.onConnected(connection);
        Assert.assertEquals(List.of("hello"), connection.types());
        Assert.assertEquals("SexyTopo 1.15.0", connection.last().getString("app"));
        Assert.assertFalse(link.isReady());

        receive(hello(ScannerMessages.PROTOCOL));
        Assert.assertEquals(List.of("hello", "survey"), connection.types());
        Assert.assertTrue(link.isReady());
        Assert.assertEquals("0.4", link.getFirmware());
        Assert.assertEquals("A1B2C3", link.getBoard());
    }

    @Test
    public void testOtherProtocolDisconnects() throws Exception {
        link.onConnected(connection);
        receive(hello(ScannerMessages.PROTOCOL - 1));
        Assert.assertFalse(connection.isConnected());
        Assert.assertFalse(link.isReady());
        Assert.assertEquals(List.of("hello"), connection.types());
    }

    @Test
    public void testSectionTakenAndReplied() throws Exception {
        connectAndGreet();
        receive(section("b1", "2", 1, 0));
        Assert.assertEquals("reply", connection.last().getString("type"));
        Assert.assertEquals("ok", connection.last().getString("result"));
        Assert.assertEquals(1, host.surveyChanges);
        Assert.assertEquals("b1", link.getLastSection());
        Assert.assertNotNull(host.survey.getScans().get(host.survey.getStationByName("2")));
    }

    @Test
    public void testSectionBeforeHelloIsIgnored() throws Exception {
        link.onConnected(connection);
        receive(section("b1", "2", 1, 0));
        Assert.assertEquals(List.of("hello"), connection.types());
        Assert.assertTrue(host.survey.getScans().isEmpty());
    }

    @Test
    public void testConflictResolvedEitherWay() throws Exception {
        // whether the user takes the scanner's drawing, then what is sent after the conflict,
        // and the revision then agreed
        Object[][] cases = {
            {true, List.of("reply ok"), 3},
            {false, List.of("drawing 4 based on 3"), 3},
        };
        for (Object[] c : cases) {
            setUp();
            connectAndGreet();
            receive(section("b1", "2", 1, 0));
            editHere(9);
            receive(section("b1", "2", 3, 1));
            Assert.assertEquals("conflict", connection.last().getString("result"));
            Assert.assertEquals(1, host.conflicts.size());
            Assert.assertEquals("2", host.conflicts.get(0).stationName);
            // nothing is sent here while the user hasn't chosen
            link.onSurveyUpdated();
            Assert.assertTrue(sentOfType("drawing").isEmpty());

            int sentBefore = connection.sent.size();
            link.resolve(host.conflicts.get(0), (boolean) c[0]);
            List<String> sent = new ArrayList<>();
            for (JSONObject message : connection.sent.subList(sentBefore, connection.sent.size())) {
                sent.add(
                        message.getString("type").equals("drawing")
                                ? "drawing "
                                        + message.getInt("revision")
                                        + " based on "
                                        + message.getInt("based_on")
                                : "reply " + message.getString("result"));
            }
            String what = "use scanner's " + c[0];
            Assert.assertEquals(what, c[1], sent);
            Assert.assertEquals(what, c[2], agreedRevision());
            Assert.assertTrue(what, link.getConflicts().isEmpty());
        }
    }

    @Test
    public void testDrawingEditedHereSent() throws Exception {
        // edited here, the scanner's reply, drawings sent in all, revision then agreed
        Object[][] cases = {
            {false, null, 0, 1},
            {true, "ok", 1, 2},
            {true, "conflict", 1, 1},
        };
        for (Object[] c : cases) {
            setUp();
            connectAndGreet();
            receive(section("b1", "2", 1, 0));
            if ((boolean) c[0]) {
                editHere(9);
            }
            link.onSurveyUpdated();
            link.onSurveyUpdated(); // not sent twice while waiting for the reply
            if (c[1] != null) {
                JSONObject drawing = sentOfType("drawing").get(0);
                Assert.assertEquals(2, drawing.getInt("revision"));
                Assert.assertEquals(1, drawing.getInt("based_on"));
                Assert.assertEquals(
                        "XScan sketch 1", drawing.getJSONObject("sketch").getString("format"));
                receive(drawingReply("b1", 2, (String) c[1]));
            }
            String what = "edited " + c[0] + ", reply " + c[1];
            Assert.assertEquals(what, c[2], sentOfType("drawing").size());
            Assert.assertEquals(what, c[3], agreedRevision());
        }
    }

    @Test
    public void testDrawingEditedWhileApartSentOnConnecting() throws Exception {
        connectAndGreet();
        receive(section("b1", "2", 1, 0));
        link.onDisconnected(connection);
        editHere(9);
        connection = new PretendConnection();
        connectAndGreet();
        Assert.assertEquals(List.of("hello", "survey", "drawing"), connection.types());
    }

    @Test
    public void testDroppedFrameGetsAnErrorReply() throws Exception {
        connectAndGreet();
        byte[] frame = ScannerFrames.toFrame(section("b1", "2", 1, 0));
        frame[frame.length - 2] ^= 1;
        link.onBytes(frame);
        Assert.assertEquals("error", connection.last().getString("result"));
        Assert.assertEquals("bad_checksum", connection.last().getString("reason"));
        Assert.assertFalse(connection.last().has("scan"));
    }

    @Test
    public void testPartFrameDroppedAfterTheTimeout() throws Exception {
        connectAndGreet();
        byte[] frame = ScannerFrames.toFrame(section("b1", "2", 1, 0));
        int half = frame.length / 2;
        // the rest arrives just in time, or too late
        long[][] cases = {
            {ScannerLink.PART_FRAME_TIMEOUT_MS, 1}, {ScannerLink.PART_FRAME_TIMEOUT_MS + 1, 0}
        };
        for (long[] c : cases) {
            setUp();
            connectAndGreet();
            link.onBytes(Arrays.copyOfRange(frame, 0, half));
            host.now += c[0];
            link.onBytes(Arrays.copyOfRange(frame, half, frame.length));
            Assert.assertEquals("gap " + c[0], c[1], host.surveyChanges);
        }
    }

    @Test
    public void testStationChangesSentOnlyWhenReady() throws Exception {
        link.onSurveyUpdated();
        link.onConnected(connection);
        SurveyUpdater.renameStation(host.survey, host.survey.getStationByName("2"), "2a");
        link.onSurveyUpdated();
        Assert.assertEquals(List.of("hello"), connection.types());

        receive(hello(ScannerMessages.PROTOCOL));
        SurveyUpdater.renameStation(host.survey, host.survey.getStationByName("3"), "3a");
        link.onSurveyUpdated();
        Assert.assertEquals(List.of("hello", "survey", "station_renamed"), connection.types());
        Assert.assertEquals("3a", connection.last().getString("to"));
    }

    @Test
    public void testOnlyTheCurrentConnectionCanDisconnect() throws Exception {
        PretendConnection old = connection;
        connectAndGreet();
        connection = new PretendConnection();
        connectAndGreet();
        link.onDisconnected(old);
        Assert.assertTrue(link.isReady());
        link.onDisconnected(connection);
        Assert.assertFalse(link.isReady());
    }
}
