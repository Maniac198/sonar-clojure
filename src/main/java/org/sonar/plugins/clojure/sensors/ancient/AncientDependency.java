package org.sonar.plugins.clojure.sensors.ancient;

public class AncientDependency {
    private final String name;
    private final String currentVersion;
    private final String availableVersion;

    public AncientDependency(String name, String currentVersion, String availableVersion) {
        this.name = name;
        this.currentVersion = currentVersion;
        this.availableVersion = availableVersion;
    }

    public String getName() {
        return name;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public String getAvailableVersion() {
        return availableVersion;
    }

    public String toMessage() {
        return name + " is using version " + currentVersion + " but " + availableVersion + " is available.";
    }
}
