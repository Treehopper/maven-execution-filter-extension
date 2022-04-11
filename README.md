# Maven Execution Filter Extension
A Maven Extension to remove (not skip) plugins from your build at runtime w/o modifying pom-files, resulting in faster builds, smaller logs and the ability to temporarily ignore minor issues.
The intended purpose of this extension is to improve the developer UX when working with large builds, which often use parent-poms optimized for CI.

## Why not skip those plugin executions using common skip options?
Skip-options have to be found, and applied correctly in your IDE, CLI, etc.
If they are applied correctly, the plugins will still be downloaded, started, and pollute logs.

# Example Usage
In your `${baseDir}/.mvn/extensions.xml`:
```xml
<extensions xmlns="https://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="https://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="https://maven.apache.org/EXTENSIONS/1.0.0 https://maven.apache.org/xsd/core-extensions-1.0.0.xsd">

    <extension>
        <groupId>com.github.Treehopper</groupId>
        <artifactId>maven-execution-filter-extension</artifactId>
        <version>1.2.0-alpha</version>
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

# Development
[![Gitpod ready-to-code](https://img.shields.io/badge/Gitpod-ready--to--code-blue?logo=gitpod)](https://gitpod.io/#https://github.com/Treehopper/maven-execution-filter-extension)

[![Total alerts](https://img.shields.io/lgtm/alerts/g/Treehopper/maven-execution-filter-extension.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/Treehopper/maven-execution-filter-extension/alerts/)

[![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/Treehopper/maven-execution-filter-extension.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/Treehopper/maven-execution-filter-extension/context:java)

[![Jitpack](https://jitpack.io/v/Treehopper/maven-execution-filter-extension.svg)](https://jitpack.io/#Treehopper/maven-execution-filter-extension)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)