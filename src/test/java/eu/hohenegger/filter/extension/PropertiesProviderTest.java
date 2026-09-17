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

  @TempDir private Path projectDirectory;

  private String originalMultiModuleProjectDirectory;
  private CapturingLogger logger;
  private PropertiesProvider propertiesProvider;
  private Path configFile;

  @BeforeEach
  public void setUp() throws IOException {
    originalMultiModuleProjectDirectory =
        System.getProperty(MULTI_MODULE_PROJECT_DIRECTORY_SYS_PROP);
    System.setProperty(MULTI_MODULE_PROJECT_DIRECTORY_SYS_PROP, projectDirectory.toString());

    // .mvn/ always already exists in a real project by the time this extension runs, since
    // extensions.xml lives there too.
    Files.createDirectories(projectDirectory.resolve(".mvn"));
    configFile = projectDirectory.resolve(".mvn").resolve(CONFIG_FILE_NAME);

    logger = new CapturingLogger();
    propertiesProvider = new PropertiesProvider(logger);
  }

  @AfterEach
  public void tearDown() {
    System.getProperties().remove(FILTER_PLUGINS_SYS_PROP);
    System.getProperties().remove(FILTER_INFO_SYS_PROP);
    if (originalMultiModuleProjectDirectory == null) {
      System.getProperties().remove(MULTI_MODULE_PROJECT_DIRECTORY_SYS_PROP);
    } else {
      System.setProperty(
          MULTI_MODULE_PROJECT_DIRECTORY_SYS_PROP, originalMultiModuleProjectDirectory);
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
    Files.writeString(
        configFile, "filterPlugins=maven-checkstyle-plugin:org.apache.maven.plugins\n");

    assertThat(propertiesProvider.getPluginDescriptors())
        .containsExactly("maven-checkstyle-plugin:org.apache.maven.plugins");
  }

  @Test
  public void supportsCommentsAndLineContinuationInConfigFile() throws IOException {
    Files.writeString(
        configFile,
        """
        # a hand-edited comment above the property, like the generated header
        filterPlugins=maven-checkstyle-plugin:org.apache.maven.plugins,\\
          maven-pmd-plugin:org.apache.maven.plugins
        """);

    assertThat(propertiesProvider.getPluginDescriptors())
        .containsExactly(
            "maven-checkstyle-plugin:org.apache.maven.plugins",
            "maven-pmd-plugin:org.apache.maven.plugins");
  }

  @Test
  public void missingKeyInConfigFileDisablesFiltering() throws IOException {
    Files.writeString(configFile, "# nothing configured here\n");

    assertThat(propertiesProvider.getPluginDescriptors()).isEmpty();
  }

  @Test
  public void blankValueInConfigFileDisablesFiltering() throws IOException {
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
}
