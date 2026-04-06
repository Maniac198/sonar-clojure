package org.sonar.plugins.clojure.sensors.eastwood;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EastwoodIssueParser {

    private static final Pattern EASTWOOD_PATTERN =
        Pattern.compile("^(.+?):(\\d+):(\\d+):\\s*([\\w-]+):\\s*(.+)$");

    private EastwoodIssueParser() {
    }

    public static List<EastwoodFinding> parse(String output) {
        if (output == null || output.isBlank()) {
            return Collections.emptyList();
        }

        List<EastwoodFinding> findings = new ArrayList<>();
        String[] lines = output.split("\\r?\\n");
        for (String line : lines) {
            Matcher matcher = EASTWOOD_PATTERN.matcher(line.trim());
            if (!matcher.matches()) {
                continue;
            }

            String filename = normalizeFilename(matcher.group(1));
            int row = parseInt(matcher.group(2));
            int col = parseInt(matcher.group(3));
            String ruleId = matcher.group(4);
            String message = matcher.group(5);
            findings.add(new EastwoodFinding(filename, row, col, ruleId, message));
        }

        return findings;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static String normalizeFilename(String filename) {
        if (filename == null) {
            return null;
        }
        if (filename.startsWith("./")) {
            return filename.substring(2);
        }
        if (filename.startsWith(".\\")) {
            return filename.substring(2);
        }
        return filename;
    }
}
