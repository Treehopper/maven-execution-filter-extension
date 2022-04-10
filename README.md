# Maven Execution Filter Extension
A Maven Extension to remove (not skip) plugins from your build at runtime w/o modifying pom-files, resulting in faster builds, smaller logs and the ability to temporarily ignore minor issues.
The intended purpose of this extension is to improve the developer UX when working with large builds, which are often used for CI.

## Why not skip those plugin executions using common skip options?
Skip-options have to be found, and applied correctly in your CLI, IDE, etc. If they are applied correctly, the plugins will still be downloaded, started, and pollute logs.

# Example Usage
In your `${baseDir}/.mvn/extensions.xml`:
```
<extensions xmlns="https://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="https://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="https://maven.apache.org/EXTENSIONS/1.0.0 https://maven.apache.org/xsd/core-extensions-1.0.0.xsd">

    <extension>
        <groupId>com.github.Treehopper</groupId>
        <artifactId>maven-execution-filter-extension</artifactId>
        <version>${version}</version>
    </extension>
</extensions>
```
