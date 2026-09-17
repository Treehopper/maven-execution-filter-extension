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
`maven-checkstyle-plugin` matches that artifactId regardless of groupId or version). The
`artifactId` must match exactly - `surefire` will not match `maven-surefire-plugin`.

### What can and can't be filtered
This only works for plugins **explicitly declared** in `<build><plugins>` - directly, inherited
from a parent POM, or inside an active `<profile>` - which is true of `maven-checkstyle-plugin`,
`maven-pmd-plugin` and the rest of the default list, since they only do anything once explicitly
bound to a phase. It does **not** work for plugins bound purely through Maven's own default
lifecycle mapping for your packaging type (e.g. `maven-surefire-plugin`, `maven-compiler-plugin`,
`maven-resources-plugin`, `maven-jar-plugin`, `maven-install-plugin`, `maven-deploy-plugin` for
`jar` packaging), since those never appear in `<build><plugins>` at all unless a project
re-declares them for configuration purposes - they're injected later, after this extension's
model-reading hook has already run, so there's nothing in the raw model to remove. To skip test
execution, use Maven's own `-DskipTests` (or `-Dmaven.test.skip=true`) instead.

## Seeing what's being filtered
Add `-DfilterInfo` to any build to print, once at the start: exactly which plugins were removed
from that build, which of the plugins still declared in it you could add to the filter too, the
full list currently configured to be filtered, and a short reminder of how to customize or disable
it:
```
mvn -DfilterInfo verify
```
```
[INFO] maven-execution-filter-extension (-DfilterInfo):
[INFO]   filtered from this build : org.apache.maven.plugins:maven-checkstyle-plugin:3.1.2
[INFO]   could still be filtered  : org.apache.maven.plugins:maven-surefire-plugin:3.6.0
[INFO]   configured to be filtered: maven-checkstyle-plugin:org.apache.maven.plugins, ...
[INFO]   customize the list       : -DfilterPlugins=artifactId[:groupId[:version]][,...]
[INFO]   disable entirely         : -DfilterPlugins=
```

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
between. `-DfilterInfo`'s once-per-build summary (see above) is also safe under daemon reuse: each
`mvnd`-served build runs on its own fresh thread even though the component instance is shared, and
the "print once" guard is thread-scoped rather than shared, so it reliably prints again on the next
build rather than only the first one the daemon ever served. (Resolving the extension itself from
JitPack under `mvnd` is a separate matter - see the note on core extension resolution above.)

# Development
Building this project requires JDK 17+ (the compiled classes still target Java 17 - see
`java.version` in `pom.xml`).

[![Gitpod ready-to-code](https://img.shields.io/badge/Gitpod-ready--to--code-blue?logo=gitpod)](https://gitpod.io/#https://github.com/Treehopper/maven-execution-filter-extension)

[![CircleCI](https://circleci.com/gh/Treehopper/maven-execution-filter-extension/tree/main.svg?style=svg)](https://circleci.com/gh/Treehopper/maven-execution-filter-extension/tree/main)

[![Total alerts](https://img.shields.io/lgtm/alerts/g/Treehopper/maven-execution-filter-extension.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/Treehopper/maven-execution-filter-extension/alerts/)

[![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/Treehopper/maven-execution-filter-extension.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/Treehopper/maven-execution-filter-extension/context:java)

[![Jitpack](https://jitpack.io/v/Treehopper/maven-execution-filter-extension.svg)](https://jitpack.io/#Treehopper/maven-execution-filter-extension)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)