package org.hwyl.sexytopo.model.scan;

import java.util.Collections;
import java.util.List;
import org.hwyl.sexytopo.model.geometry.Coord2D;
import org.junit.Assert;
import org.junit.Test;

public class SectionScanTest {

    private static final float DELTA = 0.0001f;

    @Test
    public void testPointsInSectionFrame() {
        // degrees, facing, expected x and y in metres (x right, y down) for a 1000 mm return
        Object[][] cases = {
            {0f, SectionScan.Facing.ON, 0f, -1f},
            {90f, SectionScan.Facing.ON, -1f, 0f},
            {180f, SectionScan.Facing.ON, 0f, 1f},
            {270f, SectionScan.Facing.ON, 1f, 0f},
            {0f, SectionScan.Facing.BACK, 0f, -1f},
            {90f, SectionScan.Facing.BACK, 1f, 0f},
            {180f, SectionScan.Facing.BACK, 0f, 1f},
            {270f, SectionScan.Facing.BACK, -1f, 0f},
        };
        for (Object[] c : cases) {
            List<SectionScan.Point> points =
                    Collections.singletonList(new SectionScan.Point((float) c[0], 1000, 50));
            Coord2D point =
                    new SectionScan("b1", (SectionScan.Facing) c[1], points, null, "")
                            .getPointsInSection()
                            .get(0);
            String what = c[0] + " degrees facing " + c[1];
            Assert.assertEquals(what, (float) c[2], point.x, DELTA);
            Assert.assertEquals(what, (float) c[3], point.y, DELTA);
        }
    }

    @Test
    public void testDistanceInMillimetresBecomesMetres() {
        List<SectionScan.Point> points =
                Collections.singletonList(new SectionScan.Point(180, 2500, 50));
        Coord2D point =
                new SectionScan("b1", SectionScan.Facing.ON, points, null, "")
                        .getPointsInSection()
                        .get(0);
        Assert.assertEquals(2.5f, point.y, DELTA);
    }
}
