package org.sonar.plugins.clojure.settings;

import java.util.List;
import org.sonar.api.PropertyType;
import org.sonar.api.config.PropertyDefinition;

public final class NvdProperties {

    public static final String ENABLED_KEY = "sonar.clojure.nvd.enabled";
    public static final String RUN_KEY = "sonar.clojure.nvd.run";
    public static final String COMMAND_KEY = "sonar.clojure.nvd.command";
    public static final String ARGUMENTS_KEY = "sonar.clojure.nvd.arguments";
    public static final String REPORT_PATH_KEY = "sonar.clojure.nvd.reportPath";
    public static final String TIMEOUT_KEY = "sonar.clojure.nvd.timeout";

    public static final boolean ENABLED_DEFAULT = true;
    public static final boolean RUN_DEFAULT = false;
    public static final String COMMAND_DEFAULT = "lein nvd check";
    public static final String ARGUMENTS_DEFAULT = "";
    public static final String REPORT_PATH_DEFAULT = "target/nvd/dependency-check-report.json";
    public static final String TIMEOUT_DEFAULT = "300";

    private NvdProperties() {
    }

    public static List<PropertyDefinition> getProperties() {
        return List.of(
            PropertyDefinition.builder(ENABLED_KEY)
                .name("Enable NVD (lein-nvd)")
                .description("Import NVD report during analysis.")
                .category("Clojure")
                .subCategory("NVD")
                .defaultValue(Boolean.toString(ENABLED_DEFAULT))
                .type(PropertyType.BOOLEAN)
                .build(),
            PropertyDefinition.builder(RUN_KEY)
                .name("Run NVD (lein-nvd)")
                .description("Run lein-nvd during analysis before importing the report.")
                .category("Clojure")
                .subCategory("NVD")
                .defaultValue(Boolean.toString(RUN_DEFAULT))
                .type(PropertyType.BOOLEAN)
                .build(),
            PropertyDefinition.builder(COMMAND_KEY)
                .name("NVD command")
                .description("Base command used to run lein-nvd (e.g. 'lein nvd check').")
                .category("Clojure")
                .subCategory("NVD")
                .defaultValue(COMMAND_DEFAULT)
                .type(PropertyType.STRING)
                .build(),
            PropertyDefinition.builder(ARGUMENTS_KEY)
                .name("NVD extra arguments")
                .description("Additional arguments appended to the lein-nvd command.")
                .category("Clojure")
                .subCategory("NVD")
                .defaultValue(ARGUMENTS_DEFAULT)
                .type(PropertyType.STRING)
                .build(),
            PropertyDefinition.builder(REPORT_PATH_KEY)
                .name("NVD report path")
                .description("Path to the dependency-check JSON report produced by lein-nvd.")
                .category("Clojure")
                .subCategory("NVD")
                .defaultValue(REPORT_PATH_DEFAULT)
                .type(PropertyType.STRING)
                .build(),
            PropertyDefinition.builder(TIMEOUT_KEY)
                .name("NVD timeout")
                .description("Timeout for lein-nvd execution in seconds.")
                .category("Clojure")
                .subCategory("NVD")
                .defaultValue(TIMEOUT_DEFAULT)
                .type(PropertyType.INTEGER)
                .build()
        );
    }
}
