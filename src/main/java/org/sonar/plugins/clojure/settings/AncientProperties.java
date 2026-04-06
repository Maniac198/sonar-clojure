package org.sonar.plugins.clojure.settings;

import java.util.List;
import org.sonar.api.PropertyType;
import org.sonar.api.config.PropertyDefinition;

public final class AncientProperties {

    public static final String ENABLED_KEY = "sonar.clojure.ancient.enabled";
    public static final String COMMAND_KEY = "sonar.clojure.ancient.command";
    public static final String ARGUMENTS_KEY = "sonar.clojure.ancient.arguments";
    public static final String TIMEOUT_KEY = "sonar.clojure.ancient.timeout";

    public static final boolean ENABLED_DEFAULT = true;
    public static final String COMMAND_DEFAULT = "lein ancient";
    public static final String ARGUMENTS_DEFAULT = "";
    public static final String TIMEOUT_DEFAULT = "300";

    private AncientProperties() {
    }

    public static List<PropertyDefinition> getProperties() {
        return List.of(
            PropertyDefinition.builder(ENABLED_KEY)
                .name("Enable Lein Ancient")
                .description("Run Lein Ancient during analysis.")
                .category("Clojure")
                .subCategory("Lein Ancient")
                .defaultValue(Boolean.toString(ENABLED_DEFAULT))
                .type(PropertyType.BOOLEAN)
                .build(),
            PropertyDefinition.builder(COMMAND_KEY)
                .name("Lein Ancient command")
                .description("Base command used to run Lein Ancient (e.g. 'lein ancient').")
                .category("Clojure")
                .subCategory("Lein Ancient")
                .defaultValue(COMMAND_DEFAULT)
                .type(PropertyType.STRING)
                .build(),
            PropertyDefinition.builder(ARGUMENTS_KEY)
                .name("Lein Ancient extra arguments")
                .description("Additional arguments appended to the Lein Ancient command.")
                .category("Clojure")
                .subCategory("Lein Ancient")
                .defaultValue(ARGUMENTS_DEFAULT)
                .type(PropertyType.STRING)
                .build(),
            PropertyDefinition.builder(TIMEOUT_KEY)
                .name("Lein Ancient timeout")
                .description("Timeout for Lein Ancient execution in seconds.")
                .category("Clojure")
                .subCategory("Lein Ancient")
                .defaultValue(TIMEOUT_DEFAULT)
                .type(PropertyType.INTEGER)
                .build()
        );
    }
}
