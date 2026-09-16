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

import static eu.hohenegger.filter.extension.PropertiesProvider.FILTER_PLUGINS_SYS_PROP;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class PropertiesProviderTest {

  private final PropertiesProvider propertiesProvider = new PropertiesProvider();

  @AfterEach
  public void clearSystemProperty() {
    System.getProperties().remove(FILTER_PLUGINS_SYS_PROP);
  }

  @Test
  public void fallsBackToDefaultsWhenSystemPropertyIsAbsent() {
    assertThat(propertiesProvider.getPluginDescriptors())
        .isEqualTo(PropertiesProvider.DEFAULT_FILTERED_PLUGIN_DESCRIPTORS);
  }

  @Test
  public void usesConfiguredCommaSeparatedList() {
    System.setProperty(FILTER_PLUGINS_SYS_PROP, "maven-checkstyle-plugin, maven-pmd-plugin");

    assertThat(propertiesProvider.getPluginDescriptors())
        .containsExactly("maven-checkstyle-plugin", "maven-pmd-plugin");
  }

  @Test
  public void isDisabledWhenSystemPropertyIsBlank() {
    System.setProperty(FILTER_PLUGINS_SYS_PROP, "  ");

    assertThat(propertiesProvider.getPluginDescriptors()).isEmpty();
  }
}
