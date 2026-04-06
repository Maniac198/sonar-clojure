package org.sonar.plugins.clojure.sensors.cloverage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CoverageReport {
    private final List<FileCoverage> files = new ArrayList<>();

    public void addFile(FileCoverage file) {
        files.add(file);
    }

    public List<FileCoverage> getFiles() {
        return Collections.unmodifiableList(files);
    }
}
