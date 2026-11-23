package org.hwyl.sexytopo.control.io.basic;

import java.util.Map;
import org.hwyl.sexytopo.model.scan.SectionScan;
import org.junit.Assert;
import org.junit.Test;

public class ScanJsonlTranslaterTest {

    private static final String FULL =
            "{\"format\":\"XScan scan 1\",\"scan\":\"b0004-001\",\"station\":\"5\","
                + "\"board\":{\"start_up\":4,\"scan\":1,\"ms_since_start_up\":54326},"
                + "\"lidar\":{\"model\":65,\"firmware\":\"1.02\",\"hardware\":18,\"serial\":\"53AB\"},"
                + "\"samples\":428,\"returns\":2,\"points\":[[359.312,82.25,53],[0.062,83,52]]}";
    private static final String REQUIRED_ONLY =
            "{\"format\":\"XScan scan"
                    + " 1\",\"scan\":\"b1\",\"station\":\"6\",\"points\":[[10,1000,40]]}";
    private static final String BACK =
            "{\"format\":\"XScan scan 1\",\"scan\":\"b2\",\"station\":\"7\",\"facing\":\"back\","
                    + "\"points\":[]}";
    private static final String NEWER_FOR_5 =
            "{\"format\":\"XScan scan 1\",\"scan\":\"b0009-002\",\"station\":\"5\",\"points\":[]}";
    private static final String UNKNOWN_FORMAT =
            "{\"format\":\"XScan scan 2\",\"scan\":\"b3\",\"station\":\"8\",\"points\":[]}";
    private static final String NO_STATION =
            "{\"format\":\"XScan scan 1\",\"scan\":\"b4\",\"points\":[]}";
    private static final String BAD_JSON = "{\"format\":\"XScan scan 1\",\"scan\":";

    @Test
    public void testFileReadByStation() {
        // file text, expected station to scan name, what is being checked
        Object[][] cases = {
            {FULL, new String[] {"5", "b0004-001"}, "a full line"},
            {REQUIRED_ONLY, new String[] {"6", "b1"}, "required fields only"},
            {"\n" + FULL + "\n\n", new String[] {"5", "b0004-001"}, "blank lines"},
            {FULL + "\n" + NEWER_FOR_5, new String[] {"5", "b0009-002"}, "last line wins"},
            {UNKNOWN_FORMAT + "\n" + REQUIRED_ONLY, new String[] {"6", "b1"}, "unknown format"},
            {NO_STATION + "\n" + REQUIRED_ONLY, new String[] {"6", "b1"}, "no station"},
            {BAD_JSON + "\n" + REQUIRED_ONLY, new String[] {"6", "b1"}, "bad JSON"},
            {"", new String[] {}, "empty file"},
        };
        for (Object[] c : cases) {
            Map<String, SectionScan> scans = ScanJsonlTranslater.toScans((String) c[0]);
            String[] expected = (String[]) c[1];
            String what = (String) c[2];
            Assert.assertEquals(what, expected.length / 2, scans.size());
            for (int i = 0; i < expected.length; i += 2) {
                Assert.assertEquals(what, expected[i + 1], scans.get(expected[i]).getScanName());
            }
        }
    }

    @Test
    public void testPointsAndFacingRead() {
        SectionScan full = ScanJsonlTranslater.toScans(FULL).get("5");
        Assert.assertEquals(2, full.getPoints().size());
        Assert.assertEquals(359.312f, full.getPoints().get(0).degrees, 0.0001f);
        Assert.assertEquals(82.25f, full.getPoints().get(0).millimetres, 0.0001f);
        Assert.assertEquals(53, full.getPoints().get(0).quality);
        Assert.assertEquals(SectionScan.Facing.ON, full.getFacing());
        Assert.assertEquals(
                SectionScan.Facing.BACK, ScanJsonlTranslater.toScans(BACK).get("7").getFacing());
    }
}
