package org.sonar.plugins.clojure.sensors.kibit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import org.sonar.api.batch.sensor.issue.NewExternalIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.issue.impact.SoftwareQuality;
import org.sonar.api.issue.impact.Severity;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.plugins.clojure.language.ClojureLanguage;
import org.sonar.plugins.clojure.settings.KibitProperties;

public class KibitSensor implements Sensor {

    private static final Logger LOG = Loggers.get(KibitSensor.class);

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor.name("kibit").onlyOnLanguage(ClojureLanguage.KEY);
    }

    @Override
    public void execute(SensorContext context) {
        if (!isEnabled(context)) {
            LOG.info("kibit disabled");
            return;
        }

        if (!hasClojureFiles(context.fileSystem())) {
            LOG.debug("No Clojure files found; skipping kibit.");
            return;
        }

        CommandResult result = runKibit(context);
        if (result == null) {
            return;
        }

        if (result.timedOut) {
            LOG.warn("kibit timed out; skipping issue import.");
            return;
        }

        if (result.exitCode != 0) {
            LOG.warn("kibit exited with code {}. stderr:\n{}", result.exitCode, result.stderr);
        }

        String output = result.stdout;
        if ((output == null || output.isBlank()) && result.stderr != null && !result.stderr.isBlank()) {
            output = result.stderr;
        }

        List<KibitFinding> findings = KibitIssueParser.parse(output);
        LOG.info("kibit findings: {}", findings.size());
        for (KibitFinding finding : findings) {
            saveIssue(context, finding);
        }
    }

    private boolean isEnabled(SensorContext context) {
        return context.config().getBoolean(KibitProperties.ENABLED_KEY)
            .orElse(KibitProperties.ENABLED_DEFAULT);
    }

    private boolean hasClojureFiles(FileSystem fileSystem) {
        FilePredicate isClojure = fileSystem.predicates().hasLanguage(ClojureLanguage.KEY);
        return fileSystem.inputFiles(isClojure).iterator().hasNext();
    }

    private CommandResult runKibit(SensorContext context) {
        String commandConfig = context.config().get(KibitProperties.COMMAND_KEY)
            .orElse(KibitProperties.COMMAND_DEFAULT);
        String paths = context.config().get(KibitProperties.PATHS_KEY)
            .orElse(KibitProperties.PATHS_DEFAULT);
        String args = context.config().get(KibitProperties.ARGUMENTS_KEY)
            .orElse(KibitProperties.ARGUMENTS_DEFAULT);
        long timeout = context.config().getLong(KibitProperties.TIMEOUT_KEY)
            .orElse(Long.parseLong(KibitProperties.TIMEOUT_DEFAULT));

        List<String> command = new ArrayList<>(splitArgs(commandConfig));
        if (command.isEmpty()) {
            command.addAll(splitArgs(KibitProperties.COMMAND_DEFAULT));
        }
        command.addAll(splitPaths(paths));
        command.addAll(splitArgs(args));

        File baseDir = context.fileSystem().baseDir();
        LOG.info("Running kibit: {} (cwd: {})", String.join(" ", command), baseDir);
        try {
            return execute(command, baseDir, timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("kibit execution interrupted: {}", e.getMessage());
            return null;
        } catch (IOException e) {
            LOG.error("Failed to run kibit: {}", e.getMessage());
            return null;
        }
    }

    private void saveIssue(SensorContext context, KibitFinding finding) {
        if (finding.getFilename() == null || finding.getMessage() == null) {
            return;
        }

        InputFile inputFile = findInputFile(context.fileSystem(), finding.getFilename());
        if (inputFile == null) {
            LOG.debug("kibit file not found in analysis: {}", finding.getFilename());
            return;
        }

        String ruleId = normalizeRuleId(finding.getRuleId(), finding.getMessage());
        NewExternalIssue issue = context.newExternalIssue()
            .engineId("kibit")
            .ruleId(ruleId);
        NewIssueLocation location = issue.newLocation()
            .on(inputFile)
            .message(finding.getMessage());

        if (finding.getLine() > 0) {
            location.at(inputFile.selectLine(finding.getLine()));
        }

        issue.at(location);
        issue.addImpact(SoftwareQuality.MAINTAINABILITY, Severity.MEDIUM);
        issue.save();
    }

    private InputFile findInputFile(FileSystem fileSystem, String filename) {
        FilePredicate predicate;
        if (new File(filename).isAbsolute()) {
            predicate = fileSystem.predicates().hasAbsolutePath(filename);
        } else {
            predicate = fileSystem.predicates().hasRelativePath(filename);
        }
        return fileSystem.inputFile(predicate);
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

    private List<String> splitPaths(String paths) {
        if (paths == null || paths.isBlank()) {
            return List.of();
        }
        String[] parts = paths.split(",");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String normalizeRuleId(String ruleId, String message) {
        if (ruleId != null && !ruleId.isBlank()) {
            return ruleId;
        }
        if (message == null || message.isBlank()) {
            return "kibit";
        }
        String[] words = message.toLowerCase(Locale.ROOT).trim().split("\\s+");
        String base = words.length >= 2 ? words[0] + "-" + words[1] : words[0];
        String normalized = base.replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
        return normalized.isEmpty() ? "kibit" : "kibit-" + normalized;
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
