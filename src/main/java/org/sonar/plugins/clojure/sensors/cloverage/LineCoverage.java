package org.sonar.plugins.clojure.sensors.cloverage;

public class LineCoverage {
    private final int lineNumber;
    private final int hits;

    public LineCoverage(int lineNumber, int hits) {
        this.lineNumber = lineNumber;
        this.hits = hits;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getHits() {
        return hits;
    }
}
