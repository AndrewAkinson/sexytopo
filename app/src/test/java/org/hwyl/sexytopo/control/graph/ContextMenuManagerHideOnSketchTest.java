package org.hwyl.sexytopo.control.graph;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.hwyl.sexytopo.model.survey.Leg;
import org.junit.Test;

public class ContextMenuManagerHideOnSketchTest {

    private static Leg createLeg() {
        return new Leg(1.0f, 0.0f, 0.0f);
    }

    @Test
    public void testLegIsNotHiddenOnSketchByDefault() {
        Leg leg = createLeg();
        assertFalse(leg.isHiddenOnSketch());
    }

    @Test
    public void testSettingHiddenOnSketchToTrueReflectsInGetter() {
        Leg leg = createLeg();
        leg.setHiddenOnSketch(true);
        assertTrue(leg.isHiddenOnSketch());
    }

    @Test
    public void testSettingHiddenOnSketchToFalseReflectsInGetter() {
        Leg leg = createLeg();
        leg.setHiddenOnSketch(true);
        leg.setHiddenOnSketch(false);
        assertFalse(leg.isHiddenOnSketch());
    }

    @Test
    public void testTogglingHiddenOnSketchFromFalseToTrue() {
        Leg leg = createLeg();
        leg.setHiddenOnSketch(!leg.isHiddenOnSketch());
        assertTrue(leg.isHiddenOnSketch());
    }

    @Test
    public void testTogglingHiddenOnSketchFromTrueToFalse() {
        Leg leg = createLeg();
        leg.setHiddenOnSketch(true);
        leg.setHiddenOnSketch(!leg.isHiddenOnSketch());
        assertFalse(leg.isHiddenOnSketch());
    }
}
