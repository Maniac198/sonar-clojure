package org.sonar.plugins.clojure.sensors.kibit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KibitIssueParser {

    private static final Pattern START_PATTERN =
        Pattern.compile("^At\\s+(.+?):(\\d+):\\s*$");

    private KibitIssueParser() {
    }

    public static List<KibitFinding> parse(String output) {
        if (output == null || output.isBlank()) {
            return Collections.emptyList();
        }

        List<KibitFinding> findings = new ArrayList<>();
        String[] lines = output.split("\\r?\\n");

        String currentFile = null;
        int currentLine = -1;
        List<String> block = new ArrayList<>();

        for (String line : lines) {
            Matcher matcher = START_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                flush(findings, currentFile, currentLine, block);
                currentFile = normalizeFilename(matcher.group(1));
                currentLine = parseInt(matcher.group(2));
                block.clear();
                continue;
            }

            if (currentFile != null) {
                block.add(line);
            }
        }

        flush(findings, currentFile, currentLine, block);
        return findings;
    }

    private static void flush(List<KibitFinding> findings, String file, int line, List<String> block) {
        if (file == null) {
            return;
        }

        String message = buildMessage(block);
        String ruleId = deriveRuleId(block, message);
        findings.add(new KibitFinding(file, line, message, ruleId));
    }

    private static String buildMessage(List<String> block) {
        if (block.isEmpty()) {
            return "Kibit suggestion";
        }

        int start = 0;
        int end = block.size();
        while (start < end && block.get(start).trim().isEmpty()) {
            start++;
        }
        while (end > start && block.get(end - 1).trim().isEmpty()) {
            end--;
        }
        if (start >= end) {
            return "Kibit suggestion";
        }
        return String.join("\n", block.subList(start, end)).trim();
    }

    private static String deriveRuleId(List<String> block, String message) {
        for (int i = 0; i < block.size(); i++) {
            String line = block.get(i).trim();
            if (line.equalsIgnoreCase("Consider using:")) {
                String suggestion = nextNonEmpty(block, i + 1);
                if (suggestion != null) {
                    String token = extractSuggestionToken(suggestion);
                    if (token != null) {
                        return "kibit-" + token;
                    }
                    return "kibit-" + normalizeToken(suggestion);
                }
            }
        }
        return "kibit-" + normalizeToken(message);
    }

    private static String nextNonEmpty(List<String> block, int start) {
        for (int i = start; i < block.size(); i++) {
            String value = block.get(i).trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static String extractSuggestionToken(String suggestion) {
        String trimmed = suggestion.trim();
        if (trimmed.startsWith("(")) {
            trimmed = trimmed.substring(1).trim();
        }
        int end = 0;
        while (end < trimmed.length()) {
            char c = trimmed.charAt(end);
            if (Character.isWhitespace(c) || c == ')') {
                break;
            }
            end++;
        }
        if (end == 0) {
            return null;
        }
        String token = trimmed.substring(0, end);
        if ("->".equals(token)) {
            return "thread-first";
        }
        if ("->>".equals(token)) {
            return "thread-last";
        }
        return normalizeToken(token);
    }

    private static String normalizeToken(String value) {
        if (value == null || value.isBlank()) {
            return "kibit";
        }
        String[] words = value.toLowerCase().trim().split("\\s+");
        String base = words.length >= 2 ? words[0] + "-" + words[1] : words[0];
        String normalized = base.replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
        return normalized.isEmpty() ? "kibit" : normalized;
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
