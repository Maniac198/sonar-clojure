package org.sonar.plugins.clojure.language;

import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;
import org.sonar.plugins.clojure.rules.ClojureRulesDefinition;

public class ClojureBuiltInQualityProfile implements BuiltInQualityProfilesDefinition {

    public static final String PROFILE_NAME = "Sonar way";

    @Override
    public void define(Context context) {
        NewBuiltInQualityProfile profile =
            context.createBuiltInQualityProfile(PROFILE_NAME, ClojureLanguage.KEY);
        for (String ruleKey : ClojureRulesDefinition.SONAR_WAY_RULE_KEYS) {
            profile.activateRule(ClojureRulesDefinition.REPOSITORY_KEY, ruleKey);
        }
        profile.done();
    }
}
