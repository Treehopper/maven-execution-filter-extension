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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.junit.jupiter.api.Test;

public class FilterInfoLifecycleParticipantTest {

  private static Plugin plugin(String groupId, String artifactId) {
    var plugin = new Plugin();
    plugin.setGroupId(groupId);
    plugin.setArtifactId(artifactId);
    return plugin;
  }

  private static PropertiesProvider propertiesProviderFor(
      boolean filterInfoRequested, String... pluginDescriptors) {
    return new PropertiesProvider(new ConsoleLogger()) {
      @Override
      public List<String> getPluginDescriptors() {
        return List.of(pluginDescriptors);
      }

      @Override
      public boolean isFilterInfoRequested() {
        return filterInfoRequested;
      }
    };
  }

  @Test
  public void doesNotPrintInfoOrCancelTheBuildWhenNotRequested() throws Exception {
    var logger = new CapturingLogger();
    var propertiesProvider = propertiesProviderFor(false, "maven-checkstyle-plugin");
    var modelProcessor = new FilteringModelProcessor(logger, propertiesProvider);
    var participant =
        new FilterInfoLifecycleParticipant(logger, propertiesProvider, modelProcessor);

    var model = new Model();
    var build = new Build();
    build.addPlugin(plugin("org.apache.maven.plugins", "maven-checkstyle-plugin"));
    model.setBuild(build);

    participant.afterSessionStart(null);
    modelProcessor.filter(model);
    participant.afterProjectsRead(null);

    assertThat(logger.infoMessages)
        .noneMatch(message -> message.contains("maven-execution-filter-extension"));
  }

  @Test
  public void cancelsTheBuildAfterPrintingWithAnExplanatoryMessage() {
    var logger = new CapturingLogger();
    var propertiesProvider = propertiesProviderFor(true, "maven-checkstyle-plugin");
    var modelProcessor = new FilteringModelProcessor(logger, propertiesProvider);
    var participant =
        new FilterInfoLifecycleParticipant(logger, propertiesProvider, modelProcessor);

    participant.afterSessionStart(null);

    assertThatThrownBy(() -> participant.afterProjectsRead(null))
        .isInstanceOf(MavenExecutionException.class)
        .hasMessageContaining("-DfilterInfo")
        .hasMessageContaining("does not run the build");
  }

  @Test
  public void printsAccumulatedResultsOnceAfterAllProjectsAreRead() {
    var logger = new CapturingLogger();
    var propertiesProvider = propertiesProviderFor(true, "maven-checkstyle-plugin");
    var modelProcessor = new FilteringModelProcessor(logger, propertiesProvider);
    var participant =
        new FilterInfoLifecycleParticipant(logger, propertiesProvider, modelProcessor);

    // Simulates a multi-module reactor: a parent aggregator model with nothing to filter, read
    // before a module model that actually has a matching plugin - the summary must still report
    // the module's result, not just whatever the first-read (parent) model contributed.
    var parentModel = new Model();
    var parentBuild = new Build();
    parentBuild.addPlugin(plugin("org.apache.maven.plugins", "maven-surefire-plugin"));
    parentModel.setBuild(parentBuild);

    var moduleModel = new Model();
    var moduleBuild = new Build();
    moduleBuild.addPlugin(plugin("org.apache.maven.plugins", "maven-checkstyle-plugin"));
    moduleModel.setBuild(moduleBuild);

    participant.afterSessionStart(null);
    modelProcessor.filter(parentModel);
    modelProcessor.filter(moduleModel);
    assertThatThrownBy(() -> participant.afterProjectsRead(null))
        .isInstanceOf(MavenExecutionException.class);

    assertThat(logger.infoMessages)
        .filteredOn(message -> message.contains("maven-execution-filter-extension"))
        .hasSize(1);
    assertThat(logger.infoMessages)
        .anyMatch(
            message ->
                message.contains("filtered from this build")
                    && message.contains("org.apache.maven.plugins:maven-checkstyle-plugin")
                    && !message.contains("maven-surefire-plugin"));
    assertThat(logger.infoMessages)
        .anyMatch(
            message ->
                message.contains("could still be filtered")
                    && message.contains("org.apache.maven.plugins:maven-surefire-plugin")
                    && !message.contains("maven-checkstyle-plugin"));
    assertThat(logger.infoMessages)
        .anyMatch(
            message ->
                message.contains("configured to be filtered")
                    && message.contains("maven-checkstyle-plugin"));
    assertThat(logger.infoMessages).anyMatch(message -> message.contains("-DfilterPlugins="));
  }

  @Test
  public void afterSessionStartResetsResultsFromAPreviousBuild() {
    var logger = new CapturingLogger();
    var propertiesProvider = propertiesProviderFor(true, "maven-checkstyle-plugin");
    var modelProcessor = new FilteringModelProcessor(logger, propertiesProvider);
    var participant =
        new FilterInfoLifecycleParticipant(logger, propertiesProvider, modelProcessor);

    var model = new Model();
    var build = new Build();
    build.addPlugin(plugin("org.apache.maven.plugins", "maven-checkstyle-plugin"));
    model.setBuild(build);

    // First build: something is filtered.
    participant.afterSessionStart(null);
    modelProcessor.filter(model);
    assertThatThrownBy(() -> participant.afterProjectsRead(null))
        .isInstanceOf(MavenExecutionException.class);

    // Second build (e.g. a later mvnd-served build reusing the same singleton component):
    // nothing to filter this time, so the summary must not still claim the previous build's
    // result.
    participant.afterSessionStart(null);
    assertThatThrownBy(() -> participant.afterProjectsRead(null))
        .isInstanceOf(MavenExecutionException.class);

    var secondBuildSummary =
        logger.infoMessages.stream()
            .filter(message -> message.contains("filtered from this build"))
            .reduce((first, second) -> second)
            .orElseThrow();
    assertThat(secondBuildSummary).contains("filtered from this build : none");
  }
}
