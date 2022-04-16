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
import static org.slf4j.LoggerFactory.getLogger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.ModelProcessor;
import org.codehaus.plexus.component.annotations.Component;
import org.slf4j.Logger;

@Component(role = ModelProcessor.class)
public class FilteringModelProcessor extends DefaultModelProcessor {

  private final Logger logger = getLogger(FilteringModelProcessor.class);

  private List<Plugin> filteredPlugins;
  private Comparator<Plugin> comparePartially;

  public FilteringModelProcessor() {
    comparePartially = comparing(Plugin::getGroupId).thenComparing(Plugin::getArtifactId);

    filteredPlugins = loadPluginsToBeFiltered();
  }

  private List<Plugin> loadPluginsToBeFiltered() {
    // TODO: load these from file
    Plugin cs = new Plugin();
    cs.setArtifactId("maven-checkstyle-plugin");
    cs.setGroupId("org.apache.maven.plugins");

    Plugin pmd = new Plugin();
    pmd.setArtifactId("maven-pmd-plugin");
    pmd.setGroupId("org.apache.maven.plugins");

    Plugin sb = new Plugin();
    sb.setArtifactId("spotbugs-maven-plugin");
    sb.setGroupId("com.github.spotbugs");

    Plugin lp = new Plugin();
    lp.setArtifactId("license-maven-plugin");
    lp.setGroupId("org.codehaus.mojo");

    return List.of(cs, pmd, sb, lp);
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
    boolean toBeFiltered;
    toBeFiltered =
        filteredPlugins.stream()
            .filter(filteredPlugin -> comparePartially.compare(plugin, filteredPlugin) == 0)
            .findFirst()
            .isPresent();

    if (toBeFiltered) {
      logger.info(plugin + " filtered");
    }

    return toBeFiltered;
  }
}
