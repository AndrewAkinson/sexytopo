package org.hwyl.sexytopo.control.io.thirdparty.therion;

import org.hwyl.sexytopo.control.util.GeneralPreferences;
import org.hwyl.sexytopo.control.util.TextTools;
import org.hwyl.sexytopo.model.survey.Survey;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ThconfigExporter {

    public static final String SURVEYNAME_PLACEHOLDER = "<surveyname>";

    public static final String DEFAULT_LAYOUT =
        "layout local\n" +
        "  debug off\n" +
        "  # map-header 0 0 off\n" +
        "  # symbol-hide group cave-centreline\n" + // if on Therion can fail to compile the survey
        "endlayout";

    public static String getDefaultContent() {
        String[] lines = new String[] {
            TherionExporter.getEncodingText(),
            DEFAULT_LAYOUT,
            "source \"" + SURVEYNAME_PLACEHOLDER + ".th\"",
            "export model -fmt survex -o \"" + SURVEYNAME_PLACEHOLDER + "-th.3d\"",
            "export map -proj plan -layout local -o \"" + SURVEYNAME_PLACEHOLDER + "-plan.pdf\"",
            "export map -proj extended -layout local -o \"" + SURVEYNAME_PLACEHOLDER + "-ee.pdf\""
        };

        return TextTools.join("\n\n", Arrays.asList(lines));
    }

    public static String getContent(Survey survey) {
        String name = survey.getName();

        String template = GeneralPreferences.getTherionThconfigTemplate();
        if (template.isEmpty()) {
            template = getDefaultContent();
        }

        return replaceSurveyname(template, name);
    }

    public static String getThconfigFilename(String surveyName) {
        String pattern = GeneralPreferences.getTherionThconfigName();
        return replaceSurveyname(pattern, surveyName);
    }

    /**
     * Validates the template text.
     * Returns true if:
     * - There are no angle bracket patterns (<...>) at all, OR
     * - All angle bracket patterns contain "surveyname" (case-insensitive)
     * Returns false if any angle bracket pattern contains something other than "surveyname"
     */
    public static boolean isValidTemplate(String text) {
        Pattern pattern = Pattern.compile("<([^>]+)>");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String content = matcher.group(1);
            if (content == null || !content.equalsIgnoreCase("surveyname")) {
                return false;
            }
        }
        return true;
    }

    private static String replaceSurveyname(String text, String surveyName) {
        return text.replaceAll("(?i)" + java.util.regex.Pattern.quote(SURVEYNAME_PLACEHOLDER), surveyName);
    }

}
