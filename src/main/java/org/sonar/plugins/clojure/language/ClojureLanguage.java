package org.sonar.plugins.clojure.language;

import java.util.Arrays;
import org.sonar.api.config.Configuration;
import org.sonar.api.resources.AbstractLanguage;

public class ClojureLanguage extends AbstractLanguage {

    public static final String KEY = "clojure";
    public static final String NAME = "Clojure";
    public static final String FILE_SUFFIXES_KEY = "sonar.clojure.file.suffixes";
    public static final String DEFAULT_SUFFIXES = ".clj,.cljs,.cljc,.edn";

    private final Configuration configuration;

    public ClojureLanguage(Configuration configuration) {
        super(KEY, NAME);
        this.configuration = configuration;
    }

    @Override
    public String[] getFileSuffixes() {
        String[] suffixes = Arrays.stream(configuration.getStringArray(FILE_SUFFIXES_KEY))
            .filter(s -> s != null && !s.trim().isEmpty())
            .toArray(String[]::new);

        return suffixes.length > 0 ? suffixes : DEFAULT_SUFFIXES.split(",");
    }
}
