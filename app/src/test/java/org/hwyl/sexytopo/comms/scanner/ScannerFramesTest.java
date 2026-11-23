package org.hwyl.sexytopo.comms.scanner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

/** Reads the scanner project's shared example frames, which must give what cases.json says. */
public class ScannerFramesTest {

    private static final String FOLDER = "xscan-link/";

    private static byte[] resource(String name) throws IOException {
        try (InputStream in =
                ScannerFramesTest.class.getClassLoader().getResourceAsStream(FOLDER + name)) {
            Assert.assertNotNull(name, in);
            return readAll(in);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) > 0) {
            out.write(chunk, 0, read);
        }
        return out.toByteArray();
    }

    /** A JSON value as plain Java values, numbers as doubles, so equal JSON compares equal. */
    private static Object plain(Object value) throws Exception {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Map<String, Object> map = new HashMap<>();
            for (Iterator<String> keys = object.keys(); keys.hasNext(); ) {
                String key = keys.next();
                map.put(key, plain(object.get(key)));
            }
            return map;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                list.add(plain(array.get(i)));
            }
            return list;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return value;
    }

    private static List<Object> readInPackets(byte[] data, int size) throws Exception {
        ScannerFrames.Reader reader = new ScannerFrames.Reader();
        List<Object> found = new ArrayList<>();
        for (int i = 0; i < data.length; i += size) {
            byte[] packet = Arrays.copyOfRange(data, i, Math.min(data.length, i + size));
            for (ScannerFrames.Result result : reader.feed(packet)) {
                found.add(
                        result.message != null
                                ? Arrays.asList("message", plain(result.message))
                                : Arrays.asList("dropped", result.dropped));
            }
        }
        return found;
    }

    @Test
    public void testSharedFramesReadAsCasesSay() throws Exception {
        JSONArray cases = new JSONArray(new String(resource("cases.json"), StandardCharsets.UTF_8));
        Assert.assertEquals(22, cases.length());
        int[] packetSizes = {Integer.MAX_VALUE, 1, 20, 514};
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            byte[] data = resource(c.getString("file"));
            Object expected = plain(c.getJSONArray("read"));
            for (int size : packetSizes) {
                Assert.assertEquals(
                        c.getString("file") + " in packets of " + size,
                        expected,
                        readInPackets(data, Math.min(size, Math.max(1, data.length))));
            }
        }
    }

    @Test
    public void testCutShortFrameIsHeld() throws Exception {
        ScannerFrames.Reader reader = new ScannerFrames.Reader();
        Assert.assertTrue(reader.feed(resource("cut_short.frame")).isEmpty());
        Assert.assertTrue(reader.waiting() > 0);
        reader.clear();
        Assert.assertEquals(0, reader.waiting());
    }

    @Test
    public void testFramesWrittenHereReadBack() throws Exception {
        // messages, including accents and characters org.json escapes
        String[] messages = {
            "{\"type\":\"station_added\",\"name\":\"Salle Cañón\"}",
            "{\"type\":\"reply\",\"scan\":\"b0004-001\",\"revision\":2,\"result\":\"ok\"}",
            "{\"type\":\"station_renamed\",\"from\":\"a/b\",\"to\":\"\\\"q\\\"\"}",
        };
        for (String text : messages) {
            JSONObject message = new JSONObject(text);
            List<Object> read = readInPackets(ScannerFrames.toFrame(message), 7);
            Assert.assertEquals(
                    text, Arrays.asList(Arrays.asList("message", plain(message))), read);
        }
    }

    @Test
    public void testLongJunkKeepsAPossibleFrameStart() throws Exception {
        ScannerFrames.Reader reader = new ScannerFrames.Reader();
        byte[] junk = new byte[42];
        Arrays.fill(junk, (byte) 'x');
        junk[40] = 'X';
        junk[41] = 'S';
        List<ScannerFrames.Result> found = reader.feed(junk);
        Assert.assertEquals(1, found.size());
        Assert.assertEquals(ScannerFrames.DROPPED_BAD_HEADER, found.get(0).dropped);
        Assert.assertEquals(2, reader.waiting());

        byte[] frame = ScannerFrames.toFrame(new JSONObject("{\"type\":\"hello\"}"));
        found = reader.feed(Arrays.copyOfRange(frame, 2, frame.length));
        Assert.assertEquals(1, found.size());
        Assert.assertEquals("hello", found.get(0).message.getString("type"));
    }
}
