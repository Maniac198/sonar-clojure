package org.sonar.plugins.clojure.rules;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinitionXmlLoader;
import org.sonar.plugins.clojure.language.ClojureLanguage;

public class ClojureRulesDefinition implements RulesDefinition {

    public static final String REPOSITORY_KEY = "clojure";
    public static final String REPOSITORY_NAME = "Clojure";
    public static final String KONDO_RULE_KEY = "clj-kondo";
    public static final String KIBIT_RULE_KEY = "kibit";
    public static final String EASTWOOD_RULE_KEY = "eastwood";
    public static final String ANCIENT_RULE_KEY = "lein-ancient";
    public static final String NVD_RULE_KEY = "nvd";
    public static final List<String> SONAR_WAY_RULE_KEYS = List.of(
        KONDO_RULE_KEY,
        "clj-kondo-unused-binding",
        "clj-kondo-unused-import",
        "clj-kondo-unused-namespace",
        "clj-kondo-unused-referred-var",
        "clj-kondo-unused-private-var",
        "clj-kondo-unresolved-symbol",
        "clj-kondo-unresolved-namespace",
        "clj-kondo-invalid-arity",
        "clj-kondo-redefined-var",
        "clj-kondo-shadowed-var",
        "clj-kondo-missing-docstring",
        "clj-kondo-duplicate-require",
        "clj-kondo-redundant-do",
        KIBIT_RULE_KEY,
        "kibit-threading-macro",
        "kibit-redundant-anonymous-function",
        "kibit-use-into",
        EASTWOOD_RULE_KEY,
        "eastwood-unused-locals",
        "eastwood-unused-namespace",
        "eastwood-constant-test",
        "eastwood-reflection-warning",
        "eastwood-wrong-arity",
        ANCIENT_RULE_KEY,
        NVD_RULE_KEY
    );
    private static final String RULES_PATH = "/clojure/rules.xml";

    private final RulesDefinitionXmlLoader xmlLoader;

    public ClojureRulesDefinition(RulesDefinitionXmlLoader xmlLoader) {
        this.xmlLoader = xmlLoader;
    }

    @Override
    public void define(Context context) {
        NewRepository repository =
            context.createRepository(REPOSITORY_KEY, ClojureLanguage.KEY)
                .setName(REPOSITORY_NAME);

        xmlLoader.load(
            repository,
            getClass().getResourceAsStream(RULES_PATH),
            StandardCharsets.UTF_8.name()
        );

        repository.done();
    }
}
