# Maven Execution Filter Extension
[![Jitpack](https://jitpack.io/v/Treehopper/maven-execution-filter-extension.svg)](https://jitpack.io/#Treehopper/maven-execution-filter-extension)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/47d6016afc8e40a0a9684da2f6b2ea44)](https://app.codacy.com/gh/Treehopper/maven-execution-filter-extension/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
[![Codacy Badge](https://app.codacy.com/project/badge/Coverage/47d6016afc8e40a0a9684da2f6b2ea44)](https://app.codacy.com/gh/Treehopper/maven-execution-filter-extension/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_coverage)

A Maven Extension to remove (not skip) plugins from your build at runtime w/o modifying pom-files, resulting in faster builds, smaller logs and the ability to temporarily ignore minor issues.
The intended purpose of this extension is to improve the developer UX when working with large builds, which often use parent-poms optimized for CI.

## Why not skip those plugin executions using common skip options?
Skip-options have to be found, and applied correctly in your IDE, CLI, etc.
If they are applied correctly, the plugins will still be downloaded, started, and pollute logs.
This extension instead removes the plugin declaration from the in-memory Maven model before it is
resolved, so the plugin is never downloaded, never started, and never gets a chance to log anything,
whether it's declared directly in `<build><plugins>`, inherited from a parent POM, or declared
inside an (active) `<profile>`.

## Default behaviour
Out of the box, with no configuration at all, the extension removes the following widely-used
checker/reporting plugins, plus the plugins that build release/deployment artifacts you don't need
for local development, from every build:
- `maven-checkstyle-plugin`
- `maven-pmd-plugin`
- `spotbugs-maven-plugin`
- `license-maven-plugin`
- `jacoco-maven-plugin`
- `arch-unit-maven-plugin`
- `sortpom-maven-plugin`
- `sonar-maven-plugin`
- `maven-source-plugin`
- `maven-javadoc-plugin`
- `cyclonedx-maven-plugin`
- `jib-maven-plugin`

The checker/reporting plugins (including `sonar-maven-plugin`, which uploads its analysis to a
SonarQube/SonarCloud server) are exactly the kind already enforced by your CI pipeline against a
central repository, so re-running (and re-reading the same warnings from) them on every local
build is mostly wasted time. `maven-source-plugin`, `maven-javadoc-plugin` and
`cyclonedx-maven-plugin` (SBOM generation) only matter when publishing a release, and
`jib-maven-plugin` only matters when actually building/pushing a container image, so there is no
reason to do any of that on every local build either.

The first time the extension runs, it writes this default list to `~/.mvn/filterPlugins.properties`
(see [Customizing the filtered plugins](#customizing-the-filtered-plugins)) - edit that file to
change it from then on.

# Installation

## Default: drop the jar in `${maven.home}/lib/ext`
Maven loads every jar under `${maven.home}/lib/ext` into its own core classloader at startup - the
same classpath a `.mvn/extensions.xml`-declared core extension ends up on, just installed once per
Maven installation via a plain file copy rather than per project via Maven's own dependency
resolution. That sidesteps the JitPack-resolution problem the per-project option below has (see
there for details) - you only need the jar file itself, once, and every build run with that Maven
installation picks it up from then on, with nothing to add to the project itself.

Grab the jar from the `.7z` archive attached to any
[release](https://github.com/Treehopper/maven-execution-filter-extension/releases) and copy it
into your Maven installation's `lib/ext` directory (`mvn -v` prints that location as "Maven home";
create the `lib/ext` directory if it doesn't already exist) - or build it from source yourself the
same way the release workflow does:
```
git clone https://github.com/Treehopper/maven-execution-filter-extension.git
cd maven-execution-filter-extension
mvn -Dlicense.skipDownloadLicenses=true -DskipTests package
cp target/maven-execution-filter-extension-*.jar /path/to/your/maven/lib/ext/
```
Its own runtime dependencies (`maven-core`, `maven-plugin-api`) are all `provided` scope - already
on that classpath as part of Maven itself - so the plain jar from `package` is all you need, no
shading required.

Since this is tied to the machine's Maven installation rather than the project, it's not something
you'd commit to the repository - every developer (and CI runner, see
[Disabling the extension](#disabling-the-extension-eg-for-ci) below) needs the jar in their own
`lib/ext`, unlike the per-project option below.

## Option: `.mvn/extensions.xml` (per project)
Declares the extension per project instead, resolved automatically like any other Maven artifact,
and (unlike `lib/ext`) committed to the repository so every developer gets it without a manual
install step - requires Maven 3.3.1+:
```xml
<extensions xmlns="https://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="https://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="https://maven.apache.org/EXTENSIONS/1.0.0 https://maven.apache.org/xsd/core-extensions-1.0.0.xsd">

    <extension>
        <groupId>com.github.Treehopper</groupId>
        <artifactId>maven-execution-filter-extension</artifactId>
        <version>2.0.0-alpha</version>
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

## After installing
Either way, that's it - the [default plugin list](#default-behaviour) above is now filtered out of
every local build.

## Customizing the filtered plugins
The persisted, user-editable way: `filterPlugins.properties`, a standard `.properties` file with a
`filterPlugins` key - same name and comma-separated syntax as the system property below, just
persisted - plus a separate `filterGenerators` key for code generator plugins (see
[Filtering code generator plugins](#filtering-code-generator-plugins)).

By default this lives at `~/.mvn/filterPlugins.properties` - one file per developer machine,
matching how the extension itself is typically installed once per machine now (see
[Installation](#installation)) rather than declared per project, and applying to every project
that developer builds. If a project has its own `.mvn/filterPlugins.properties` already, that one
takes priority instead - useful for a team that wants to commit a shared, project-specific list,
the way this worked before the extension defaulted to a per-user file; a project with no config
file of its own does *not* get one created for it automatically, only the per-user one does.

Either way, the first time it's used (i.e. that particular file doesn't exist yet), it's created
with the [default list](#default-behaviour) above:
```properties
# Plugins filtered from local builds by maven-execution-filter-extension.
#
# Comma-separated artifactId[:groupId[:version]] descriptors, one per continuation line
# below for readability - keep the trailing '\' on every line except the last. Add or
# remove a line to change what's filtered locally; clear the value entirely
# (filterPlugins=) to disable filtering. If this file is under a project's .mvn/, commit it
# so your team shares the same local dev experience; if it's ~/.mvn/filterPlugins.properties
# instead, it's yours alone, applied to every project you build.
#
# To override this file for a single build without editing it:
#   -DfilterPlugins=artifactId[:groupId[:version]][,...]
# To disable filtering entirely for one build without editing this file:
#   -DfilterPlugins=
filterPlugins=maven-checkstyle-plugin:org.apache.maven.plugins,\
  maven-pmd-plugin:org.apache.maven.plugins,\
  ...
```
From then on, it's yours: add or remove lines (keeping the trailing `\` continuation on every line
but the last) to change what's filtered on the next build, no flags needed. If it's a project-level
file, commit it like you would `.mvn/extensions.xml` or `.mvn/jvm.config`, so the whole team gets
the same local dev experience rather than everyone tuning their own copy. Clearing the value
entirely (`filterPlugins=`) disables filtering, same as the blank system property value below.

The one-off, invocation-only way: set the `filterPlugins` system property to a comma-separated list
of the same descriptor syntax, e.g.:
```
mvn -DfilterPlugins=maven-checkstyle-plugin:org.apache.maven.plugins,maven-pmd-plugin:org.apache.maven.plugins verify
```
Setting this property **fully replaces** the file for that build only (it is not merged with it,
and the file itself is left untouched - not even created if it didn't exist yet). This is the
right tool for a single ad-hoc build; for anything you want to keep, edit the file instead.

Either way, `groupId` and `version` are optional: if omitted, the plugin is matched on the
remaining segments alone (e.g. `maven-checkstyle-plugin` matches that artifactId regardless of
groupId or version). The `artifactId` must match exactly - `surefire` will not match
`maven-surefire-plugin`.

### What can and can't be filtered
This works for any plugin present in a project's effective `<build><plugins>` by the time Maven has
fully read the reactor - directly declared, inherited from a parent POM, inside an active
`<profile>`, **or bound purely through Maven's own default lifecycle mapping** for your packaging
type (e.g. `maven-surefire-plugin`, `maven-compiler-plugin`, `maven-resources-plugin` for `jar`
packaging), even when a project never mentions that plugin at all. To skip test execution
specifically, Maven's own `-DskipTests` (or `-Dmaven.test.skip=true`) is still simpler than
filtering `maven-surefire-plugin` outright, since it avoids fully removing the plugin (and thus,
for `-DfilterInfo` purposes, still reports it as present).

## Filtering code generator plugins
Code generator plugins (e.g. `openapi-generator-maven-plugin`, `swagger-codegen-maven-plugin`) are
**not** part of the default list above, and adding them via `filterPlugins` isn't recommended
either: unlike a checker or a release-artifact step, removing a generator can break the build
outright if the sources it generates are needed to compile. Add `-DfilterGenerators` to a build
that doesn't need the generated code regenerated - e.g. it's already been generated and committed,
or this particular build doesn't touch that module - to filter them anyway:
```
mvn -DfilterGenerators verify
```
This is additive: it filters the configured generator plugin list on top of whatever
`filterPlugins`/the persisted config file already filters, even when that other list is empty or
explicitly disabled (`-DfilterPlugins=`).

The generator plugin list itself is just as customizable as `filterPlugins`, but lives under its
own `filterGenerators` key in the same `filterPlugins.properties` file (see
[Customizing the filtered plugins](#customizing-the-filtered-plugins)), bootstrapped the same way
with its own built-in default the first time the file is created:
```properties
filterGenerators=openapi-generator-maven-plugin:org.openapitools,\
  swagger-codegen-maven-plugin
```
Add or remove a line here to change which generator plugins `-DfilterGenerators` targets - same
syntax, same rules (`groupId`/`version` optional) as `filterPlugins`. Unlike `filterPlugins`,
there is no `-DfilterGenerators=...` system-property override for the list itself: that flag only
turns this filtering on or off - which generator plugins it filters always comes from the config
file.

## Seeing what's being filtered
Add `-DfilterInfo` to any build to print, once at the start: exactly which plugins were removed
from that build, which of the plugins still declared in it you could add to the filter too, the
full list currently configured to be filtered, and a short reminder of how to customize or disable
it. In a multi-module (reactor) build, this reflects the totals across every module, not just the
first one Maven happens to read (typically the parent aggregator POM, which usually has no plugins
of its own):
```
mvn -DfilterInfo verify
```
```
[INFO] maven-execution-filter-extension (-DfilterInfo):
[INFO]   filtered from this build:
[INFO]     org.apache.maven.plugins:maven-checkstyle-plugin
[INFO]     org.apache.maven.plugins:maven-pmd-plugin
[INFO]   could still be filtered:
[INFO]     org.apache.maven.plugins:maven-surefire-plugin
[INFO]     org.apache.maven.plugins:maven-compiler-plugin
[INFO]   configured to be filtered:
[INFO]     maven-checkstyle-plugin:org.apache.maven.plugins
[INFO]     maven-pmd-plugin:org.apache.maven.plugins
[INFO]     ...
[INFO]   customize the list       : -DfilterPlugins=artifactId[:groupId[:version]][,...]
[INFO]   disable entirely         : -DfilterPlugins=
[ERROR] Build cancelled: -DfilterInfo only prints this summary, it does not run the build - remove
it to build normally.
```
Each list prints one plugin per indented line rather than a single comma-separated line - a "none"
result still prints inline with its label instead (e.g. `  configured to be filtered: none
(disabled)` when `filterPlugins` is blank). Note that `could still be filtered` now also includes
default-lifecycle-bound plugins like `maven-surefire-plugin` and `maven-compiler-plugin` (see
[What can and can't be filtered](#what-can-and-cant-be-filtered)), not just explicitly declared
ones. The plugin's version is deliberately left out too: filtering matches on artifactId/groupId
only, never on version (see [Customizing the filtered plugins](#customizing-the-filtered-plugins)),
so the same plugin pinned to a different version in each module of a reactor is genuinely one
entry, not several.

`-DfilterInfo` is a dry run, not something you add alongside a real build: it cancels the build
right after printing the summary, before any project actually executes, so the console shows
`BUILD FAILURE` and the process exits non-zero - intentional, not a bug, so a script or CI job that
runs it by mistake doesn't quietly report success without having built anything. Run the same
command without `-DfilterInfo` once you're done reading the summary.

## Disabling the extension (e.g. for CI)
If a project commits its own `.mvn/filterPlugins.properties` (like `.mvn/jvm.config`), it applies
to every build, including your CI pipeline. (The default per-user `~/.mvn/filterPlugins.properties`
is normally moot for CI instead, since a CI runner doesn't already have one - though it would
otherwise get created fresh with the built-in defaults, same as on a developer's first local build,
if the extension is installed there too.) To let CI run with the full, unfiltered set of plugins
without touching either file, override with a blank value on the command line:
```
mvn -DfilterPlugins= verify
```

## Compatibility with mvnd (the Maven Daemon)
The extension re-reads `filterPlugins` and `.mvn/filterPlugins.properties` on every build rather than
caching either once, so it correctly picks up a different value, or a just-saved edit to the file,
on the next `mvnd` invocation even when the daemon reuses the same warm JVM (and thus the same
extension component instance) - no need to run `mvnd --stop` in between. `-DfilterInfo`'s
once-per-build summary (see above) is also safe under daemon reuse: it is printed by a Maven
lifecycle participant hook that fires exactly once per build (with a fresh `MavenSession` each
time, even when the daemon reuses the same component instance), rather than by a flag on the model
reader itself, so it reliably prints again on the next build rather than only the first one the
daemon ever served. (Resolving the extension itself from JitPack under `mvnd` is a separate matter;
see the `.mvn/extensions.xml` option under [Installation](#installation) above.)

# Development
Building this project requires JDK 17+, but the compiled classes target Java 11 (see
`java.version` in `pom.xml`) - deliberately capped there rather than following the build JDK.
Maven 3.8.x and earlier bundle a much older Eclipse Sisu whose embedded ASM fork silently fails to
discover an `@Named` core-extension component compiled for Java 15+ (no error - it just never
runs). Since this project *is* a core extension, loaded by whatever Maven version the user's own
project happens to run rather than anything this build controls, raising `java.version` past 14
means deliberately dropping support for Maven <= 3.8.x - do that only on purpose, not as a
drive-by bump.

## Code quality and coverage (Codacy)
The repository is connected to [Codacy](https://app.codacy.com/gh/Treehopper/maven-execution-filter-extension/dashboard)
for code quality analysis. `.github/workflows/ci.yml` builds and tests every push/PR, generates a
JaCoCo coverage report (unit tests only - the integration tests fork entirely separate `mvn`
processes JaCoCo's agent doesn't reach), and uploads it to Codacy via the `CODACY_PROJECT_TOKEN`
repository secret.

