# SonarClojure
> A SonarQube plugin to analyze Clojure source.

[![Build Status](https://travis-ci.org/fsantiag/sonar-clojure.svg?branch=master)](https://travis-ci.org/fsantiag/sonar-clojure)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=org.sonar.plugins.clojure%3Asonar-clojure-plugin&metric=alert_status
)](https://sonarcloud.io/dashboard?id=org.sonar.plugins.clojure%3Asonar-clojure-plugin)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=org.sonar.plugins.clojure%3Asonar-clojure-plugin&metric=coverage
)](https://sonarcloud.io/dashboard?id=org.sonar.plugins.clojure%3Asonar-clojure-plugin)

## Current State

### Features:
* Static code analysis powered by [clj-kondo](https://github.com/clj-kondo/clj-kondo), [Kibit](https://github.com/jonase/kibit), and [Eastwood](https://github.com/jonase/eastwood).
* Coverage import powered by [Cloverage](https://github.com/cloverage/cloverage).
* Outdated dependency detection powered by [lein-ancient](https://github.com/xsc/lein-ancient).
* Vulnerability scanning powered by [nvd-clojure](https://github.com/rm-hull/nvd-clojure) (lein-nvd).

## Installation
In order to install SonarClojure:
1. Download the [latest](https://github.com/fsantiag/sonar-clojure/releases) jar of the plugin.
2. Place the jar in the SonarQube server plugins directory, usually located under: `/opt/sonarqube/extensions/plugins/`
3. Restart the SonarQube server.

## Usage
1. Ensure `clj-kondo` is installed and available on your `PATH`, or configure its path via
`sonar.clojure.kondo.path` (see below).

2. Create a ***sonar-project.properties*** file in the root folder of your app:

    ```properties
    sonar.projectKey=your-project-key
    sonar.projectName=YourProjectName
    sonar.projectVersion=1.0
    sonar.sources=.
    ```

3. Run [sonar-scanner](https://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner) on your project.

### Configuring clj-kondo

The sensor runs clj-kondo during analysis. You can configure:

* `sonar.clojure.kondo.enabled` (default: `true`)
* `sonar.clojure.kondo.path` (default: `clj-kondo`)
* `sonar.clojure.kondo.lintPaths` (default: `.`)
* `sonar.clojure.kondo.config` (default: `{:output {:format :json}}`)
* `sonar.clojure.kondo.configPath` (default: empty)
* `sonar.clojure.kondo.arguments` (default: empty)
* `sonar.clojure.kondo.timeout` (default: `300`, seconds)

By default the sensor forces JSON output via the `:output {:format :json}` config so findings can be
mapped to clj-kondo rule types. If you provide `sonar.clojure.kondo.configPath`, that file is used
instead of the inline EDN config.

### Configuring Kibit

The sensor runs Kibit during analysis. You can configure:

* `sonar.clojure.kibit.enabled` (default: `true`)
* `sonar.clojure.kibit.command` (default: `lein kibit`)
* `sonar.clojure.kibit.paths` (default: empty)
* `sonar.clojure.kibit.arguments` (default: empty)
* `sonar.clojure.kibit.timeout` (default: `300`, seconds)

If you use the default `lein kibit` command, make sure the `lein-kibit` plugin is configured
in your `project.clj`.

### Configuring Eastwood

The sensor runs Eastwood during analysis. You can configure:

* `sonar.clojure.eastwood.enabled` (default: `true`)
* `sonar.clojure.eastwood.command` (default: `lein eastwood`)
* `sonar.clojure.eastwood.options` (default: empty)
* `sonar.clojure.eastwood.arguments` (default: empty)
* `sonar.clojure.eastwood.timeout` (default: `300`, seconds)

If you use the default `lein eastwood` command, make sure the `eastwood` plugin is configured
in your `project.clj`.

### Configuring Cloverage

The sensor imports coverage from Cloverage's `codecov.json`. You can configure:

* `sonar.clojure.cloverage.enabled` (default: `true`)
* `sonar.clojure.cloverage.run` (default: `false`)
* `sonar.clojure.cloverage.command` (default: `lein cloverage --codecov`)
* `sonar.clojure.cloverage.arguments` (default: empty)
* `sonar.clojure.cloverage.reportPath` (default: `target/coverage/codecov.json`)
* `sonar.clojure.cloverage.timeout` (default: `300`, seconds)

If you set `sonar.clojure.cloverage.run=true`, make sure the `lein-cloverage` plugin is configured
in your `project.clj`.

### Configuring Lein Ancient

The sensor runs `lein ancient` and reports outdated dependencies. You can configure:

* `sonar.clojure.ancient.enabled` (default: `true`)
* `sonar.clojure.ancient.command` (default: `lein ancient`)
* `sonar.clojure.ancient.arguments` (default: empty)
* `sonar.clojure.ancient.timeout` (default: `300`, seconds)

Make sure the `lein-ancient` plugin is configured in your `project.clj`.

### Configuring NVD (lein-nvd)

The sensor imports vulnerabilities from the JSON report produced by `lein nvd`. You can configure:

* `sonar.clojure.nvd.enabled` (default: `true`)
* `sonar.clojure.nvd.run` (default: `false`)
* `sonar.clojure.nvd.command` (default: `lein nvd check`)
* `sonar.clojure.nvd.arguments` (default: empty)
* `sonar.clojure.nvd.reportPath` (default: `target/nvd/dependency-check-report.json`)
* `sonar.clojure.nvd.timeout` (default: `300`, seconds)

If you set `sonar.clojure.nvd.run=true`, make sure the `lein-nvd` plugin is configured in your
`project.clj` and that it is producing a JSON report at the configured `reportPath`.

#### Debugging
* SonarClojure is in its early days and therefore you might face problems when trying to run the plugin, especially because
 we rely on other plugins that are also in its early days. A nice way to try to debug
a problem you might have is to make sure the particular plugin you are using is running fine before executing the 
sonar-scanner. For instance, if you are trying to visualize the coverage data on SonarQube, make sure to run cloverage
against your project using `lein cloverage --codecov` for instance. Once you fix the cloverage issue on your project,
then SonarClojure should be able to parse the results. The same idea applies to all the plugins.
 
* In general, plugins should not stop execution in case of errors, unless an exception happens.

* You can use `-X` or `--debug` when running sonar-scanner to get a detailed information of what SonarClojure is trying to do.

## Building from Source
```sh
./mvnw clean package
```

Maven will generate a SNAPSHOT under the folder ***target***.

## Compatibility
SonarClojure targets SonarQube Community Build 26.1 (and newer in the 26.x line).
Builds require Java 21.

## License

SonarClojure is open-sourced software licensed under the [MIT license](https://github.com/fsantiag/sonar-clojure/blob/master/LICENSE).
