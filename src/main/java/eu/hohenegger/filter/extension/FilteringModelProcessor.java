/*-
 * #%L
 * maven-execution-filter-extension
 * %%
 * Copyright (C) 2020-2021 Max Hohenegger <maven-execution-filter-extension@hohenegger.eu>
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

import static java.util.Comparator.comparing;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.ModelProcessor;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;

@Component(role = ModelProcessor.class, hint = "filtering-model-reader")
public class FilteringModelProcessor extends DefaultModelProcessor {

  @Requirement private Logger logger = new ConsoleLogger();

  private List<Plugin> filteredPlugins = Collections.emptyList();
  private Comparator<Plugin> comparePartially;

  @Inject
  public FilteringModelProcessor(PropertiesProvider propertiesProvider) {
    comparePartially =
        comparing(Plugin::getArtifactId)
            .thenComparing(Plugin::getGroupId, Comparator.nullsFirst(null));

    if (propertiesProvider.isConfigured()) {
      List<String> pluginDescriptors = propertiesProvider.getPluginDescriptors();
      if (pluginDescriptors.isEmpty()) {
        return;
      }
      filteredPlugins =
          pluginDescriptors.stream().map(this::loadPluginToBeFiltered).collect(toList());
    }
  }

  private Plugin loadPluginToBeFiltered(String pluginDescriptor) {
    List<String> segments = List.of(pluginDescriptor.split(":"));
    if (segments.size() < 1) {
      throw new RuntimeException(
          "pluginDescriptor must be of format: artifactId[:groupId[:version]]");
    }
    Plugin plugin = new Plugin();
    plugin.setArtifactId(segments.get(0));
    if (segments.size() > 1) {
      plugin.setGroupId(segments.get(1));
    }
    if (segments.size() > 2) {
      plugin.setVersion(segments.get(2));
    }
    return plugin;
  }

  @Override
  public Model read(File input, Map<String, ?> options) throws IOException {
    return filter(super.read(input, options)).clone();
  }

  @Override
  public Model read(Reader input, Map<String, ?> options) throws IOException {
    return filter(super.read(input, options)).clone();
  }

  @Override
  public Model read(InputStream input, Map<String, ?> options) throws IOException {
    return filter(super.read(input, options)).clone();
  }

  synchronized Model filter(Model model) {
    logger.debug("filtering: " + model);

    Build build = model.getBuild();
    if (build == null) {
      return model;
    }
    List<Plugin> plugins = build.getPlugins();
    build.setPlugins(plugins.stream().filter(not(this::isFilteredPlugin)).collect(toList()));

    return model;
  }

  boolean isFilteredPlugin(Plugin plugin) {
    Optional<Plugin> ofilteredPlugin =
        filteredPlugins.stream()
            .filter(filteredPlugin -> comparePartially.compare(plugin, filteredPlugin) == 0)
            .findFirst();

    if (ofilteredPlugin.isPresent()) {
      logger.info(
          String.format(
              "Plugin [%s:%s:%s] filtered",
              plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion()));
    }

    return ofilteredPlugin.isPresent();
  }
}
