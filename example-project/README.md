# Example Project

A minimal, runnable project showing what `maven-execution-filter-extension` actually does. It is
plugged in via `.mvn/extensions.xml`, exactly as described in the [main README](../README.md).

`pom.xml` binds `maven-checkstyle-plugin` to the `validate` phase, checked against
[`checkstyle.xml`](checkstyle.xml), which forbids star imports. `Main.java` deliberately uses one
(`import java.util.*;`), so checkstyle would fail the build if it actually ran.

## Setup

This example depends on the extension being available in your local repository. From the
repository root (only needs to be done once, or again after changing the extension's code):
```
mvn -Dlicense.skipDownloadLicenses=true install -DskipTests
```
(`maven-execution-filter-extension` is only published to [JitPack](https://jitpack.io), not Maven
Central - installing locally sidesteps that entirely. To instead pull a released version from
JitPack, add it as a repository in your own `~/.m2/settings.xml`; a `<pluginRepositories>` entry in
`pom.xml` will not work, since core extensions are resolved before Maven ever reads `pom.xml`.)

## Try it

**Default behaviour.** `maven-checkstyle-plugin` is part of the extension's built-in default list,
so it is removed from the build before Maven ever downloads or runs it - the build succeeds, and
there is no checkstyle output at all. This also creates `.mvn/filterPlugins.properties` here, populated
with that default list (not committed in this repo, so you get to see it being created fresh):
```
mvn verify
cat .mvn/filterPlugins.properties
```

**Edit the persisted file** to change what's filtered from now on, no flags needed. Delete (or
comment out with `#`) the `maven-checkstyle-plugin` line in `.mvn/filterPlugins.properties`, then run
`mvn verify` again - checkstyle now runs and fails on the star import. This is the same file a real
project would commit so the whole team shares it.

**Disable filtering for one build** without touching the file, to see the build your CI pipeline
would actually run:
```
mvn -DfilterPlugins= verify
```

**Filter something else instead for one build** (this fully replaces the file's list for that
build only, so checkstyle now runs too, and fails for the same reason as above):
```
mvn -DfilterPlugins=maven-pmd-plugin verify
```

**See what's being filtered** - prints a one-time summary of what was actually removed, what's
configured, and how to change it:
```
mvn -DfilterInfo verify
```
