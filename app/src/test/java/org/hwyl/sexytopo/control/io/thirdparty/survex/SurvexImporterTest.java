package org.hwyl.sexytopo.control.io.thirdparty.survex;

import java.util.List;
import org.hwyl.sexytopo.control.io.thirdparty.survextherion.SurvexTherionImporter;
import org.hwyl.sexytopo.model.survey.Leg;
import org.hwyl.sexytopo.model.survey.Station;
import org.hwyl.sexytopo.model.survey.Survey;
import org.junit.Assert;
import org.junit.Test;

public class SurvexImporterTest {

    @Test
    public void testBasicImport() throws Exception {
        final String testContent = "1\t2\t5.0\t0.0\t0.0";
        Survey survey = new Survey();
        SurvexTherionImporter.parseCentreline(testContent, survey);
        Assert.assertEquals(2, survey.getAllStations().size());
    }

    @Test
    public void testBasicImportRecordsPromotions() throws Exception {
        final String testContent =
                "1\t2\t5.0\t0.0\t0.0\t; {from: 5.0 0.0 0.0, 5.0 0.0 0.0, 5.0 0.0 0.0}";
        Survey survey = new Survey();
        SurvexTherionImporter.parseCentreline(testContent, survey);
        Leg leg = survey.getOrigin().getConnectedOnwardLegs().get(0);
        Assert.assertEquals(3, leg.getPromotedFrom().length);
    }

    @Test
    public void testBasicImportHandlesComments() throws Exception {
        final String testContent =
                "1\t2\t5.0\t0.0\t0.0\t; {from: 5.0 0.0 0.0, 5.0 0.0 0.0, 5.0 0.0 0.0} testComment";
        Survey survey = new Survey();
        SurvexTherionImporter.parseCentreline(testContent, survey);
        Station created = survey.getStationByName("2");
        Assert.assertEquals("testComment", created.getComment());
    }

    @Test
    public void testHiddenOnSketchSplayIsImported() throws Exception {
        // A non-splay leg must come first to establish the survey origin; the splay follows
        final String testContent = "0\t1\t5.0\t0.0\t0.0\n;1\t-\t2.0\t90.0\t0.0";
        Survey survey = new Survey();
        SurvexTherionImporter.parseCentreline(testContent, survey);
        List<Leg> allLegs = survey.getAllLegsInChronoOrder();
        Assert.assertEquals(2, allLegs.size());
        Leg splay = allLegs.get(1);
        Assert.assertFalse(splay.hasDestination());
        Assert.assertTrue(splay.isHiddenOnSketch());
    }

    @Test
    public void testCommentedRealLegIsSkipped() throws Exception {
        // Commented-out real legs are not imported (hidden-on-sketch is splay-only)
        final String testContent = "0\t1\t5.0\t0.0\t0.0\n;1\t2\t3.0\t90.0\t0.0";
        Survey survey = new Survey();
        SurvexTherionImporter.parseCentreline(testContent, survey);
        Assert.assertEquals(1, survey.getAllLegsInChronoOrder().size());
    }

    @Test
    public void testPlainCommentLineIsSkipped() throws Exception {
        final String testContent = "; this is just a comment";
        Survey survey = new Survey();
        SurvexTherionImporter.parseCentreline(testContent, survey);
        Assert.assertEquals(0, survey.getAllLegs().size());
    }
}
