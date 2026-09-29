/*-
 * #%L
 * maven-execution-filter-extension
 * %%
 * Copyright (C) 2026 Max Hohenegger <maven-execution-filter-extension@hohenegger.eu>
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

import static eu.hohenegger.filter.extension.PropertiesProvider.CONFIG_FILE_NAME;
import static eu.hohenegger.filter.extension.PropertiesProvider.FILTER_GENERATORS_SYS_PROP;
import static eu.hohenegger.filter.extension.PropertiesProvider.FILTER_INFO_SYS_PROP;
import static eu.hohenegger.filter.extension.PropertiesProvider.FILTER_PLUGINS_SYS_PROP;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PropertiesProviderTest {

  private static final String MULTI_MODULE_PROJECT_DIRECTORY_SYS_PROP =
      "maven.multiModuleProjectDirectory";
  private static final String USER_HOME_SYS_PROP = "user.home";

  @TempDir private Path projectDirectory;
  @TempDir private Path homeDirectory;

  private String originalMultiModuleProjectDirectory;
  private String originalUserHome;
  private CapturingLogger logger;
  private PropertiesProvider propertiesProvider;

  /**
   * Where the config file is created/read by default now: a project-level {@code .mvn/} is no
   * longer guaranteed to exist (the extension is typically installed once per machine via {@code
   * lib/ext} rather than declared per project), so {@code ~/.mvn/filterPlugins.properties} is the
   * default - a project-level file only takes priority if one already exists, see {@link
   * #projectLevelConfigFileOverridesTheHomeDirectoryOne}.
   */
  private Path configFile;

  private Path projectConfigFile;

  @BeforeEach
  public void setUp() {
    originalMultiModuleProjectDirectory =
        System.getProperty(MULTI_MODULE_PROJECT_DIRECTORY_SYS_PROP);
    originalUserHome = System.getProperty(USER_HOME_SYS_PROP);
    System.setProperty(MULTI_MODULE_PROJECT_DIRECTORY_SYS_PROP, projectDirectory.toString());
    System.setProperty(USER_HOME_SYS_PROP, homeDirectory.toString());

    configFile = homeDirectory.resolve(".mvn").resolve(CONFIG_FILE_NAME);
    projectConfigFile = projectDirectory.resolve(".mvn").resolve(CONFIG_FILE_NAME);

    logger = new CapturingLogger();
    propertiesProvider = new PropertiesProvider(logger);
  }

  @AfterEach
  public void tearDown() {
    System.getProperties().remove(FILTER_PLUGINS_SYS_PROP);
    System.getProperties().remove(FILTER_INFO_SYS_PROP);
    System.getProperties().remove(FILTER_GENERATORS_SYS_PROP);
    if (originalMultiModuleProjectDirectory == null) {
      System.getProperties().remove(MULTI_MODULE_PROJECT_DIRECTORY_SYS_PROP);
    } else {
      System.setProperty(
          MULTI_MODULE_PROJECT_DIRECTORY_SYS_PROP, originalMultiModuleProjectDirectory);
    }
    if (originalUserHome == null) {
      System.getProperties().remove(USER_HOME_SYS_PROP);
    } else {
      System.setProperty(USER_HOME_SYS_PROP, originalUserHome);
    }
  }

  @Test
  public void createsConfigFileWithDefaultsOnFirstUse() {
    assertThat(configFile).doesNotExist();

    assertThat(propertiesProvider.getPluginDescriptors())
        .isEqualTo(PropertiesProvider.DEFAULT_FILTERED_PLUGIN_DESCRIPTORS);

    assertThat(configFile).exists();
    assertThat(logger.infoMessages).anyMatch(message -> message.contains(CONFIG_FILE_NAME));
  }

  @Test
  public void reusesAnAlreadyExistingConfigFile() throws IOException {
    Files.createDirectories(configFile.getParent());
    Files.writeString(
        configFile, "filterPlugins=maven-checkstyle-plugin:org.apache.maven.plugins\n");

    assertThat(propertiesProvider.getPluginDescriptors())
        .containsExactly("maven-checkstyle-plugin:org.apache.maven.plugins");
  }

  /**
   * A project can still commit its own {@code .mvn/filterPlugins.properties} to share a filter
   * list with the whole team, same as before the extension started defaulting to a single
   * per-user {@code ~/.mvn/filterPlugins.properties} - it just has to already exist, since a
   * project with no config file of its own falls back to (and creates) the per-user one instead
   * of silently creating a new project-level file nobody asked for.
   */
  @Test
  public void projectLevelConfigFileOverridesTheHomeDirectoryOne() throws IOException {
    Files.createDirectories(projectConfigFile.getParent());
    Files.writeString(
        projectConfigFile, "filterPlugins=maven-pmd-plugin:org.apache.maven.plugins\n");

    assertThat(propertiesProvider.getPluginDescriptors())
        .containsExactly("maven-pmd-plugin:org.apache.maven.plugins");
    assertThat(configFile).doesNotExist();
  }

  @Test
  public void supportsCommentsAndLineContinuationInConfigFile() throws IOException {
    Files.createDirectories(configFile.getParent());
    Files.writeString(
        configFile,
        "# a hand-edited comment above the property, like the generated header\n"
            + "filterPlugins=maven-checkstyle-plugin:org.apache.maven.plugins,\\\n"
            + "  maven-pmd-plugin:org.apache.maven.plugins\n");

    assertThat(propertiesProvider.getPluginDescriptors())
        .containsExactly(
            "maven-checkstyle-plugin:org.apache.maven.plugins",
            "maven-pmd-plugin:org.apache.maven.plugins");
  }

  @Test
  public void missingKeyInConfigFileDisablesFiltering() throws IOException {
    Files.createDirectories(configFile.getParent());
    Files.writeString(configFile, "# nothing configured here\n");

    assertThat(propertiesProvider.getPluginDescriptors()).isEmpty();
  }

  @Test
  public void blankValueInConfigFileDisablesFiltering() throws IOException {
    Files.createDirectories(configFile.getParent());
    Files.writeString(configFile, "filterPlugins=\n");

    assertThat(propertiesProvider.getPluginDescriptors()).isEmpty();
  }

  @Test
  public void defaultsIncludeArchUnitSortPomSourceAndJavadoc() {
    assertThat(propertiesProvider.getPluginDescriptors())
        .contains(
            "arch-unit-maven-plugin:com.societegenerale.commons",
            "sortpom-maven-plugin:com.github.ekryd.sortpom",
            "maven-source-plugin:org.apache.maven.plugins",
            "maven-javadoc-plugin:org.apache.maven.plugins");
  }

  @Test
  public void systemPropertyOverridesConfigFileWithoutTouchingIt() {
    System.setProperty(FILTER_PLUGINS_SYS_PROP, "maven-checkstyle-plugin, maven-pmd-plugin");

    assertThat(propertiesProvider.getPluginDescriptors())
        .containsExactly("maven-checkstyle-plugin", "maven-pmd-plugin");
    assertThat(configFile).doesNotExist();
  }

  @Test
  public void isDisabledWhenSystemPropertyIsBlank() {
    System.setProperty(FILTER_PLUGINS_SYS_PROP, "  ");

    assertThat(propertiesProvider.getPluginDescriptors()).isEmpty();
  }

  @Test
  public void filterInfoIsNotRequestedByDefault() {
    assertThat(propertiesProvider.isFilterInfoRequested()).isFalse();
  }

  @Test
  public void filterInfoIsRequestedWhenFlagIsBare() {
    System.setProperty(FILTER_INFO_SYS_PROP, "true");

    assertThat(propertiesProvider.isFilterInfoRequested()).isTrue();
  }

  @Test
  public void filterInfoIsNotRequestedWhenExplicitlyFalse() {
    System.setProperty(FILTER_INFO_SYS_PROP, "false");

    assertThat(propertiesProvider.isFilterInfoRequested()).isFalse();
  }

  @Test
  public void filterGeneratorsIsNotRequestedByDefault() {
    assertThat(propertiesProvider.isFilterGeneratorsRequested()).isFalse();
  }

  @Test
  public void filterGeneratorsIsRequestedWhenFlagIsBare() {
    System.setProperty(FILTER_GENERATORS_SYS_PROP, "true");

    assertThat(propertiesProvider.isFilterGeneratorsRequested()).isTrue();
  }

  /**
   * swagger-codegen-maven-plugin is published under at least two different groupIds ({@code
   * io.swagger.codegen.v3}, the maintained fork, and the older, largely abandoned {@code
   * io.swagger}) - a hardcoded groupId here previously matched only one of them, silently failing
   * to filter the other. Locks in matching by artifactId alone instead.
   */
  @Test
  public void swaggerCodegenDescriptorHasNoHardcodedGroupIdSoItMatchesEitherFork() {
    assertThat(PropertiesProvider.GENERATOR_PLUGIN_DESCRIPTORS)
        .contains("swagger-codegen-maven-plugin");
  }

  @Test
  public void generatorPluginsAreNotFilteredByDefault() {
    assertThat(propertiesProvider.getPluginDescriptors())
        .doesNotContainAnyElementsOf(PropertiesProvider.GENERATOR_PLUGIN_DESCRIPTORS);
  }

  @Test
  public void filterGeneratorsAppendsGeneratorPluginsOnTopOfTheDefaultList() {
    System.setProperty(FILTER_GENERATORS_SYS_PROP, "true");

    assertThat(propertiesProvider.getPluginDescriptors())
        .containsAll(PropertiesProvider.DEFAULT_FILTERED_PLUGIN_DESCRIPTORS)
        .containsAll(PropertiesProvider.GENERATOR_PLUGIN_DESCRIPTORS);
  }

  @Test
  public void filterGeneratorsAppendsGeneratorPluginsEvenWhenFilterPluginsIsBlank() {
    System.setProperty(FILTER_PLUGINS_SYS_PROP, " ");
    System.setProperty(FILTER_GENERATORS_SYS_PROP, "true");

    assertThat(propertiesProvider.getPluginDescriptors())
        .isEqualTo(PropertiesProvider.GENERATOR_PLUGIN_DESCRIPTORS);
  }

  @Test
  public void createsConfigFileWithGeneratorDefaultsOnFirstUseToo() throws IOException {
    assertThat(configFile).doesNotExist();
    System.setProperty(FILTER_GENERATORS_SYS_PROP, "true");

    propertiesProvider.getPluginDescriptors();

    var content = Files.readString(configFile);
    assertThat(content).contains(FILTER_GENERATORS_SYS_PROP + "=");
    for (var descriptor : PropertiesProvider.GENERATOR_PLUGIN_DESCRIPTORS) {
      assertThat(content).contains(descriptor);
    }
  }

  /**
   * The generator plugin list is meant to be as user-editable/extendable as {@code filterPlugins}
   * itself - a custom generator plugin added by hand must take effect, same as editing {@code
   * filterPlugins} already does.
   */
  @Test
  public void editingTheGeneratorsListInTheConfigFileIsHonored() throws IOException {
    Files.createDirectories(configFile.getParent());
    Files.writeString(
        configFile,
        "filterPlugins=\n" + FILTER_GENERATORS_SYS_PROP + "=my-custom-generator-plugin\n");
    System.setProperty(FILTER_GENERATORS_SYS_PROP, "true");

    assertThat(propertiesProvider.getPluginDescriptors())
        .containsExactly("my-custom-generator-plugin");
  }
}
