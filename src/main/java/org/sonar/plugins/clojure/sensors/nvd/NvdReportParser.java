package org.sonar.plugins.clojure.sensors.nvd;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public final class NvdReportParser {

    private static final Logger LOG = Loggers.get(NvdReportParser.class);

    private NvdReportParser() {
    }

    public static List<NvdFinding> parse(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }

        JsonElement root;
        try {
            root = new JsonParser().parse(json);
        } catch (RuntimeException ex) {
            LOG.warn("Failed to parse NVD report JSON", ex);
            return Collections.emptyList();
        }

        if (!root.isJsonObject()) {
            return Collections.emptyList();
        }

        JsonObject rootObject = root.getAsJsonObject();
        JsonArray dependencies = getArray(rootObject, "dependencies");
        if (dependencies == null) {
            return Collections.emptyList();
        }

        List<NvdFinding> findings = new ArrayList<>();
        for (JsonElement dependencyElement : dependencies) {
            if (!dependencyElement.isJsonObject()) {
                continue;
            }
            JsonObject dependency = dependencyElement.getAsJsonObject();
            String dependencyName = extractDependencyName(dependency);
            JsonArray vulnerabilities = getArray(dependency, "vulnerabilities");
            if (vulnerabilities == null) {
                continue;
            }

            for (JsonElement vulnElement : vulnerabilities) {
                if (!vulnElement.isJsonObject()) {
                    continue;
                }
                JsonObject vuln = vulnElement.getAsJsonObject();
                String cve = firstNonBlank(
                    getString(vuln, "name"),
                    getString(vuln, "cve"),
                    getString(vuln, "id")
                );
                String severity = firstNonBlank(
                    getString(vuln, "severity"),
                    getString(getObject(vuln, "cvssv3"), "baseSeverity"),
                    getString(getObject(vuln, "cvssv31"), "baseSeverity")
                );
                Double score = firstNonNull(
                    getScore(getObject(vuln, "cvssv3"), "baseScore"),
                    getScore(getObject(vuln, "cvssv31"), "baseScore"),
                    getScore(getObject(vuln, "cvssv2"), "score")
                );
                String description = getString(vuln, "description");
                findings.add(new NvdFinding(dependencyName, cve, severity, score, description));
            }
        }

        return findings;
    }

    private static JsonArray getArray(JsonObject obj, String key) {
        if (obj == null) {
            return null;
        }
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        return element.getAsJsonArray();
    }

    private static JsonObject getObject(JsonObject obj, String key) {
        if (obj == null) {
            return null;
        }
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null) {
            return null;
        }
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }

    private static Double getScore(JsonObject obj, String key) {
        if (obj == null) {
            return null;
        }
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsDouble();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Double firstNonNull(Double... values) {
        if (values == null) {
            return null;
        }
        for (Double value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String extractDependencyName(JsonObject dependency) {
        String name = null;
        JsonArray packages = getArray(dependency, "packages");
        if (packages != null && packages.size() > 0) {
            JsonElement pkgElement = packages.get(0);
            if (pkgElement.isJsonObject()) {
                name = getString(pkgElement.getAsJsonObject(), "id");
            }
        }
        if (name == null || name.isBlank()) {
            name = getString(dependency, "fileName");
        }
        if ((name == null || name.isBlank())) {
            String filePath = getString(dependency, "filePath");
            if (filePath != null) {
                name = new File(filePath).getName();
            }
        }
        return normalizeDependency(name);
    }

    private static String normalizeDependency(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.startsWith("pkg:maven/")) {
            trimmed = trimmed.substring("pkg:maven/".length());
            int atIndex = trimmed.indexOf('@');
            if (atIndex > 0) {
                trimmed = trimmed.substring(0, atIndex);
            }
        }
        if (trimmed.contains("@")) {
            trimmed = trimmed.substring(0, trimmed.indexOf('@'));
        }
        if (!trimmed.contains("/") && trimmed.contains(":")) {
            String[] parts = trimmed.split(":");
            if (parts.length >= 2) {
                trimmed = parts[0] + "/" + parts[1];
            }
        }
        return trimmed;
    }
}
