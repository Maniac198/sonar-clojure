package org.sonar.plugins.clojure.sensors.ancient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AncientOutputParser {

    private static final Pattern ANSI_PATTERN = Pattern.compile("\\u001B\\[[;\\d]*m");
    private static final Pattern ANCIENT_PATTERN =
        Pattern.compile("\\[([^\\s]+)\\s+\"([^\"]+)\"]([^\"\\n]*)\"([^\"]+)\"");

    private AncientOutputParser() {
    }

    public static List<AncientDependency> parse(String output) {
        if (output == null || output.isBlank()) {
            return Collections.emptyList();
        }

        String cleaned = ANSI_PATTERN.matcher(output).replaceAll("");
        String[] lines = cleaned.split("\\r?\\n");
        List<AncientDependency> dependencies = new ArrayList<>();
        for (String line : lines) {
            Matcher matcher = ANCIENT_PATTERN.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            String name = matcher.group(1);
            String available = matcher.group(2);
            String current = matcher.group(4);
            dependencies.add(new AncientDependency(name, current, available));
        }
        return dependencies;
    }
}
