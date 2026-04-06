package org.sonar.plugins.clojure;

import org.sonar.api.Plugin;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.plugins.clojure.language.ClojureBuiltInQualityProfile;
import org.sonar.plugins.clojure.language.ClojureLanguage;
import org.sonar.plugins.clojure.rules.ClojureRulesDefinition;
import org.sonar.plugins.clojure.sensors.ancient.AncientSensor;
import org.sonar.plugins.clojure.sensors.cloverage.CloverageSensor;
import org.sonar.plugins.clojure.sensors.eastwood.EastwoodSensor;
import org.sonar.plugins.clojure.sensors.kibit.KibitSensor;
import org.sonar.plugins.clojure.sensors.kondo.KondoSensor;
import org.sonar.plugins.clojure.sensors.metrics.ClojureMetricsSensor;
import org.sonar.plugins.clojure.sensors.nvd.NvdSensor;
import org.sonar.plugins.clojure.settings.AncientProperties;
import org.sonar.plugins.clojure.settings.CloverageProperties;
import org.sonar.plugins.clojure.settings.EastwoodProperties;
import org.sonar.plugins.clojure.settings.KibitProperties;
import org.sonar.plugins.clojure.settings.KondoProperties;
import org.sonar.plugins.clojure.settings.NvdProperties;

public class ClojurePlugin implements Plugin {

    @Override
    public void define(Context context) {
        context.addExtension(ClojureLanguage.class);
        context.addExtension(ClojureBuiltInQualityProfile.class);
        context.addExtension(ClojureRulesDefinition.class);
        context.addExtension(KondoSensor.class);
        context.addExtension(KibitSensor.class);
        context.addExtension(EastwoodSensor.class);
        context.addExtension(CloverageSensor.class);
        context.addExtension(AncientSensor.class);
        context.addExtension(NvdSensor.class);
        context.addExtension(ClojureMetricsSensor.class);
        context.addExtensions(KondoProperties.getProperties());
        context.addExtensions(KibitProperties.getProperties());
        context.addExtensions(EastwoodProperties.getProperties());
        context.addExtensions(CloverageProperties.getProperties());
        context.addExtensions(AncientProperties.getProperties());
        context.addExtensions(NvdProperties.getProperties());
        context.addExtension(
            PropertyDefinition.builder(ClojureLanguage.FILE_SUFFIXES_KEY)
                .name("Clojure File Suffixes")
                .description("Comma-separated list of file suffixes to analyze.")
                .defaultValue(ClojureLanguage.DEFAULT_SUFFIXES)
                .category("Clojure")
                .subCategory("General")
                .onConfigScopes(PropertyDefinition.ConfigScope.PROJECT)
                .multiValues(true)
                .build()
        );
    }
}
