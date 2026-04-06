package org.sonar.plugins.clojure.sensors.ancient;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
import org.sonar.plugins.clojure.settings.AncientProperties;

public class AncientSensor implements Sensor {

    private static final Logger LOG = Loggers.get(AncientSensor.class);
    private static final String PROJECT_FILE = "project.clj";

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor.name("lein-ancient").onlyOnLanguage(ClojureLanguage.KEY);
    }

    @Override
    public void execute(SensorContext context) {
        if (!isEnabled(context)) {
            LOG.info("lein-ancient disabled");
            return;
        }

        CommandResult result = runAncient(context);
        if (result == null) {
            return;
        }

        if (result.timedOut) {
            LOG.warn("lein-ancient timed out; skipping issue import.");
            return;
        }

        if (result.exitCode != 0) {
            LOG.warn("lein-ancient exited with code {}. stderr:\n{}", result.exitCode, result.stderr);
        }

        String output = result.stdout;
        if ((output == null || output.isBlank()) && result.stderr != null && !result.stderr.isBlank()) {
            output = result.stderr;
        }

        List<AncientDependency> dependencies = AncientOutputParser.parse(output);
        LOG.info("lein-ancient findings: {}", dependencies.size());
        if (dependencies.isEmpty()) {
            return;
        }

        InputFile projectFile = context.fileSystem()
            .inputFile(context.fileSystem().predicates().hasRelativePath(PROJECT_FILE));
        String projectContents = null;
        if (projectFile != null) {
            try {
                projectContents = projectFile.contents();
            } catch (IOException e) {
                LOG.debug("Unable to read project.clj", e);
            }
        }

        for (AncientDependency dependency : dependencies) {
            saveIssue(context, dependency, projectFile, projectContents);
        }
    }

    private boolean isEnabled(SensorContext context) {
        return context.config().getBoolean(AncientProperties.ENABLED_KEY)
            .orElse(AncientProperties.ENABLED_DEFAULT);
    }

    private CommandResult runAncient(SensorContext context) {
        String commandConfig = context.config().get(AncientProperties.COMMAND_KEY)
            .orElse(AncientProperties.COMMAND_DEFAULT);
        String args = context.config().get(AncientProperties.ARGUMENTS_KEY)
            .orElse(AncientProperties.ARGUMENTS_DEFAULT);
        long timeout = context.config().getLong(AncientProperties.TIMEOUT_KEY)
            .orElse(Long.parseLong(AncientProperties.TIMEOUT_DEFAULT));

        List<String> command = new ArrayList<>(splitArgs(commandConfig));
        if (command.isEmpty()) {
            command.addAll(splitArgs(AncientProperties.COMMAND_DEFAULT));
        }
        command.addAll(splitArgs(args));

        File baseDir = context.fileSystem().baseDir();
        LOG.info("Running lein-ancient: {} (cwd: {})", String.join(" ", command), baseDir);
        try {
            return execute(command, baseDir, timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("lein-ancient execution interrupted: {}", e.getMessage());
            return null;
        } catch (IOException e) {
            LOG.error("Failed to run lein-ancient: {}", e.getMessage());
            return null;
        }
    }

    private void saveIssue(SensorContext context, AncientDependency dependency,
                           InputFile projectFile, String projectContents) {
        if (dependency.getName() == null) {
            return;
        }

        NewExternalIssue issue = context.newExternalIssue()
            .engineId("lein-ancient")
            .ruleId(normalizeRuleId(dependency.getName()));
        String message = dependency.toMessage();

        if (projectFile != null) {
            NewIssueLocation location = issue.newLocation().on(projectFile).message(message);
            int lineNumber = findLine(projectContents, dependency);
            if (lineNumber > 0) {
                location.at(projectFile.selectLine(lineNumber));
            }
            issue.at(location);
        } else {
            issue.at(issue.newLocation().message(message));
        }

        issue.addImpact(SoftwareQuality.RELIABILITY, Severity.MEDIUM);
        issue.save();
    }

    private int findLine(String contents, AncientDependency dependency) {
        if (contents == null || contents.isBlank()) {
            return -1;
        }
        String needle = dependency.getName();
        String version = dependency.getCurrentVersion();
        String[] lines = contents.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains(needle) && (version == null || line.contains(version))) {
                return i + 1;
            }
        }
        return -1;
    }

    private String normalizeRuleId(String name) {
        String normalized = name.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
        if (normalized.isEmpty()) {
            return "ancient";
        }
        String prefixed = normalized.startsWith("ancient-") ? normalized : "ancient-" + normalized;
        return prefixed.length() > 64 ? prefixed.substring(0, 64) : prefixed;
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
