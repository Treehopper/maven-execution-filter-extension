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

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import org.apache.maven.building.Source;
import org.apache.maven.model.BuildBase;
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

  private static final String DEFAULT_PLUGIN_GROUP_ID = "org.apache.maven.plugins";

  @Requirement private Logger logger = new ConsoleLogger();

  private final List<Plugin> filteredPlugins;

  @Inject
  public FilteringModelProcessor(PropertiesProvider propertiesProvider) {
    filteredPlugins =
        propertiesProvider.getPluginDescriptors().stream()
            .map(this::loadPluginToBeFiltered)
            .collect(toList());
  }

  private Plugin loadPluginToBeFiltered(String pluginDescriptor) {
    List<String> segments = List.of(pluginDescriptor.split(":"));
    if (segments.isEmpty()) {
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
    return process(super.read(input, options), input.getName());
  }

  @Override
  public Model read(Reader input, Map<String, ?> options) throws IOException {
    return process(super.read(input, options), locationOf(options));
  }

  @Override
  public Model read(InputStream input, Map<String, ?> options) throws IOException {
    return process(super.read(input, options), locationOf(options));
  }

  private Model process(Model model, String location) throws IOException {
    if (isProjectPom(location)) {
      filter(model);
    }
    return model.clone();
  }

  private static String locationOf(Map<String, ?> options) {
    Object source = options == null ? null : options.get(ModelProcessor.SOURCE);
    return source instanceof Source ? ((Source) source).getLocation() : null;
  }

  /**
   * Distinguishes an actual project/parent POM (always named {@code pom.xml} on disk) from a POM
   * that Maven reads merely to resolve an artifact's metadata (dependency, plugin, extension, ...),
   * which is cached in the local repository under {@code <artifactId>-<version>.pom}. Without this
   * check, plugins would be "filtered" (and logged) out of unrelated third-party POMs read only for
   * dependency resolution, which has no effect on the actual build but produces confusing log noise
   * - exactly what this extension is meant to avoid. When the location cannot be determined,
   * filtering is applied, since that is the common case for an actual project POM read from disk.
   */
  private static boolean isProjectPom(String location) {
    if (location == null) {
      return true;
    }
    String normalized = location.replace('\\', '/');
    int lastSlash = normalized.lastIndexOf('/');
    String fileName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    return "pom.xml".equals(fileName);
  }

  synchronized Model filter(Model model) {
    if (filteredPlugins.isEmpty()) {
      return model;
    }

    logger.debug("filtering: " + model);

    filterBuild(model.getBuild());
    model.getProfiles().forEach(profile -> filterBuild(profile.getBuild()));

    return model;
  }

  private void filterBuild(BuildBase build) {
    if (build == null) {
      return;
    }
    build.setPlugins(
        build.getPlugins().stream().filter(not(this::isFilteredPlugin)).collect(toList()));
  }

  boolean isFilteredPlugin(Plugin plugin) {
    Optional<Plugin> ofilteredPlugin =
        filteredPlugins.stream()
            .filter(filteredPlugin -> matches(plugin, filteredPlugin))
            .findFirst();

    if (ofilteredPlugin.isPresent()) {
      logger.info(
          String.format(
              "Plugin [%s:%s:%s] filtered",
              plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion()));
    }

    return ofilteredPlugin.isPresent();
  }

  /**
   * A plugin matches a filter descriptor if the artifactId is equal, and the groupId/version are
   * either equal or left unspecified (null) in the filter descriptor. Plugin declarations omitting
   * the groupId (legal for core plugins) are compared as if they had Maven's default {@value
   * #DEFAULT_PLUGIN_GROUP_ID}, since that default is only applied later on, during model
   * inheritance/normalization.
   */
  private boolean matches(Plugin plugin, Plugin filteredPlugin) {
    if (!Objects.equals(plugin.getArtifactId(), filteredPlugin.getArtifactId())) {
      return false;
    }
    if (filteredPlugin.getGroupId() != null
        && !filteredPlugin.getGroupId().equals(effectiveGroupId(plugin))) {
      return false;
    }
    return filteredPlugin.getVersion() == null
        || filteredPlugin.getVersion().equals(plugin.getVersion());
  }

  private String effectiveGroupId(Plugin plugin) {
    return plugin.getGroupId() != null ? plugin.getGroupId() : DEFAULT_PLUGIN_GROUP_ID;
  }
}
