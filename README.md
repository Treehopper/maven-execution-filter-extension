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
checker/reporting plugins, plus the plugins that build release artifacts you don't need for local
development, from every build:
- `maven-checkstyle-plugin`
- `maven-pmd-plugin`
- `spotbugs-maven-plugin`
- `license-maven-plugin`
- `jacoco-maven-plugin`
- `arch-unit-maven-plugin`
- `sortpom-maven-plugin`
- `maven-source-plugin`
- `maven-javadoc-plugin`

The checker/reporting plugins are exactly the kind already enforced by your CI pipeline against a
central repository, so re-running (and re-reading the same warnings from) them on every local
build is mostly wasted time. `maven-source-plugin` and `maven-javadoc-plugin` only matter when
publishing a release, so there is no reason to build sources/javadoc jars on every local build
either.

# Example Usage
In your `${baseDir}/.mvn/extensions.xml` (requires Maven 3.3.1):
```xml
<extensions xmlns="https://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="https://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="https://maven.apache.org/EXTENSIONS/1.0.0 https://maven.apache.org/xsd/core-extensions-1.0.0.xsd">

    <extension>
        <groupId>com.github.Treehopper</groupId>
        <artifactId>maven-execution-filter-extension</artifactId>
        <version>1.7.0-alpha</version>
    </extension>
</extensions>
```

This artifact is only published to [JitPack](https://jitpack.io), not Maven Central, and Maven
resolves core extensions before it reads `pom.xml` or activates any `settings.xml` profile - a
`<pluginRepositories>` entry in either place cannot make JitPack available to them. In practice you
need a repository manager (e.g. Nexus/Artifactory) that mirrors JitPack for you, or you can build
and `mvn install` this extension from source into your own local repository, which sidesteps
JitPack entirely - see [`example-project`](example-project) for a runnable demonstration of exactly
that.

Once resolved, that's it - the [default plugin list](#default-behaviour) above is now filtered out
of every local build.

## Customizing the filtered plugins
To filter a different set of plugins, set the `filterPlugins` system property to a comma-separated
list of `artifactId[:groupId[:version]]` descriptors, e.g. in your `${baseDir}/.mvn/jvm.config`:
```
-DfilterPlugins=maven-checkstyle-plugin:org.apache.maven.plugins,maven-pmd-plugin:org.apache.maven.plugins,spotbugs-maven-plugin:com.github.spotbugs,license-maven-plugin:org.codehaus.mojo,jacoco-maven-plugin:org.jacoco,arch-unit-maven-plugin:com.societegenerale.commons,sortpom-maven-plugin:com.github.ekryd.sortpom,maven-source-plugin:org.apache.maven.plugins,maven-javadoc-plugin:org.apache.maven.plugins
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

## Compatibility with mvnd (the Maven Daemon)
The extension re-reads `filterPlugins` on every build rather than caching it once, so it correctly
picks up a different value on the next `mvnd` invocation even when the daemon reuses the same
warm JVM (and thus the same extension component instance) - no need to run `mvnd --stop` in
between. (Resolving the extension itself from JitPack under `mvnd` is a separate matter - see the
note on core extension resolution above.)

# Development
Building this project requires JDK 17+ (the compiled classes still target Java 17 - see
`java.version` in `pom.xml`).

[![Gitpod ready-to-code](https://img.shields.io/badge/Gitpod-ready--to--code-blue?logo=gitpod)](https://gitpod.io/#https://github.com/Treehopper/maven-execution-filter-extension)

[![CircleCI](https://circleci.com/gh/Treehopper/maven-execution-filter-extension/tree/main.svg?style=svg)](https://circleci.com/gh/Treehopper/maven-execution-filter-extension/tree/main)

[![Total alerts](https://img.shields.io/lgtm/alerts/g/Treehopper/maven-execution-filter-extension.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/Treehopper/maven-execution-filter-extension/alerts/)

[![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/Treehopper/maven-execution-filter-extension.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/Treehopper/maven-execution-filter-extension/context:java)

[![Jitpack](https://jitpack.io/v/Treehopper/maven-execution-filter-extension.svg)](https://jitpack.io/#Treehopper/maven-execution-filter-extension)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)