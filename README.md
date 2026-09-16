# Maven Execution Filter Extension
A Maven Extension to remove (not skip) plugins from your build at runtime w/o modifying pom-files, resulting in faster builds, smaller logs and the ability to temporarily ignore minor issues.
The intended purpose of this extension is to improve the developer UX when working with large builds, which often use parent-poms optimized for CI.

## Why not skip those plugin executions using common skip options?
Skip-options have to be found, and applied correctly in your IDE, CLI, etc.
If they are applied correctly, the plugins will still be downloaded, started, and pollute logs.
This extension instead removes the plugin declaration from the in-memory Maven model before it is
resolved, so the plugin is never downloaded, never started, and never gets a chance to log anything
- for plugins declared directly in `<build><plugins>`, inherited from a parent POM, or declared
inside an (active) `<profile>`.

## Default behaviour
Out of the box, with no configuration at all, the extension removes the following widely-used
checker/reporting plugins from every build:
- `maven-checkstyle-plugin`
- `maven-pmd-plugin`
- `spotbugs-maven-plugin`
- `license-maven-plugin`
- `jacoco-maven-plugin`

These are exactly the kind of plugins that are already enforced by your CI pipeline against a
central repository, so re-running (and re-reading the same warnings from) them on every local
build is mostly wasted time.

# Example Usage
In your `${baseDir}/.mvn/extensions.xml` (requires Maven 3.3.1):
```xml
<extensions xmlns="https://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="https://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="https://maven.apache.org/EXTENSIONS/1.0.0 https://maven.apache.org/xsd/core-extensions-1.0.0.xsd">

    <extension>
        <groupId>com.github.Treehopper</groupId>
        <artifactId>maven-execution-filter-extension</artifactId>
        <version>1.4.0-alpha</version>
    </extension>
</extensions>
```

```xml
<pluginRepositories>
    <pluginRepository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </pluginRepository>
</pluginRepositories>
```

That's it - the [default plugin list](#default-behaviour) above is now filtered out of every local
build.

## Customizing the filtered plugins
To filter a different set of plugins, set the `filterPlugins` system property to a comma-separated
list of `artifactId[:groupId[:version]]` descriptors, e.g. in your `${baseDir}/.mvn/jvm.config`:
```
-DfilterPlugins=maven-checkstyle-plugin:org.apache.maven.plugins,maven-pmd-plugin:org.apache.maven.plugins,spotbugs-maven-plugin:com.github.spotbugs,license-maven-plugin:org.codehaus.mojo,jacoco-maven-plugin:org.jacoco
```
Setting this property **fully replaces** the default list (it is not merged with it). `groupId`
and `version` are optional: if omitted, the plugin is matched on the remaining segments alone (e.g.
`maven-checkstyle-plugin` matches that artifactId regardless of groupId or version).

## Disabling the extension (e.g. for CI)
Since `.mvn/jvm.config` is typically committed to the repository, it applies to every build,
including your CI pipeline. To let CI run with the full, unfiltered set of plugins, override the
property with a blank value on the command line, which takes precedence over `jvm.config`:
```
mvn -DfilterPlugins= verify
```

# Development
[![Gitpod ready-to-code](https://img.shields.io/badge/Gitpod-ready--to--code-blue?logo=gitpod)](https://gitpod.io/#https://github.com/Treehopper/maven-execution-filter-extension)

[![CircleCI](https://circleci.com/gh/Treehopper/maven-execution-filter-extension/tree/main.svg?style=svg)](https://circleci.com/gh/Treehopper/maven-execution-filter-extension/tree/main)

[![Total alerts](https://img.shields.io/lgtm/alerts/g/Treehopper/maven-execution-filter-extension.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/Treehopper/maven-execution-filter-extension/alerts/)

[![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/Treehopper/maven-execution-filter-extension.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/Treehopper/maven-execution-filter-extension/context:java)

[![Jitpack](https://jitpack.io/v/Treehopper/maven-execution-filter-extension.svg)](https://jitpack.io/#Treehopper/maven-execution-filter-extension)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)