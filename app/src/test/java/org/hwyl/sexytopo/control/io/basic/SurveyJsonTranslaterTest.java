package org.hwyl.sexytopo.control.io.basic;

import java.util.List;
import org.hwyl.sexytopo.model.survey.Leg;
import org.hwyl.sexytopo.model.survey.Survey;
import org.hwyl.sexytopo.testutils.BasicTestSurveyCreator;
import org.hwyl.sexytopo.testutils.ExampleSurveyCreator;
import org.hwyl.sexytopo.testutils.SurveyChecker;
import org.junit.Assert;
import org.junit.Test;

public class SurveyJsonTranslaterTest {

    @Test
    public void testEmptySurveyResultsIn1Station() throws Exception {
        Survey survey = new Survey();
        String text = SurveyJsonTranslater.toText(survey, "test", 0);

        Survey newSurvey = new Survey();
        SurveyJsonTranslater.populateSurvey(survey, text);
        assert newSurvey.getAllStations().size() == 1;
    }

    @Test
    public void testSimpleSurveyIsParsed() throws Exception {
        Survey survey = BasicTestSurveyCreator.createStraightNorth();
        String text = SurveyJsonTranslater.toText(survey, "test", 0);

        Survey newSurvey = new Survey();
        SurveyJsonTranslater.populateSurvey(survey, text);

        SurveyChecker.areEqual(survey, newSurvey);
    }

    @Test
    public void testSlightlyBiggerSurveyIsParsed() throws Exception {
        Survey survey = BasicTestSurveyCreator.createRightRight();
        String text = SurveyJsonTranslater.toText(survey, "test", 0);

        Survey newSurvey = new Survey();
        SurveyJsonTranslater.populateSurvey(survey, text);

        SurveyChecker.areEqual(survey, newSurvey);
    }

    @Test
    public void testRandomSurveyIsParsed() throws Exception {
        Survey survey = ExampleSurveyCreator.create(10, 10);
        String text = SurveyJsonTranslater.toText(survey, "test", 0);

        Survey newSurvey = new Survey();
        SurveyJsonTranslater.populateSurvey(survey, text);

        SurveyChecker.areEqual(survey, newSurvey);
    }

    @Test
    public void testSurveyWithTripsAreParsed() throws Exception {
        Survey survey = BasicTestSurveyCreator.createStraightNorthWithTrip();
        String text = SurveyJsonTranslater.toText(survey, "test", 0);

        Survey newSurvey = new Survey();
        SurveyJsonTranslater.populateSurvey(survey, text);

        SurveyChecker.areEqual(survey, newSurvey);
    }

    @Test
    public void testHiddenOnSketchSplayIsPreservedInRoundTrip() throws Exception {
        Survey survey = BasicTestSurveyCreator.createStraightNorth();
        // Add a hidden-on-sketch splay at the origin
        Leg splay = new Leg(3.0f, 90.0f, 0.0f);
        splay.setHiddenOnSketch(true);
        survey.getOrigin().addOnwardLeg(splay);
        survey.addLegRecord(splay);

        String text = SurveyJsonTranslater.toText(survey, "test", 0);

        Survey reloaded = new Survey();
        SurveyJsonTranslater.populateSurvey(reloaded, text);

        List<Leg> reloadedSplays =
                reloaded.getAllLegsInChronoOrder().stream()
                        .filter(l -> !l.hasDestination())
                        .collect(java.util.stream.Collectors.toList());
        Assert.assertEquals(1, reloadedSplays.size());
        Assert.assertTrue(reloadedSplays.get(0).isHiddenOnSketch());
    }
}
