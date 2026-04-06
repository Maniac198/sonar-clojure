package org.sonar.plugins.clojure.settings;

import java.util.List;
import org.sonar.api.PropertyType;
import org.sonar.api.config.PropertyDefinition;

public final class CloverageProperties {

    public static final String ENABLED_KEY = "sonar.clojure.cloverage.enabled";
    public static final String RUN_KEY = "sonar.clojure.cloverage.run";
    public static final String COMMAND_KEY = "sonar.clojure.cloverage.command";
    public static final String ARGUMENTS_KEY = "sonar.clojure.cloverage.arguments";
    public static final String REPORT_PATH_KEY = "sonar.clojure.cloverage.reportPath";
    public static final String TIMEOUT_KEY = "sonar.clojure.cloverage.timeout";

    public static final boolean ENABLED_DEFAULT = true;
    public static final boolean RUN_DEFAULT = false;
    public static final String COMMAND_DEFAULT = "lein cloverage --codecov";
    public static final String ARGUMENTS_DEFAULT = "";
    public static final String REPORT_PATH_DEFAULT = "target/coverage/codecov.json";
    public static final String TIMEOUT_DEFAULT = "300";

    private CloverageProperties() {
    }

    public static List<PropertyDefinition> getProperties() {
        return List.of(
            PropertyDefinition.builder(ENABLED_KEY)
                .name("Enable Cloverage")
                .description("Import Cloverage coverage during analysis.")
                .category("Clojure")
                .subCategory("Cloverage")
                .defaultValue(Boolean.toString(ENABLED_DEFAULT))
                .type(PropertyType.BOOLEAN)
                .build(),
            PropertyDefinition.builder(RUN_KEY)
                .name("Run Cloverage")
                .description("Run Cloverage before importing the report.")
                .category("Clojure")
                .subCategory("Cloverage")
                .defaultValue(Boolean.toString(RUN_DEFAULT))
                .type(PropertyType.BOOLEAN)
                .build(),
            PropertyDefinition.builder(COMMAND_KEY)
                .name("Cloverage command")
                .description("Base command used to run Cloverage (e.g. 'lein cloverage --codecov').")
                .category("Clojure")
                .subCategory("Cloverage")
                .defaultValue(COMMAND_DEFAULT)
                .type(PropertyType.STRING)
                .build(),
            PropertyDefinition.builder(ARGUMENTS_KEY)
                .name("Cloverage extra arguments")
                .description("Additional arguments appended to the Cloverage command.")
                .category("Clojure")
                .subCategory("Cloverage")
                .defaultValue(ARGUMENTS_DEFAULT)
                .type(PropertyType.STRING)
                .build(),
            PropertyDefinition.builder(REPORT_PATH_KEY)
                .name("Cloverage report path")
                .description("Path to the Cloverage codecov JSON report.")
                .category("Clojure")
                .subCategory("Cloverage")
                .defaultValue(REPORT_PATH_DEFAULT)
                .type(PropertyType.STRING)
                .build(),
            PropertyDefinition.builder(TIMEOUT_KEY)
                .name("Cloverage timeout")
                .description("Timeout for Cloverage execution in seconds.")
                .category("Clojure")
                .subCategory("Cloverage")
                .defaultValue(TIMEOUT_DEFAULT)
                .type(PropertyType.INTEGER)
                .build()
        );
    }
}
