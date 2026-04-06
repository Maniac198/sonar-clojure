package org.sonar.plugins.clojure.sensors.metrics;

import java.io.IOException;
import java.util.List;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.plugins.clojure.language.ClojureLanguage;

public class ClojureMetricsSensor implements Sensor {

    private static final Logger LOG = Loggers.get(ClojureMetricsSensor.class);

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor.name("clojure-metrics").onlyOnLanguage(ClojureLanguage.KEY);
    }

    @Override
    public void execute(SensorContext context) {
        FileSystem fileSystem = context.fileSystem();
        FilePredicate predicate = fileSystem.predicates().and(
            fileSystem.predicates().hasLanguage(ClojureLanguage.KEY),
            fileSystem.predicates().hasType(InputFile.Type.MAIN)
        );

        int analyzedFiles = 0;
        for (InputFile inputFile : fileSystem.inputFiles(predicate)) {
            try {
                FileMetrics metrics = computeMetrics(inputFile);
                saveMeasures(context, inputFile, metrics);
                analyzedFiles++;
            } catch (IOException e) {
                LOG.debug("Failed to compute metrics for {}", inputFile.filename(), e);
            }
        }
        LOG.info("clojure-metrics analyzed files: {}", analyzedFiles);
    }

    private FileMetrics computeMetrics(InputFile inputFile) throws IOException {
        List<String> lines = inputFile.contents().lines().toList();
        int ncloc = 0;
        int commentLines = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.startsWith(";")) {
                commentLines++;
                continue;
            }

            ncloc++;
        }

        return new FileMetrics(ncloc, commentLines);
    }

    private void saveMeasures(SensorContext context, InputFile inputFile, FileMetrics metrics) {
        context.<Integer>newMeasure()
            .on(inputFile)
            .forMetric(CoreMetrics.NCLOC)
            .withValue(metrics.ncloc)
            .save();

        context.<Integer>newMeasure()
            .on(inputFile)
            .forMetric(CoreMetrics.COMMENT_LINES)
            .withValue(metrics.commentLines)
            .save();
    }

    private static final class FileMetrics {
        private final int ncloc;
        private final int commentLines;

        private FileMetrics(int ncloc, int commentLines) {
            this.ncloc = ncloc;
            this.commentLines = commentLines;
        }
    }
}
