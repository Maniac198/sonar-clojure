package org.sonar.plugins.clojure.sensors.nvd;

public class NvdFinding {

    private final String dependency;
    private final String cve;
    private final String severity;
    private final Double score;
    private final String description;

    public NvdFinding(String dependency, String cve, String severity, Double score, String description) {
        this.dependency = dependency;
        this.cve = cve;
        this.severity = severity;
        this.score = score;
        this.description = description;
    }

    public String getDependency() {
        return dependency;
    }

    public String getCve() {
        return cve;
    }

    public String getSeverity() {
        return severity;
    }

    public Double getScore() {
        return score;
    }

    public String getDescription() {
        return description;
    }
}
