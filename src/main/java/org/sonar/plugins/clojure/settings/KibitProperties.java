package org.sonar.plugins.clojure.settings;

import java.util.List;
import org.sonar.api.PropertyType;
import org.sonar.api.config.PropertyDefinition;

public final class KibitProperties {

    public static final String ENABLED_KEY = "sonar.clojure.kibit.enabled";
    public static final String COMMAND_KEY = "sonar.clojure.kibit.command";
    public static final String PATHS_KEY = "sonar.clojure.kibit.paths";
    public static final String ARGUMENTS_KEY = "sonar.clojure.kibit.arguments";
    public static final String TIMEOUT_KEY = "sonar.clojure.kibit.timeout";

    public static final boolean ENABLED_DEFAULT = true;
    public static final String COMMAND_DEFAULT = "lein kibit";
    public static final String PATHS_DEFAULT = "";
    public static final String ARGUMENTS_DEFAULT = "";
    public static final String TIMEOUT_DEFAULT = "300";

    private KibitProperties() {
    }

    public static List<PropertyDefinition> getProperties() {
        return List.of(
            PropertyDefinition.builder(ENABLED_KEY)
                .name("Enable Kibit")
                .description("Run Kibit during analysis.")
                .category("Clojure")
                .subCategory("Kibit")
                .defaultValue(Boolean.toString(ENABLED_DEFAULT))
                .type(PropertyType.BOOLEAN)
                .build(),
            PropertyDefinition.builder(COMMAND_KEY)
                .name("Kibit command")
                .description("Base command used to run Kibit (e.g. 'lein kibit' or 'kibit').")
                .category("Clojure")
                .subCategory("Kibit")
                .defaultValue(COMMAND_DEFAULT)
                .type(PropertyType.STRING)
                .build(),
            PropertyDefinition.builder(PATHS_KEY)
                .name("Kibit paths")
                .description("Comma-separated list of paths passed to Kibit.")
                .category("Clojure")
                .subCategory("Kibit")
                .defaultValue(PATHS_DEFAULT)
                .type(PropertyType.STRING)
                .build(),
            PropertyDefinition.builder(ARGUMENTS_KEY)
                .name("Kibit extra arguments")
                .description("Additional arguments appended to the Kibit command.")
                .category("Clojure")
                .subCategory("Kibit")
                .defaultValue(ARGUMENTS_DEFAULT)
                .type(PropertyType.STRING)
                .build(),
            PropertyDefinition.builder(TIMEOUT_KEY)
                .name("Kibit timeout")
                .description("Timeout for Kibit execution in seconds.")
                .category("Clojure")
                .subCategory("Kibit")
                .defaultValue(TIMEOUT_DEFAULT)
                .type(PropertyType.INTEGER)
                .build()
        );
    }
}
