package org.sonar.plugins.clojure.sensors.kondo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KondoIssueParser {

    private static final Pattern TEXT_PATTERN =
        Pattern.compile("^(.+?):(\\d+):(\\d+):\\s*(\\w+):\\s*(.+)$");

    private KondoIssueParser() {
    }

    public static List<KondoFinding> parse(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        List<KondoFinding> findings = parseJson(json);
        if (!findings.isEmpty()) {
            return findings;
        }
        return parseText(json);
    }

    private static List<KondoFinding> parseJson(String json) {
        JsonElement root;
        try {
            root = new JsonParser().parse(json);
        } catch (RuntimeException ex) {
            return Collections.emptyList();
        }
        JsonArray findingsArray = null;

        if (root.isJsonObject()) {
            JsonObject rootObject = root.getAsJsonObject();
            if (rootObject.has("findings") && rootObject.get("findings").isJsonArray()) {
                findingsArray = rootObject.getAsJsonArray("findings");
            }
        } else if (root.isJsonArray()) {
            findingsArray = root.getAsJsonArray();
        }

        if (findingsArray == null) {
            return Collections.emptyList();
        }

        List<KondoFinding> findings = new ArrayList<>();
        for (JsonElement element : findingsArray) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            String filename = normalizeFilename(getString(obj, "filename"));
            int row = getInt(obj, "row");
            int col = getInt(obj, "col");
            int endRow = getInt(obj, "end-row");
            int endCol = getInt(obj, "end-col");
            String level = getString(obj, "level");
            String message = getString(obj, "message");
            String type = getString(obj, "type");
            findings.add(new KondoFinding(filename, row, col, endRow, endCol, level, message, type));
        }

        return findings;
    }

    private static List<KondoFinding> parseText(String output) {
        if (output == null || output.isBlank()) {
            return Collections.emptyList();
        }

        List<KondoFinding> findings = new ArrayList<>();
        String[] lines = output.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("linting took")) {
                continue;
            }

            Matcher matcher = TEXT_PATTERN.matcher(trimmed);
            if (!matcher.matches()) {
                continue;
            }

            String filename = normalizeFilename(matcher.group(1));
            int row = parseInt(matcher.group(2));
            int col = parseInt(matcher.group(3));
            String level = matcher.group(4);
            String message = matcher.group(5);
            findings.add(new KondoFinding(filename, row, col, -1, -1, level, message, null));
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

    private static String getString(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }

    private static int getInt(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return -1;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
