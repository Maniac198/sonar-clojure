package org.sonar.plugins.clojure.sensors.eastwood;

public class EastwoodFinding {
    private final String filename;
    private final int line;
    private final int column;
    private final String ruleId;
    private final String message;

    public EastwoodFinding(String filename, int line, int column, String ruleId, String message) {
        this.filename = filename;
        this.line = line;
        this.column = column;
        this.ruleId = ruleId;
        this.message = message;
    }

    public String getFilename() {
        return filename;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getMessage() {
        return message;
    }
}
