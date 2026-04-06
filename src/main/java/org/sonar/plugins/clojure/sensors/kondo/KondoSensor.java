package org.sonar.plugins.clojure.sensors.kondo;

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
import org.sonar.api.batch.fs.TextRange;
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
import org.sonar.plugins.clojure.settings.KondoProperties;

public class KondoSensor implements Sensor {

    private static final Logger LOG = Loggers.get(KondoSensor.class);

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor.name("clj-kondo").onlyOnLanguage(ClojureLanguage.KEY);
    }

    @Override
    public void execute(SensorContext context) {
        if (!isEnabled(context)) {
            LOG.info("clj-kondo disabled");
            return;
        }

        if (!hasClojureFiles(context.fileSystem())) {
            LOG.debug("No Clojure files found; skipping clj-kondo.");
            return;
        }

        CommandResult result = runKondo(context);
        if (result == null) {
            return;
        }

        if (result.timedOut) {
            LOG.warn("clj-kondo timed out; skipping issue import.");
            return;
        }

        if (result.exitCode != 0) {
            LOG.warn("clj-kondo exited with code {}. stderr:\n{}", result.exitCode, result.stderr);
        }

        String output = result.stdout;
        if ((output == null || output.isBlank()) && result.stderr != null && !result.stderr.isBlank()) {
            LOG.debug("clj-kondo produced no stdout; attempting to parse stderr as JSON.");
            output = result.stderr;
        }

        List<KondoFinding> findings = KondoIssueParser.parse(output);
        LOG.info("clj-kondo findings: {}", findings.size());
        if (findings.isEmpty() && output != null && !output.isBlank()) {
            LOG.debug("clj-kondo output could not be parsed. First 500 chars:\n{}",
                truncate(output, 500));
        }
        for (KondoFinding finding : findings) {
            saveIssue(context, finding);
        }
    }

    private boolean isEnabled(SensorContext context) {
        return context.config().getBoolean(KondoProperties.ENABLED_KEY)
            .orElse(KondoProperties.ENABLED_DEFAULT);
    }

    private boolean hasClojureFiles(FileSystem fileSystem) {
        FilePredicate isClojure = fileSystem.predicates().hasLanguage(ClojureLanguage.KEY);
        return fileSystem.inputFiles(isClojure).iterator().hasNext();
    }

    private CommandResult runKondo(SensorContext context) {
        String path = context.config().get(KondoProperties.PATH_KEY)
            .orElse(KondoProperties.PATH_DEFAULT);
        String lintPaths = context.config().get(KondoProperties.LINT_PATHS_KEY)
            .orElse(KondoProperties.LINT_PATHS_DEFAULT);
        String configPath = context.config().get(KondoProperties.CONFIG_PATH_KEY)
            .orElse(KondoProperties.CONFIG_PATH_DEFAULT);
        String configEdn = context.config().get(KondoProperties.CONFIG_KEY)
            .orElse(KondoProperties.CONFIG_DEFAULT);
        String args = context.config().get(KondoProperties.ARGUMENTS_KEY)
            .orElse(KondoProperties.ARGUMENTS_DEFAULT);
        long timeout = context.config().getLong(KondoProperties.TIMEOUT_KEY)
            .orElse(Long.parseLong(KondoProperties.TIMEOUT_DEFAULT));

        List<String> command = new ArrayList<>();
        command.add(path);
        command.add("--lint");
        command.addAll(splitLintPaths(lintPaths));
        addConfigArgs(command, configPath, configEdn);
        command.addAll(splitArgs(args));

        File baseDir = context.fileSystem().baseDir();
        LOG.info("Running clj-kondo: {} (cwd: {})", String.join(" ", command), baseDir);
        try {
            return execute(command, baseDir, timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("clj-kondo execution interrupted: {}", e.getMessage());
            return null;
        } catch (IOException e) {
            LOG.error("Failed to run clj-kondo: {}", e.getMessage());
            return null;
        }
    }

    private void saveIssue(SensorContext context, KondoFinding finding) {
        if (finding.getFilename() == null || finding.getMessage() == null) {
            return;
        }

        InputFile inputFile = findInputFile(context.fileSystem(), finding.getFilename());
        if (inputFile == null) {
            LOG.debug("clj-kondo file not found in analysis: {}", finding.getFilename());
            return;
        }

        String ruleId = normalizeRuleId(finding.getType(), finding.getMessage());
        NewExternalIssue issue = context.newExternalIssue()
            .engineId("clj-kondo")
            .ruleId(ruleId);
        String message = formatMessage(finding);
        NewIssueLocation location = issue.newLocation().on(inputFile).message(message);

        if (finding.getRow() > 0) {
            if (finding.getEndRow() > 0 && finding.getEndCol() > 0 && finding.getCol() > 0) {
                TextRange range = inputFile.newRange(
                    finding.getRow(),
                    Math.max(0, finding.getCol() - 1),
                    finding.getEndRow(),
                    Math.max(0, finding.getEndCol() - 1)
                );
                location.at(range);
            } else {
                location.at(inputFile.selectLine(finding.getRow()));
            }
        }

        issue.at(location);
        Severity impact = mapImpactSeverity(finding.getLevel());
        if (impact != null) {
            issue.addImpact(SoftwareQuality.MAINTAINABILITY, impact);
        }
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

    private Severity mapImpactSeverity(String level) {
        if (level == null) {
            return null;
        }
        switch (level.toLowerCase(Locale.ROOT)) {
            case "info":
                return Severity.LOW;
            case "warning":
                return Severity.MEDIUM;
            case "error":
                return Severity.HIGH;
            default:
                return null;
        }
    }

    private String formatMessage(KondoFinding finding) {
        if (finding.getType() == null || finding.getType().isBlank()) {
            return finding.getMessage();
        }
        return "[" + finding.getType() + "] " + finding.getMessage();
    }

    private void addConfigArgs(List<String> command, String configPath, String configEdn) {
        if (configPath != null && !configPath.isBlank()) {
            command.add("--config");
            command.add(configPath.trim());
            return;
        }
        if (configEdn != null && !configEdn.isBlank()) {
            command.add("--config");
            command.add(configEdn.trim());
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

    private List<String> splitLintPaths(String lintPaths) {
        if (lintPaths == null || lintPaths.isBlank()) {
            return List.of(".");
        }
        String[] parts = lintPaths.split(",");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result.isEmpty() ? List.of(".") : result;
    }

    private String normalizeRuleId(String type, String message) {
        String base;
        if (type != null && !type.isBlank()) {
            base = type.trim();
        } else if (message != null && !message.isBlank()) {
            String[] words = message.toLowerCase(Locale.ROOT).trim().split("\\s+");
            if (words.length >= 2) {
                base = words[0] + "-" + words[1];
            } else {
                base = words[0];
            }
        } else {
            return "kondo";
        }

        String normalized = base.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
        if (normalized.isEmpty()) {
            return "kondo";
        }
        String prefixed = normalized.startsWith("kondo-") ? normalized : "kondo-" + normalized;
        return prefixed.length() > 64 ? prefixed.substring(0, 64) : prefixed;
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

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
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
