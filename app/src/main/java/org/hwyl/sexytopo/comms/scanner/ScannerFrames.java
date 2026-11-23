package org.hwyl.sexytopo.comms.scanner;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Frames of the cross-section scanner link (CrossSectionScanner docs/LINK.md): a header line of
 * XS1, the JSON's length and its CRC-32, then one JSON message. Follows the scanner project's
 * tools/xscan_link.py, and its tests/link frames are read in the tests.
 */
public class ScannerFrames {

    public static final int MAX_JSON = 262144;
    private static final int MAX_HEADER = 32;
    private static final byte[] FRAME_START = "XS1 ".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern HEADER = Pattern.compile("XS1 (\\d{1,9}) ([0-9a-f]{8})");

    public static final String DROPPED_BAD_HEADER = "bad_header";
    public static final String DROPPED_BAD_CHECKSUM = "bad_checksum";
    public static final String DROPPED_UNKNOWN_FORMAT = "unknown_format";

    /** A message read from the frames, or why a frame was dropped. */
    public static class Result {
        public final JSONObject message;
        public final String dropped;

        private Result(JSONObject message, String dropped) {
            this.message = message;
            this.dropped = dropped;
        }
    }

    public static byte[] toFrame(JSONObject message) {
        byte[] body = message.toString().getBytes(StandardCharsets.UTF_8);
        String header = String.format("XS1 %d %08x\n", body.length, crc(body));
        byte[] frame = new byte[header.length() + body.length];
        System.arraycopy(header.getBytes(StandardCharsets.US_ASCII), 0, frame, 0, header.length());
        System.arraycopy(body, 0, frame, header.length(), body.length);
        return frame;
    }

    private static long crc(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return crc.getValue();
    }

    /** Joins the bytes received, in whatever packets they come, into messages. */
    public static class Reader {

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        /** What the new bytes completed, in order. */
        public List<Result> feed(byte[] data) {
            buffer.write(data, 0, data.length);
            List<Result> found = new ArrayList<>();
            byte[] bytes = buffer.toByteArray();
            int start = 0;
            while (true) {
                int end = indexOf(bytes, (byte) '\n', start);
                if (end < 0) {
                    if (bytes.length - start > MAX_HEADER) {
                        found.add(new Result(null, DROPPED_BAD_HEADER));
                        start = skip(bytes, start);
                        continue;
                    }
                    break;
                }
                String header = new String(bytes, start, end - start, StandardCharsets.US_ASCII);
                Matcher matcher = HEADER.matcher(header);
                if (!matcher.matches() || Integer.parseInt(matcher.group(1)) > MAX_JSON) {
                    found.add(new Result(null, DROPPED_BAD_HEADER));
                    start = skip(bytes, start);
                    continue;
                }
                int length = Integer.parseInt(matcher.group(1));
                long expectedCrc = Long.parseLong(matcher.group(2), 16);
                int bodyStart = end + 1;
                if (bytes.length < bodyStart + length) {
                    break;
                }
                byte[] body = Arrays.copyOfRange(bytes, bodyStart, bodyStart + length);
                start = bodyStart + length;
                found.add(read(body, expectedCrc));
            }
            buffer.reset();
            buffer.write(bytes, start, bytes.length - start);
            return found;
        }

        private static Result read(byte[] body, long expectedCrc) {
            if (crc(body) != expectedCrc) {
                return new Result(null, DROPPED_BAD_CHECKSUM);
            }
            try {
                JSONObject message = new JSONObject(new String(body, StandardCharsets.UTF_8));
                if (message.opt(ScannerMessages.TYPE) instanceof String) {
                    return new Result(message, null);
                }
            } catch (JSONException e) {
                // not JSON, dropped below
            }
            return new Result(null, DROPPED_UNKNOWN_FORMAT);
        }

        /**
         * Where reading goes on after a bad header: the next frame start after the first byte, or a
         * possible partial frame start at the end.
         */
        private static int skip(byte[] bytes, int start) {
            int next = indexOf(bytes, FRAME_START, start + 1);
            if (next >= 0) {
                return next;
            }
            for (int keep = FRAME_START.length - 1; keep > 0; keep--) {
                int from = bytes.length - keep;
                if (from > start && regionMatches(bytes, from, FRAME_START, keep)) {
                    return from;
                }
            }
            return bytes.length;
        }

        /** Bytes held for a frame not yet complete, so a caller can drop them after a timeout. */
        public int waiting() {
            return buffer.size();
        }

        public void clear() {
            buffer.reset();
        }
    }

    private static int indexOf(byte[] bytes, byte wanted, int from) {
        for (int i = from; i < bytes.length; i++) {
            if (bytes[i] == wanted) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOf(byte[] bytes, byte[] wanted, int from) {
        for (int i = from; i + wanted.length <= bytes.length; i++) {
            if (regionMatches(bytes, i, wanted, wanted.length)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean regionMatches(byte[] bytes, int from, byte[] wanted, int length) {
        for (int i = 0; i < length; i++) {
            if (bytes[from + i] != wanted[i]) {
                return false;
            }
        }
        return true;
    }
}
