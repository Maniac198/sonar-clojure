package org.sonar.plugins.clojure.sensors.cloverage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.sonar.api.batch.fs.InputFile;

public class FileCoverage {
    private final InputFile file;
    private final List<LineCoverage> lines = new ArrayList<>();

    public FileCoverage(InputFile file) {
        this.file = file;
    }

    public InputFile getFile() {
        return file;
    }

    public void addLine(int lineNumber, int hits) {
        lines.add(new LineCoverage(lineNumber, hits));
    }

    public List<LineCoverage> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
