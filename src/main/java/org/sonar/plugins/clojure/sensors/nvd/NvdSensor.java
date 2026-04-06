package org.sonar.plugins.clojure.sensors.nvd;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.sonar.plugins.clojure.settings.NvdProperties;

public class NvdSensor implements Sensor {

    private static final Logger LOG = Loggers.get(NvdSensor.class);
    private static final String PROJECT_FILE = "project.clj";
    private static final String DEPS_FILE = "deps.edn";

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor.name("nvd-clojure").onlyOnLanguage(ClojureLanguage.KEY);
    }

    @Override
    public void execute(SensorContext context) {
        if (!isEnabled(context)) {
            LOG.info("nvd-clojure disabled");
            return;
        }

        boolean run = context.config().getBoolean(NvdProperties.RUN_KEY)
            .orElse(NvdProperties.RUN_DEFAULT);
        if (run) {
            CommandResult result = runNvd(context);
            if (result == null) {
                return;
            }
            if (result.timedOut) {
                LOG.warn("nvd-clojure timed out; skipping issue import.");
                return;
            }
            if (result.exitCode != 0) {
                LOG.warn("nvd-clojure exited with code {}. stderr:\n{}",
                    result.exitCode, result.stderr);
            }
        }

        String reportPath = context.config().get(NvdProperties.REPORT_PATH_KEY)
            .orElse(NvdProperties.REPORT_PATH_DEFAULT);
        File reportFile = resolveReportFile(context.fileSystem().baseDir(), reportPath);
        if (reportFile == null || !reportFile.exists()) {
            LOG.warn("NVD report not found at {}", reportFile == null ? reportPath : reportFile);
            return;
        }

        String json;
        try {
            json = Files.readString(reportFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("Failed to read NVD report at {}: {}", reportFile, e.getMessage());
            return;
        }

        List<NvdFinding> findings = NvdReportParser.parse(json);
        LOG.info("nvd-clojure findings: {}", findings.size());
        if (findings.isEmpty()) {
            return;
        }

        InputFile projectFile = findProjectFile(context.fileSystem());
        String projectContents = null;
        if (projectFile != null) {
            try {
                projectContents = projectFile.contents();
            } catch (IOException e) {
                LOG.debug("Unable to read project file", e);
            }
        }

        for (NvdFinding finding : findings) {
            saveIssue(context, finding, projectFile, projectContents);
        }
    }

    private boolean isEnabled(SensorContext context) {
        return context.config().getBoolean(NvdProperties.ENABLED_KEY)
            .orElse(NvdProperties.ENABLED_DEFAULT);
    }

    private File resolveReportFile(File baseDir, String reportPath) {
        if (reportPath == null || reportPath.isBlank()) {
            return null;
        }
        File reportFile = new File(reportPath);
        if (reportFile.isAbsolute()) {
            return reportFile;
        }
        return new File(baseDir, reportPath);
    }

    private CommandResult runNvd(SensorContext context) {
        String commandConfig = context.config().get(NvdProperties.COMMAND_KEY)
            .orElse(NvdProperties.COMMAND_DEFAULT);
        String args = context.config().get(NvdProperties.ARGUMENTS_KEY)
            .orElse(NvdProperties.ARGUMENTS_DEFAULT);
        long timeout = context.config().getLong(NvdProperties.TIMEOUT_KEY)
            .orElse(Long.parseLong(NvdProperties.TIMEOUT_DEFAULT));

        List<String> command = new ArrayList<>(splitArgs(commandConfig));
        if (command.isEmpty()) {
            command.addAll(splitArgs(NvdProperties.COMMAND_DEFAULT));
        }
        command.addAll(splitArgs(args));

        File baseDir = context.fileSystem().baseDir();
        LOG.info("Running nvd-clojure: {} (cwd: {})", String.join(" ", command), baseDir);
        try {
            return execute(command, baseDir, timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("nvd-clojure execution interrupted: {}", e.getMessage());
            return null;
        } catch (IOException e) {
            LOG.error("Failed to run nvd-clojure: {}", e.getMessage());
            return null;
        }
    }

    private void saveIssue(SensorContext context, NvdFinding finding,
                           InputFile projectFile, String projectContents) {
        String message = buildMessage(finding);
        if (message == null || message.isBlank()) {
            return;
        }

        String ruleId = normalizeRuleId(finding.getCve());
        NewExternalIssue issue = context.newExternalIssue()
            .engineId("nvd-clojure")
            .ruleId(ruleId);

        NewIssueLocation location = issue.newLocation().message(message);
        if (projectFile != null) {
            location.on(projectFile);
            int lineNumber = findLine(projectContents, finding);
            if (lineNumber > 0) {
                location.at(projectFile.selectLine(lineNumber));
            }
        }

        issue.at(location);
        Severity impact = mapImpactSeverity(finding);
        if (impact != null) {
            issue.addImpact(SoftwareQuality.SECURITY, impact);
        }
        issue.save();
    }

    private InputFile findProjectFile(FileSystem fileSystem) {
        InputFile projectFile = fileSystem.inputFile(
            fileSystem.predicates().hasRelativePath(PROJECT_FILE));
        if (projectFile != null) {
            return projectFile;
        }
        return fileSystem.inputFile(fileSystem.predicates().hasRelativePath(DEPS_FILE));
    }

    private int findLine(String contents, NvdFinding finding) {
        if (contents == null || contents.isBlank()) {
            return -1;
        }
        String dependency = finding.getDependency();
        if (dependency == null || dependency.isBlank()) {
            return -1;
        }

        List<String> candidates = new ArrayList<>();
        candidates.add(dependency);
        if (dependency.contains("/")) {
            String[] parts = dependency.split("/");
            if (parts.length == 2) {
                candidates.add(parts[0] + ":" + parts[1]);
                candidates.add(parts[1]);
            }
        }

        String[] lines = contents.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            for (String candidate : candidates) {
                if (candidate != null && !candidate.isBlank() && line.contains(candidate)) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    private String buildMessage(NvdFinding finding) {
        String dependency = finding.getDependency();
        String cve = finding.getCve();
        String severity = normalizeSeverity(finding.getSeverity());
        Double score = finding.getScore();
        String description = finding.getDescription();

        StringBuilder message = new StringBuilder();
        if (dependency != null && !dependency.isBlank()) {
            message.append(dependency).append(" ");
        }

        if (cve != null && !cve.isBlank()) {
            message.append("is affected by ").append(cve);
        } else {
            message.append("has a known vulnerability");
        }

        List<String> details = new ArrayList<>();
        if (severity != null) {
            details.add(severity);
        }
        if (score != null) {
            details.add(String.format(Locale.ROOT, "CVSS %.1f", score));
        }
        if (!details.isEmpty()) {
            message.append(" (").append(String.join(", ", details)).append(")");
        }

        if (description != null && !description.isBlank()) {
            message.append(". ").append(description.trim());
        }

        return message.toString();
    }

    private Severity mapImpactSeverity(NvdFinding finding) {
        String severity = normalizeSeverity(finding.getSeverity());
        if (severity != null) {
            switch (severity) {
                case "CRITICAL":
                case "HIGH":
                    return Severity.HIGH;
                case "MEDIUM":
                    return Severity.MEDIUM;
                case "LOW":
                case "INFO":
                    return Severity.LOW;
                default:
                    break;
            }
        }

        Double score = finding.getScore();
        if (score != null) {
            if (score >= 7.0) {
                return Severity.HIGH;
            }
            if (score >= 4.0) {
                return Severity.MEDIUM;
            }
            return Severity.LOW;
        }

        return Severity.MEDIUM;
    }

    private String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return null;
        }
        return severity.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRuleId(String cve) {
        String normalized;
        if (cve == null || cve.isBlank()) {
            normalized = "nvd";
        } else {
            normalized = cve.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
            if (!normalized.startsWith("nvd-")) {
                normalized = "nvd-" + normalized;
            }
        }
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
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
