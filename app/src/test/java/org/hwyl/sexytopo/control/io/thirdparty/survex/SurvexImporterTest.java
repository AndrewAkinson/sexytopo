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
        Assert.assertTrue(splay.isHiddenOnSketch());
    }

    @Test
    public void testHiddenOnSketchLegIsImported() throws Exception {
        final String testContent = ";0\t1\t5.0\t0.0\t0.0";
        Survey survey = new Survey();
        SurvexTherionImporter.parseCentreline(testContent, survey);
        Assert.assertEquals(1, survey.getAllLegs().size());
        Leg leg = survey.getAllLegs().get(0);
        Assert.assertTrue(leg.isHiddenOnSketch());
        Assert.assertNotNull(survey.getStationByName("1"));
    }

    @Test
    public void testNormalLegIsNotHiddenOnSketch() throws Exception {
        final String testContent = "0\t1\t5.0\t0.0\t0.0";
        Survey survey = new Survey();
        SurvexTherionImporter.parseCentreline(testContent, survey);
        Assert.assertFalse(survey.getAllLegs().get(0).isHiddenOnSketch());
    }

    @Test
    public void testPlainCommentLineIsSkipped() throws Exception {
        final String testContent = "; this is just a comment";
        Survey survey = new Survey();
        SurvexTherionImporter.parseCentreline(testContent, survey);
        Assert.assertEquals(0, survey.getAllLegs().size());
    }

    @Test
    public void testHiddenOnSketchPromotedLegPreservesPromotedFrom() throws Exception {
        final String testContent =
                ";0\t1\t5.541\t253.93\t4.67\n"
                        + ";;0\t1\t5.542\t73.95\t-4.64\n"
                        + ";;0\t1\t5.541\t73.93\t-4.69";
        Survey survey = new Survey();
        SurvexTherionImporter.parseCentreline(testContent, survey);
        Assert.assertEquals(1, survey.getAllLegs().size());
        Leg leg = survey.getAllLegs().get(0);
        Assert.assertTrue(leg.isHiddenOnSketch());
        Assert.assertEquals(2, leg.getPromotedFrom().length);
    }
}
