package org.sonar.plugins.clojure.sensors.cloverage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.coverage.NewCoverage;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.plugins.clojure.language.ClojureLanguage;
import org.sonar.plugins.clojure.settings.CloverageProperties;

public class CloverageSensor implements Sensor {

    private static final Logger LOG = Loggers.get(CloverageSensor.class);

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor.name("cloverage").onlyOnLanguage(ClojureLanguage.KEY);
    }

    @Override
    public void execute(SensorContext context) {
        if (!isEnabled(context)) {
            LOG.info("cloverage disabled");
            return;
        }

        if (!hasClojureFiles(context.fileSystem())) {
            LOG.debug("No Clojure files found; skipping cloverage.");
            return;
        }

        if (shouldRun(context)) {
            runCloverage(context);
        }

        Path reportPath = resolveReportPath(context);
        if (reportPath == null || !Files.exists(reportPath)) {
            LOG.warn("Cloverage report not found at {}", reportPath);
            return;
        }

        try {
            String json = Files.readString(reportPath, StandardCharsets.UTF_8);
            CoverageReport report = CloverageReportParser.parse(context, json);
            saveCoverage(report, context);
        } catch (IOException ex) {
            LOG.warn("Failed to read Cloverage report at {}", reportPath, ex);
        }
    }

    private boolean isEnabled(SensorContext context) {
        return context.config().getBoolean(CloverageProperties.ENABLED_KEY)
            .orElse(CloverageProperties.ENABLED_DEFAULT);
    }

    private boolean shouldRun(SensorContext context) {
        return context.config().getBoolean(CloverageProperties.RUN_KEY)
            .orElse(CloverageProperties.RUN_DEFAULT);
    }

    private boolean hasClojureFiles(FileSystem fileSystem) {
        FilePredicate isClojure = fileSystem.predicates().hasLanguage(ClojureLanguage.KEY);
        return fileSystem.inputFiles(isClojure).iterator().hasNext();
    }

    private void runCloverage(SensorContext context) {
        String commandConfig = context.config().get(CloverageProperties.COMMAND_KEY)
            .orElse(CloverageProperties.COMMAND_DEFAULT);
        String args = context.config().get(CloverageProperties.ARGUMENTS_KEY)
            .orElse(CloverageProperties.ARGUMENTS_DEFAULT);
        long timeout = context.config().getLong(CloverageProperties.TIMEOUT_KEY)
            .orElse(Long.parseLong(CloverageProperties.TIMEOUT_DEFAULT));

        List<String> command = new ArrayList<>(splitArgs(commandConfig));
        if (command.isEmpty()) {
            command.addAll(splitArgs(CloverageProperties.COMMAND_DEFAULT));
        }
        command.addAll(splitArgs(args));

        File baseDir = context.fileSystem().baseDir();
        LOG.info("Running cloverage: {} (cwd: {})", String.join(" ", command), baseDir);
        try {
            CommandResult result = execute(command, baseDir, timeout);
            if (result.exitCode != 0) {
                LOG.warn("cloverage exited with code {}. stderr:\n{}", result.exitCode, result.stderr);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("cloverage execution interrupted: {}", e.getMessage());
        } catch (IOException e) {
            LOG.error("Failed to run cloverage: {}", e.getMessage());
        }
    }

    private Path resolveReportPath(SensorContext context) {
        String reportPath = context.config().get(CloverageProperties.REPORT_PATH_KEY)
            .orElse(CloverageProperties.REPORT_PATH_DEFAULT);
        if (reportPath == null || reportPath.isBlank()) {
            return null;
        }
        Path path = Path.of(reportPath);
        if (path.isAbsolute()) {
            return path;
        }
        return context.fileSystem().baseDir().toPath().resolve(path);
    }

    private void saveCoverage(CoverageReport report, SensorContext context) {
        for (FileCoverage fileCoverage : report.getFiles()) {
            NewCoverage coverage = context.newCoverage().onFile(fileCoverage.getFile());
            for (LineCoverage line : fileCoverage.getLines()) {
                coverage.lineHits(line.getLineNumber(), line.getHits());
            }
            coverage.save();
        }
    }

    private List<String> splitArgs(String args) {
        if (args == null || args.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;

        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            if (Character.isWhitespace(c) && !inSingle && !inDouble) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }

        if (current.length() > 0) {
            result.add(current.toString());
        }

        return result;
    }

    private CommandResult execute(List<String> command, File workingDir, long timeoutSeconds)
        throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir);
        Process process = builder.start();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<String> stdoutFuture = executor.submit(() -> readAll(process.getInputStream()));
        Future<String> stderrFuture = executor.submit(() -> readAll(process.getErrorStream()));

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }

        String stdout = getFuture(stdoutFuture, timeoutSeconds);
        String stderr = getFuture(stderrFuture, timeoutSeconds);
        executor.shutdownNow();

        int exitCode = finished ? process.exitValue() : -1;
        return new CommandResult(exitCode, stdout, stderr, !finished);
    }

    private String readAll(InputStream inputStream) throws IOException {
        try (InputStream in = inputStream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String getFuture(Future<String> future, long timeoutSeconds) {
        try {
            long waitSeconds = Math.min(30, Math.max(1, timeoutSeconds));
            return future.get(waitSeconds, TimeUnit.SECONDS);
        } catch (Exception ex) {
            future.cancel(true);
            return "";
        }
    }

    private static final class CommandResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        private final boolean timedOut;

        private CommandResult(int exitCode, String stdout, String stderr, boolean timedOut) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.timedOut = timedOut;
        }
    }
}
