package org.sonar.plugins.clojure.sensors.cloverage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.util.Map;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public final class CloverageReportParser {

    private static final Logger LOG = Loggers.get(CloverageReportParser.class);

    private CloverageReportParser() {
    }

    public static CoverageReport parse(SensorContext context, String json) {
        CoverageReport report = new CoverageReport();
        if (json == null || json.isBlank()) {
            return report;
        }

        JsonElement root;
        try {
            root = new JsonParser().parse(json);
        } catch (RuntimeException ex) {
            LOG.warn("Failed to parse Cloverage report JSON", ex);
            return report;
        }

        if (!root.isJsonObject()) {
            return report;
        }

        JsonObject rootObject = root.getAsJsonObject();
        JsonObject coverageObject = rootObject.getAsJsonObject("coverage");
        if (coverageObject == null) {
            return report;
        }

        for (Map.Entry<String, JsonElement> entry : coverageObject.entrySet()) {
            String path = normalizePath(entry.getKey());
            InputFile inputFile = resolveInputFile(context.fileSystem(), path);
            if (inputFile == null) {
                LOG.debug("Cloverage file not found in analysis: {}", path);
                continue;
            }

            JsonElement value = entry.getValue();
            if (!value.isJsonArray()) {
                continue;
            }

            JsonArray coverageArray = value.getAsJsonArray();
            FileCoverage fileCoverage = new FileCoverage(inputFile);

            int lineNumber = 0;
            for (JsonElement lineElement : coverageArray) {
                if (lineNumber > 0 && lineElement != null && !lineElement.isJsonNull()) {
                    int hits = parseHits(lineElement);
                    fileCoverage.addLine(lineNumber, hits);
                }
                lineNumber++;
            }

            if (!fileCoverage.getLines().isEmpty()) {
                report.addFile(fileCoverage);
            }
        }

        return report;
    }

    private static int parseHits(JsonElement element) {
        try {
            return element.getAsInt();
        } catch (RuntimeException ex) {
            return 1;
        }
    }

    private static InputFile resolveInputFile(FileSystem fileSystem, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        File file = new File(path);
        FilePredicate predicate;
        if (file.isAbsolute()) {
            predicate = fileSystem.predicates().hasAbsolutePath(file.getAbsolutePath());
            InputFile inputFile = fileSystem.inputFile(predicate);
            if (inputFile != null) {
                return inputFile;
            }
        } else {
            predicate = fileSystem.predicates().hasRelativePath(path);
            InputFile inputFile = fileSystem.inputFile(predicate);
            if (inputFile != null) {
                return inputFile;
            }
        }

        String normalizedPath = path.replace("\\", "/");
        InputFile byPattern = fileSystem.inputFile(
            fileSystem.predicates().matchesPathPattern("**/" + normalizedPath)
        );
        if (byPattern != null) {
            return byPattern;
        }

        String filename = new File(normalizedPath).getName();
        if (!filename.isBlank()) {
            return fileSystem.inputFile(
                fileSystem.predicates().matchesPathPattern("**/" + filename)
            );
        }

        return null;
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.trim();
        if (normalized.startsWith("./")) {
            return normalized.substring(2);
        }
        if (normalized.startsWith(".\\")) {
            return normalized.substring(2);
        }
        return normalized;
    }
}
