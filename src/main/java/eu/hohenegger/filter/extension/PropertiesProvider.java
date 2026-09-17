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

import java.util.Arrays;
import java.util.List;
import javax.inject.Named;

@Named("PropertiesProvider")
public class PropertiesProvider {

  public static final String FILTER_PLUGINS_SYS_PROP = "filterPlugins";

  /**
   * Short, easy-to-remember opt-in flag: prints, once at the start of the build, which plugins were
   * actually filtered, the full configured list, and a short reminder of how to customize or
   * disable it (e.g. {@code -DfilterInfo}).
   */
  public static final String FILTER_INFO_SYS_PROP = "filterInfo";

  /**
   * Widely-used checker/reporting plugins that are removed from the build by default, so that a
   * plain install of this extension already speeds up local builds without any further
   * configuration.
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

  /**
   * The plugin descriptors to filter out of the build.
   *
   * <p>If the {@value #FILTER_PLUGINS_SYS_PROP} system property is not set at all, {@link
   * #DEFAULT_FILTERED_PLUGIN_DESCRIPTORS} is used. If it is set, its comma-separated content fully
   * replaces the default list; setting it to a blank value (e.g. {@code -DfilterPlugins=}) disables
   * filtering entirely, which is useful to restore the original, unfiltered build on a CI server.
   */
  public List<String> getPluginDescriptors() {
    if (!System.getProperties().containsKey(FILTER_PLUGINS_SYS_PROP)) {
      return DEFAULT_FILTERED_PLUGIN_DESCRIPTORS;
    }

    return Arrays.stream(System.getProperty(FILTER_PLUGINS_SYS_PROP, "").split(","))
        .map(String::trim)
        .filter(not(String::isEmpty))
        .toList();
  }

  /** Whether the {@value #FILTER_INFO_SYS_PROP} flag is set for this build. */
  public boolean isFilterInfoRequested() {
    return Boolean.parseBoolean(System.getProperty(FILTER_INFO_SYS_PROP, "false"));
  }
}
