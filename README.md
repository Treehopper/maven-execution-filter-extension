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
resolved, so the plugin is never downloaded, never started, and never gets a chance to log anything
- for plugins declared directly in `<build><plugins>`, inherited from a parent POM, or declared
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

The first time the extension runs in a project, it writes this default list to `.mvn/filterPlugins.properties`
(see [Customizing the filtered plugins](#customizing-the-filtered-plugins)) - edit that file to
change it from then on.

# Example Usage
In your `${baseDir}/.mvn/extensions.xml` (requires Maven 3.3.1):
```xml
<extensions xmlns="https://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="https://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="https://maven.apache.org/EXTENSIONS/1.0.0 https://maven.apache.org/xsd/core-extensions-1.0.0.xsd">

    <extension>
        <groupId>com.github.Treehopper</groupId>
        <artifactId>maven-execution-filter-extension</artifactId>
        <version>1.9.0-alpha</version>
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
The persisted, user-editable way: `.mvn/filterPlugins.properties`, a standard `.properties` file
with a single `filterPlugins` key - same name and comma-separated syntax as the system property
below, just persisted. The first time the extension runs in a project (i.e. that file doesn't
exist yet), it's created with the [default list](#default-behaviour) above:
```properties
# Plugins filtered from local builds by maven-execution-filter-extension.
#
# Comma-separated artifactId[:groupId[:version]] descriptors, one per continuation line
# below for readability - keep the trailing '\' on every line except the last. Add or
# remove a line to change what's filtered locally; clear the value entirely
# (filterPlugins=) to disable filtering. Commit this file so your team shares the same
# local dev experience.
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
but the last) to change what's filtered on the next build, no flags needed. Commit it like you
would `.mvn/extensions.xml` or `.mvn/jvm.config`, so the whole team gets the same local dev
experience rather than everyone tuning their own copy. Clearing the value entirely
(`filterPlugins=`) disables filtering, same as the blank system property value below.

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
This is additive: it filters the built-in list of generator plugins on top of whatever
`filterPlugins`/the persisted config file already filters, even when that other list is empty or
explicitly disabled (`-DfilterPlugins=`). There is currently no way to customize *which* generator
plugins this flag targets - if you need that, use `filterPlugins`/the config file directly instead.

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
[INFO]   filtered from this build : org.apache.maven.plugins:maven-checkstyle-plugin
[INFO]   could still be filtered  : org.apache.maven.plugins:maven-surefire-plugin
[INFO]   configured to be filtered: maven-checkstyle-plugin:org.apache.maven.plugins, ...
[INFO]   customize the list       : -DfilterPlugins=artifactId[:groupId[:version]][,...]
[INFO]   disable entirely         : -DfilterPlugins=
[ERROR] Build cancelled: -DfilterInfo only prints this summary, it does not run the build - remove
it to build normally.
```
The plugin's version is deliberately left out here: filtering matches on artifactId/groupId only,
never on version (see [Customizing the filtered plugins](#customizing-the-filtered-plugins)), so
the same plugin pinned to a different version in each module of a reactor is genuinely one entry,
not several.

`-DfilterInfo` is a dry run, not something you add alongside a real build: it cancels the build
right after printing the summary, before any project actually executes, so the console shows
`BUILD FAILURE` and the process exits non-zero - intentional, not a bug, so a script or CI job that
runs it by mistake doesn't quietly report success without having built anything. Run the same
command without `-DfilterInfo` once you're done reading the summary.

## Disabling the extension (e.g. for CI)
Since `.mvn/filterPlugins.properties` (like `.mvn/jvm.config`) is typically committed to the repository,
it applies to every build, including your CI pipeline. To let CI run with the full, unfiltered set
of plugins without touching either file, override with a blank value on the command line:
```
mvn -DfilterPlugins= verify
```

## Compatibility with mvnd (the Maven Daemon)
The extension re-reads `filterPlugins` and `.mvn/filterPlugins.properties` on every build rather than
caching either once, so it correctly picks up a different value - or a just-saved edit to the file
- on the next `mvnd` invocation even when the daemon reuses the same warm JVM (and thus the same
extension component instance) - no need to run `mvnd --stop` in between. `-DfilterInfo`'s
once-per-build summary (see above) is also safe under daemon reuse: it is printed by a Maven
lifecycle participant hook that fires exactly once per build (with a fresh `MavenSession` each
time, even when the daemon reuses the same component instance), rather than by a flag on the model
reader itself, so it reliably prints again on the next build rather than only the first one the
daemon ever served. (Resolving the extension itself from JitPack under `mvnd` is a separate matter
- see the note on core extension resolution above.)

## Compatibility with other core extensions (e.g. maven-git-versioning-extension)
Maven allows exactly one core extension to take over reading POMs from disk - the mechanism every
such extension (including this one) uses to hook in is a single, unqualified lookup that can only
resolve to one implementation. If another installed core extension does the same thing - the
best-known example being [maven-git-versioning-extension](https://github.com/qoomon/maven-git-versioning-extension),
which rewrites `${project.version}` based on the current git branch/tag - only one of the two can
win that lookup, determined by extension load order rather than anything either extension's author
controls.

To still let both work together, this extension looks up every other registered POM reader and
delegates the actual disk read to one of them before applying its own filtering - so whichever of
the two ends up winning the lookup, the other one's logic still runs as part of the chain. This
requires no configuration; it is automatic whenever another core extension is present.

### Compatibility matrix

Verified by hand; combinations not listed (other Maven versions, other
maven-git-versioning-extension versions) are simply untested, not known to be broken.

| Maven | maven-git-versioning-extension | Result |
| --- | --- | --- |
| 3.8.3  | - (this extension alone) | Works |
| 3.9.16 | - (this extension alone) | Works |
| 3.8.3  | 7.3.0 | Works |
| 3.9.16 | 7.3.0 | Works |
| 3.8.3  | 9.12.0 / 9.12.1 (9.7.0+) | Fails - upstream bug, see below |
| 3.9.16 | 9.12.0 / 9.12.1 (9.7.0+) | Fails - upstream bug, see below |

This only works if the *other* extension is new enough to have equivalent delegation logic of its
own, for the case where it wins the lookup instead. For maven-git-versioning-extension specifically:
- **7.x and later that predate 9.7.0**: has no concept of another `ModelProcessor` at all, so it
  either wins the lookup outright (and this extension never runs, with no error) or loses it (in
  which case this extension's delegation reaches it correctly, and both extensions work as
  expected) - verified with 7.3.0.
- **9.7.0+**: added its own delegation logic, but its plugin-version-rewriting step assumes the
  plugin list is unchanged before and after delegating - an assumption this extension's filtering
  breaks the moment it actually removes a plugin, regardless of whether the project is
  single-module or a multi-module reactor. This crashes the build entirely with `Internal error:
  java.lang.IllegalArgumentException: Collections sizes are not equals` inside
  `GitVersioningModelProcessor.updatePluginVersions`. This is a bug in that extension's own
  reconciliation logic, not something fixable from here; if you hit it, consider reporting it
  upstream, or pinning to a pre-9.7.0 release in the meantime.

Separately, on Maven 3.8.x and earlier: see the note on the Java 11 bytecode target below - an
older extension build compiled for Java 15+ is invisible to *any* other core extension's own
delegation search on those Maven versions (and to Maven's own core-extension lookup), regardless
of which extension it is combined with.

If you use a different extension that also reads/rewrites POMs and see it stop working (or this one
stop working) once both are installed, this is almost certainly the same class of conflict - check
whether it delegates to other `ModelProcessor` implementations before assuming otherwise.

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

