/*-
 * #%L
 * maven-execution-filter-extension
 * %%
 * Copyright (C) 2022 Max Hohenegger <maven-execution-filter-extension@hohenegger.eu>
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package eu.hohenegger.filter.extension;

import static com.soebes.itf.extension.assertj.MavenITAssertions.assertThat;
import static com.soebes.itf.jupiter.extension.MavenCLIOptions.*;
import static eu.hohenegger.filter.extension.PropertiesProvider.FILTER_INFO_SYS_PROP;
import static eu.hohenegger.filter.extension.PropertiesProvider.FILTER_PLUGINS_SYS_PROP;

import com.soebes.itf.jupiter.extension.MavenJupiterExtension;
import com.soebes.itf.jupiter.extension.MavenOption;
import com.soebes.itf.jupiter.extension.MavenTest;
import com.soebes.itf.jupiter.extension.SystemProperty;
import com.soebes.itf.jupiter.maven.MavenExecutionResult;
import com.soebes.itf.jupiter.maven.MavenProjectResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;

@MavenJupiterExtension
public class ExtensionIT {

  /**
   * Only the {@code coexists_with_other_core_extension} fixture ships a
   * maven-git-versioning-extension config, and that extension needs an actual git repository
   * (branch/commit) to do anything - itf-maven-plugin copies fixture files verbatim, it does not
   * create one. Runs once per test, before itf's own build of the copied project, and is a no-op
   * (skipped instantly) for every other fixture.
   */
  @BeforeEach
  void initGitRepositoryIfFixtureNeedsOne(MavenProjectResult mavenProjectResult) throws Exception {
    var projectDirectory = mavenProjectResult.getTargetProjectDirectory();
    if (!Files.isRegularFile(projectDirectory.resolve(".mvn/maven-git-versioning-extension.xml"))
        || Files.isDirectory(projectDirectory.resolve(".git"))) {
      return;
    }
    runGit(projectDirectory, "init", "-q", "-b", "it-test");
    runGit(projectDirectory, "add", "-A");
    runGit(
        projectDirectory,
        "-c",
        "user.email=it@it.test",
        "-c",
        "user.name=it",
        "commit",
        "-q",
        "-m",
        "init");
  }

  private static void runGit(Path directory, String... args)
      throws IOException, InterruptedException {
    var command = new ArrayList<>(List.of("git"));
    command.addAll(List.of(args));
    var process =
        new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
    var output = new String(process.getInputStream().readAllBytes());
    if (process.waitFor() != 0) {
      throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + output);
    }
  }

  @MavenTest
  @MavenOption(NO_TRANSFER_PROGRESS)
  @MavenOption(ERRORS)
  void no_config(MavenExecutionResult result) {
    assertThat(result).isSuccessful();
    assertThat(result)
        .out()
        .info()
        .contains("Plugin [org.apache.maven.plugins:maven-checkstyle-plugin:3.1.2] filtered");
  }

  @MavenTest
  @MavenOption(NO_TRANSFER_PROGRESS)
  @MavenOption(ERRORS)
  @SystemProperty(value = FILTER_PLUGINS_SYS_PROP, content = " ")
  void disabled(MavenExecutionResult result) {
    assertThat(result).isFailure();
    assertThat(result).out().info().contains("--- checkstyle:3.1.2:check (default) @ bar ---");
  }

  @MavenTest
  @MavenOption(NO_TRANSFER_PROGRESS)
  @SystemProperty(value = FILTER_PLUGINS_SYS_PROP, content = "maven-checkstyle-plugin")
  void artifactid_only(MavenExecutionResult result) {
    assertThat(result).isSuccessful();
    assertThat(result)
        .out()
        .info()
        .contains("Plugin [org.apache.maven.plugins:maven-checkstyle-plugin:3.1.2] filtered");
  }

  @MavenTest
  @MavenOption(NO_TRANSFER_PROGRESS)
  @SystemProperty(
      value = FILTER_PLUGINS_SYS_PROP,
      content = "maven-checkstyle-plugin:org.apache.maven.plugins")
  void artifactid_groupid(MavenExecutionResult result) {
    assertThat(result).isSuccessful();
    assertThat(result)
        .out()
        .info()
        .contains("Plugin [org.apache.maven.plugins:maven-checkstyle-plugin:3.1.2] filtered");
  }

  @MavenTest
  @MavenOption(NO_TRANSFER_PROGRESS)
  void jvm_config(MavenExecutionResult result) {
    assertThat(result).isSuccessful();
    assertThat(result)
        .out()
        .info()
        .contains("Plugin [org.apache.maven.plugins:maven-checkstyle-plugin:3.1.2] filtered")
        .contains("Plugin [org.apache.maven.plugins:maven-pmd-plugin:3.21.2] filtered");
  }

  @MavenTest
  @MavenOption(NO_TRANSFER_PROGRESS)
  void profile_plugin(MavenExecutionResult result) {
    assertThat(result).isSuccessful();
    assertThat(result)
        .out()
        .info()
        .contains("Plugin [org.apache.maven.plugins:maven-checkstyle-plugin:3.1.2] filtered");
  }

  @MavenTest
  @MavenOption(NO_TRANSFER_PROGRESS)
  @SystemProperty(
      value = FILTER_PLUGINS_SYS_PROP,
      content = "maven-checkstyle-plugin:org.apache.maven.plugins")
  @SystemProperty(value = FILTER_INFO_SYS_PROP, content = "true")
  void filter_info(MavenExecutionResult result) {
    assertThat(result).isSuccessful();
    assertThat(result)
        .out()
        .info()
        .contains("maven-execution-filter-extension (-DfilterInfo):")
        .contains(
            "  filtered from this build : org.apache.maven.plugins:maven-checkstyle-plugin:3.1.2")
        .contains("  could still be filtered  : none")
        .contains("  configured to be filtered: maven-checkstyle-plugin:org.apache.maven.plugins")
        .contains(
            "  customize the list       : -DfilterPlugins=artifactId[:groupId[:version]][,...]")
        .contains("  disable entirely         : -DfilterPlugins=");
  }

  /**
   * maven-surefire-plugin is bound purely via Maven's default lifecycle mapping for jar packaging
   * in this fixture (no explicit &lt;plugin&gt; declaration at all), so it never appears in the raw
   * model this extension's ModelProcessor reads - filterPlugins listing it has no effect. See the
   * "What can and can't be filtered" section of the README.
   */
  @MavenTest
  @MavenOption(NO_TRANSFER_PROGRESS)
  @SystemProperty(
      value = FILTER_PLUGINS_SYS_PROP,
      content = "maven-surefire-plugin:org.apache.maven.plugins")
  void default_lifecycle_plugin_not_filterable(MavenExecutionResult result) {
    assertThat(result).isSuccessful();
    assertThat(result)
        .out()
        .info()
        .anyMatch(line -> line.matches("--- surefire:.*:test \\(default-test\\) @ bar ---"));
  }

  /**
   * No {@value #FILTER_PLUGINS_SYS_PROP} system property at all here - the fixture ships its own
   * pre-existing {@code .mvn/filterPlugins.properties}, simulating a developer having already
   * edited the file created on a previous run. Proves the persisted-file path works without any -D
   * flag.
   */
  @MavenTest
  @MavenOption(NO_TRANSFER_PROGRESS)
  void persisted_config_file(MavenExecutionResult result) {
    assertThat(result).isSuccessful();
    assertThat(result)
        .out()
        .info()
        .contains("Plugin [org.apache.maven.plugins:maven-checkstyle-plugin:3.1.2] filtered");
  }

  /**
   * Regression test for a real conflict between two core extensions that both need to become "the"
   * {@code ModelProcessor} Maven calls to read POMs - see {@link FilteringModelProcessor}'s class
   * javadoc. Combines this extension with <a
   * href="https://github.com/qoomon/maven-git-versioning-extension">maven-git-versioning-extension</a>,
   * which rewrites the project version from the current git branch (set up as a fixed "it-test"
   * branch by {@link #initGitRepositoryIfFixtureNeedsOne}), pinned to 7.3.0 - confirmed, by hand,
   * to have no delegation logic of its own, making this the scenario where this extension's own
   * delegation (rather than the other extension's) is what makes coexistence possible at all. See
   * the "Compatibility with other core extensions" section of the README for the newer
   * maven-git-versioning-extension versions this does not cover.
   */
  @MavenTest
  @MavenOption(NO_TRANSFER_PROGRESS)
  @SystemProperty(value = FILTER_INFO_SYS_PROP, content = "true")
  void coexists_with_other_core_extension(MavenExecutionResult result) {
    assertThat(result).isSuccessful();
    assertThat(result).out().info().anyMatch(line -> line.contains("it-test-SNAPSHOT"));
    assertThat(result)
        .out()
        .info()
        .contains("Plugin [org.apache.maven.plugins:maven-checkstyle-plugin:3.1.2] filtered")
        .contains("maven-execution-filter-extension (-DfilterInfo):");
  }
}
