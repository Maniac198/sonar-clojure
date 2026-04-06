package org.sonar.plugins.clojure.settings;

import java.util.List;
import org.sonar.api.PropertyType;
import org.sonar.api.config.PropertyDefinition;

public final class EastwoodProperties {

    public static final String ENABLED_KEY = "sonar.clojure.eastwood.enabled";
    public static final String COMMAND_KEY = "sonar.clojure.eastwood.command";
    public static final String OPTIONS_KEY = "sonar.clojure.eastwood.options";
    public static final String ARGUMENTS_KEY = "sonar.clojure.eastwood.arguments";
    public static final String TIMEOUT_KEY = "sonar.clojure.eastwood.timeout";

    public static final boolean ENABLED_DEFAULT = true;
    public static final String COMMAND_DEFAULT = "lein eastwood";
    public static final String OPTIONS_DEFAULT = "";
    public static final String ARGUMENTS_DEFAULT = "";
    public static final String TIMEOUT_DEFAULT = "300";

    private EastwoodProperties() {
    }

    public static List<PropertyDefinition> getProperties() {
        return List.of(
            PropertyDefinition.builder(ENABLED_KEY)
                .name("Enable Eastwood")
                .description("Run Eastwood during analysis.")
                .category("Clojure")
                .subCategory("Eastwood")
                .defaultValue(Boolean.toString(ENABLED_DEFAULT))
                .type(PropertyType.BOOLEAN)
                .build(),
            PropertyDefinition.builder(COMMAND_KEY)
                .name("Eastwood command")
                .description("Base command used to run Eastwood (e.g. 'lein eastwood').")
                .category("Clojure")
                .subCategory("Eastwood")
                .defaultValue(COMMAND_DEFAULT)
                .type(PropertyType.STRING)
                .build(),
            PropertyDefinition.builder(OPTIONS_KEY)
                .name("Eastwood options")
                .description("Optional EDN options passed as a single argument.")
                .category("Clojure")
                .subCategory("Eastwood")
                .defaultValue(OPTIONS_DEFAULT)
                .type(PropertyType.TEXT)
                .build(),
            PropertyDefinition.builder(ARGUMENTS_KEY)
                .name("Eastwood extra arguments")
                .description("Additional arguments appended to the Eastwood command.")
                .category("Clojure")
                .subCategory("Eastwood")
                .defaultValue(ARGUMENTS_DEFAULT)
                .type(PropertyType.STRING)
                .build(),
            PropertyDefinition.builder(TIMEOUT_KEY)
                .name("Eastwood timeout")
                .description("Timeout for Eastwood execution in seconds.")
                .category("Clojure")
                .subCategory("Eastwood")
                .defaultValue(TIMEOUT_DEFAULT)
                .type(PropertyType.INTEGER)
                .build()
        );
    }
}
