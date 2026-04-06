package org.sonar.plugins.clojure.sensors.kondo;

public class KondoFinding {
    private final String filename;
    private final int row;
    private final int col;
    private final int endRow;
    private final int endCol;
    private final String level;
    private final String message;
    private final String type;

    public KondoFinding(String filename, int row, int col, int endRow, int endCol,
                        String level, String message, String type) {
        this.filename = filename;
        this.row = row;
        this.col = col;
        this.endRow = endRow;
        this.endCol = endCol;
        this.level = level;
        this.message = message;
        this.type = type;
    }

    public String getFilename() {
        return filename;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public int getEndRow() {
        return endRow;
    }

    public int getEndCol() {
        return endCol;
    }

    public String getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public String getType() {
        return type;
    }
}
