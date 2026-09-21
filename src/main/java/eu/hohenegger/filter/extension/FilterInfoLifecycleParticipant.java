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

import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.logging.Logger;

/**
 * Prints the {@value PropertiesProvider#FILTER_INFO_SYS_PROP} summary exactly once per build, using
 * totals accumulated by {@link FilteringModelProcessor} across every project's model read during
 * that build - not just the first one. That distinction matters for a multi-module (reactor) build:
 * {@link FilteringModelProcessor#filter(org.apache.maven.model.Model)} runs once per POM read
 * (parent aggregator plus every module), and printing from there directly would report only on
 * whichever POM happened to be read first - typically the parent aggregator, which usually declares
 * no plugins of its own - making the summary claim "none" even though a child module's plugin was
 * genuinely filtered moments later in the same build.
 *
 * <p>{@link #afterProjectsRead} fires exactly once per build, after Maven has read and resolved the
 * entire reactor, which is both the right point to report the totals and (as a side effect) a more
 * robust "once per build" guard than a thread-scoped flag: a Maven daemon (mvnd) that reuses this
 * component's singleton instance across builds still gets a fresh {@link MavenSession}, and
 * therefore a fresh call to {@link #afterSessionStart}/{@link #afterProjectsRead}, for each one.
 */
@Named
@Singleton
public class FilterInfoLifecycleParticipant extends AbstractMavenLifecycleParticipant {

  private final Logger logger;
  private final PropertiesProvider propertiesProvider;
  private final FilteringModelProcessor filteringModelProcessor;

  @Inject
  public FilterInfoLifecycleParticipant(
      Logger logger,
      PropertiesProvider propertiesProvider,
      FilteringModelProcessor filteringModelProcessor) {
    this.logger = logger;
    this.propertiesProvider = propertiesProvider;
    this.filteringModelProcessor = filteringModelProcessor;
  }

  @Override
  public void afterSessionStart(MavenSession session) {
    filteringModelProcessor.resetAccumulatedResults();
  }

  @Override
  public void afterProjectsRead(MavenSession session) {
    if (!propertiesProvider.isFilterInfoRequested()) {
      return;
    }
    var configuredDescriptors = propertiesProvider.getPluginDescriptors();
    logger.info("");
    logger.info(
        "maven-execution-filter-extension (-D%s):"
            .formatted(PropertiesProvider.FILTER_INFO_SYS_PROP));
    logger.info(
        infoLine(
            "filtered from this build",
            describe(filteringModelProcessor.accumulatedRemovedPlugins())));
    logger.info(
        infoLine(
            "could still be filtered",
            describe(filteringModelProcessor.accumulatedRemainingPlugins())));
    logger.info(
        infoLine(
            "configured to be filtered",
            configuredDescriptors.isEmpty()
                ? "none (disabled)"
                : String.join(", ", configuredDescriptors)));
    logger.info(
        infoLine(
            "customize the list",
            "-D%s=artifactId[:groupId[:version]][,...]"
                .formatted(PropertiesProvider.FILTER_PLUGINS_SYS_PROP)));
    logger.info(
        infoLine(
            "disable entirely", "-D%s=".formatted(PropertiesProvider.FILTER_PLUGINS_SYS_PROP)));
    logger.info("");
  }

  private static String infoLine(String label, String value) {
    return "  %-25s: %s".formatted(label, value);
  }

  private static String describe(List<Plugin> plugins) {
    if (plugins.isEmpty()) {
      return "none";
    }
    return plugins.stream()
        .map(
            plugin ->
                "%s:%s:%s"
                    .formatted(plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion()))
        .collect(Collectors.joining(", "));
  }
}
