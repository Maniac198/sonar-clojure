package org.sonar.plugins.clojure.settings;

import java.util.List;
import org.sonar.api.PropertyType;
import org.sonar.api.config.PropertyDefinition;

public final class KondoProperties {

    public static final String ENABLED_KEY = "sonar.clojure.kondo.enabled";
    public static final String PATH_KEY = "sonar.clojure.kondo.path";
    public static final String LINT_PATHS_KEY = "sonar.clojure.kondo.lintPaths";
    public static final String CONFIG_KEY = "sonar.clojure.kondo.config";
    public static final String CONFIG_PATH_KEY = "sonar.clojure.kondo.configPath";
    public static final String ARGUMENTS_KEY = "sonar.clojure.kondo.arguments";
    public static final String TIMEOUT_KEY = "sonar.clojure.kondo.timeout";

    public static final boolean ENABLED_DEFAULT = true;
    public static final String PATH_DEFAULT = "clj-kondo";
    public static final String LINT_PATHS_DEFAULT = ".";
    public static final String CONFIG_DEFAULT = "{:output {:format :json}}";
    public static final String CONFIG_PATH_DEFAULT = "";
    public static final String ARGUMENTS_DEFAULT = "";
    public static final String TIMEOUT_DEFAULT = "300";

    private KondoProperties() {
    }

    public static List<PropertyDefinition> getProperties() {
        return List.of(
            PropertyDefinition.builder(ENABLED_KEY)
                .name("Enable clj-kondo")
                .description("Run clj-kondo during analysis.")
                .category("Clojure")
                .subCategory("clj-kondo")
                .defaultValue(Boolean.toString(ENABLED_DEFAULT))
                .type(PropertyType.BOOLEAN)
                .build(),
            PropertyDefinition.builder(PATH_KEY)
                .name("clj-kondo binary path")
                .description("Path to the clj-kondo executable (defaults to PATH lookup).")
                .category("Clojure")
                .subCategory("clj-kondo")
                .defaultValue(PATH_DEFAULT)
                .type(PropertyType.STRING)
                .build(),
            PropertyDefinition.builder(LINT_PATHS_KEY)
                .name("clj-kondo lint paths")
                .description("Comma-separated list of paths passed to --lint.")
                .category("Clojure")
                .subCategory("clj-kondo")
                .defaultValue(LINT_PATHS_DEFAULT)
                .type(PropertyType.STRING)
                .build(),
            PropertyDefinition.builder(CONFIG_KEY)
                .name("clj-kondo config (EDN)")
                .description("EDN config passed to --config when configPath is empty.")
                .category("Clojure")
                .subCategory("clj-kondo")
                .defaultValue(CONFIG_DEFAULT)
                .type(PropertyType.TEXT)
                .build(),
            PropertyDefinition.builder(CONFIG_PATH_KEY)
                .name("clj-kondo config path")
                .description("Path to a clj-kondo config file (overrides the EDN config).")
                .category("Clojure")
                .subCategory("clj-kondo")
                .defaultValue(CONFIG_PATH_DEFAULT)
                .type(PropertyType.STRING)
                .build(),
            PropertyDefinition.builder(ARGUMENTS_KEY)
                .name("clj-kondo extra arguments")
                .description("Additional arguments appended to the clj-kondo command.")
                .category("Clojure")
                .subCategory("clj-kondo")
                .defaultValue(ARGUMENTS_DEFAULT)
                .type(PropertyType.STRING)
                .build(),
            PropertyDefinition.builder(TIMEOUT_KEY)
                .name("clj-kondo timeout")
                .description("Timeout for clj-kondo execution in seconds.")
                .category("Clojure")
                .subCategory("clj-kondo")
                .defaultValue(TIMEOUT_DEFAULT)
                .type(PropertyType.INTEGER)
                .build()
        );
    }
}
