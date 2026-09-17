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

import static java.util.function.Predicate.not;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;
import org.codehaus.plexus.logging.Logger;

@Named("PropertiesProvider")
public class PropertiesProvider {

  public static final String FILTER_PLUGINS_SYS_PROP = "filterPlugins";

  /**
   * Short, easy-to-remember opt-in flag: prints, once at the start of the build, which plugins were
   * actually filtered, the full configured list, and a short reminder of how to customize or
   * disable it (e.g. {@code -DfilterInfo}).
   */
  public static final String FILTER_INFO_SYS_PROP = "filterInfo";

  /** Name of the persisted, user-editable config file inside {@code .mvn/}. */
  static final String CONFIG_FILE_NAME = "filterPlugins.txt";

  /**
   * Widely-used checker/reporting plugins that are removed from the build by default. Used both as
   * the fallback when the config file can't be read/written, and as the content the config file is
   * bootstrapped with the first time it's created.
   */
  static final List<String> DEFAULT_FILTERED_PLUGIN_DESCRIPTORS =
      List.of(
          "maven-checkstyle-plugin:org.apache.maven.plugins",
          "maven-pmd-plugin:org.apache.maven.plugins",
          "spotbugs-maven-plugin:com.github.spotbugs",
          "license-maven-plugin:org.codehaus.mojo",
          "jacoco-maven-plugin:org.jacoco",
          "arch-unit-maven-plugin:com.societegenerale.commons",
          "sortpom-maven-plugin:com.github.ekryd.sortpom",
          "maven-source-plugin:org.apache.maven.plugins",
          "maven-javadoc-plugin:org.apache.maven.plugins");

  private final Logger logger;

  @Inject
  public PropertiesProvider(Logger logger) {
    this.logger = logger;
  }

  /**
   * The plugin descriptors to filter out of the build.
   *
   * <p>If the {@value #FILTER_PLUGINS_SYS_PROP} system property is set, its comma-separated content
   * is used for this build only, taking priority over the config file below; setting it to a blank
   * value (e.g. {@code -DfilterPlugins=}) disables filtering entirely for this build, which is
   * useful to restore the original, unfiltered build on a CI server without touching the file.
   *
   * <p>Otherwise, the persisted {@value #CONFIG_FILE_NAME} file inside {@code .mvn/} is used. The
   * first time this runs in a project (i.e. that file doesn't exist yet), it is created with {@link
   * #DEFAULT_FILTERED_PLUGIN_DESCRIPTORS}, which are also returned for that build; from then on,
   * it's plain text meant to be edited directly - one descriptor per line, commit it so the whole
   * team shares the same local dev experience.
   */
  public List<String> getPluginDescriptors() {
    if (System.getProperties().containsKey(FILTER_PLUGINS_SYS_PROP)) {
      return parseCommaSeparated(System.getProperty(FILTER_PLUGINS_SYS_PROP, ""));
    }
    return readOrInitializeConfigFile();
  }

  /** Whether the {@value #FILTER_INFO_SYS_PROP} flag is set for this build. */
  public boolean isFilterInfoRequested() {
    return Boolean.parseBoolean(System.getProperty(FILTER_INFO_SYS_PROP, "false"));
  }

  private static List<String> parseCommaSeparated(String value) {
    return Arrays.stream(value.split(",")).map(String::trim).filter(not(String::isEmpty)).toList();
  }

  private List<String> readOrInitializeConfigFile() {
    var configFile = configFilePath();
    try {
      if (Files.notExists(configFile)) {
        writeDefaultConfigFile(configFile);
        return DEFAULT_FILTERED_PLUGIN_DESCRIPTORS;
      }
      return readConfigFile(configFile);
    } catch (IOException e) {
      logger.warn("Could not access " + configFile + ", falling back to built-in defaults", e);
      return DEFAULT_FILTERED_PLUGIN_DESCRIPTORS;
    }
  }

  /**
   * {@code .mvn/} always already exists by the time this runs - it's where {@code extensions.xml}
   * itself lives - so only the file needs creating, not the directory. {@code
   * maven.multiModuleProjectDirectory} is the system property Maven itself sets to the directory
   * containing {@code .mvn/}, which is the reliable way to find it regardless of which submodule's
   * pom.xml happens to be getting read.
   */
  private static Path configFilePath() {
    var projectDirectory =
        System.getProperty("maven.multiModuleProjectDirectory", System.getProperty("user.dir"));
    return Path.of(projectDirectory, ".mvn", CONFIG_FILE_NAME);
  }

  private void writeDefaultConfigFile(Path configFile) throws IOException {
    try {
      Files.writeString(
          configFile,
          defaultConfigFileContent(),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE_NEW);
      logger.info(
          "Created "
              + configFile
              + " with the default filtered plugins - edit it to customize which plugins are"
              + " filtered locally.");
    } catch (FileAlreadyExistsException raceWithAnotherProcess) {
      // e.g. a parallel reactor build created it first between our exists-check and this write;
      // whatever it wrote is used instead, same as if it had always been there.
    }
  }

  private static String defaultConfigFileContent() {
    var header =
        """
        # Plugins filtered from local builds by maven-execution-filter-extension.
        #
        # One artifactId[:groupId[:version]] descriptor per line. Blank lines and lines starting
        # with '#' are ignored. Remove a line to stop filtering that plugin locally; add a line to
        # filter another. Commit this file so your team shares the same local dev experience.
        #
        # To override this file for a single build without editing it:
        #   -DfilterPlugins=artifactId[:groupId[:version]][,...]
        # To disable filtering entirely for one build:
        #   -DfilterPlugins=
        """;
    return header + String.join("\n", DEFAULT_FILTERED_PLUGIN_DESCRIPTORS) + "\n";
  }

  private static List<String> readConfigFile(Path configFile) throws IOException {
    return Files.readAllLines(configFile, StandardCharsets.UTF_8).stream()
        .map(String::trim)
        .filter(not(String::isEmpty))
        .filter(not(line -> line.startsWith("#")))
        .toList();
  }
}
