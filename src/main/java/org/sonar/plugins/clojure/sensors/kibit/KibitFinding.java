package org.sonar.plugins.clojure.sensors.kibit;

public class KibitFinding {
    private final String filename;
    private final int line;
    private final String message;
    private final String ruleId;

    public KibitFinding(String filename, int line, String message, String ruleId) {
        this.filename = filename;
        this.line = line;
        this.message = message;
        this.ruleId = ruleId;
    }

    public String getFilename() {
        return filename;
    }

    public int getLine() {
        return line;
    }

    public String getMessage() {
        return message;
    }

    public String getRuleId() {
        return ruleId;
    }
}
