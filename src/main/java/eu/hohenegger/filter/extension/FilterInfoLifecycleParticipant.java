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
import org.apache.maven.MavenExecutionException;
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
 *
 * <p>{@value PropertiesProvider#FILTER_INFO_SYS_PROP} is a dry run, not just a report printed
 * alongside an otherwise normal build: once the summary is printed, {@link #afterProjectsRead}
 * throws to cancel the build before any project actually executes, the standard way for a Maven
 * lifecycle participant to abort - so a script or CI job that runs {@code -DfilterInfo} by mistake
 * gets a non-zero exit code instead of quietly building anyway.
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
  public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
    if (!propertiesProvider.isFilterInfoRequested()) {
      return;
    }
    var configuredDescriptors = propertiesProvider.getPluginDescriptors();
    logger.info("");
    logger.info(
        String.format(
            "maven-execution-filter-extension (-D%s):", PropertiesProvider.FILTER_INFO_SYS_PROP));
    printPluginList(
        "filtered from this build",
        describe(filteringModelProcessor.accumulatedRemovedPlugins()),
        "none");
    printPluginList(
        "could still be filtered",
        describe(filteringModelProcessor.accumulatedRemainingPlugins()),
        "none");
    printPluginList("configured to be filtered", configuredDescriptors, "none (disabled)");
    logger.info(
        infoLine(
            "customize the list",
            String.format(
                "-D%s=artifactId[:groupId[:version]][,...]",
                PropertiesProvider.FILTER_PLUGINS_SYS_PROP)));
    logger.info(
        infoLine(
            "disable entirely",
            String.format("-D%s=", PropertiesProvider.FILTER_PLUGINS_SYS_PROP)));
    logger.info("");
    throw new MavenExecutionException(
        String.format(
            "Build cancelled: -D%s only prints this summary, it does not run the build - remove"
                + " it to build normally.",
            PropertiesProvider.FILTER_INFO_SYS_PROP),
        (Throwable) null);
  }

  private static String infoLine(String label, String value) {
    return String.format("  %-25s: %s", label, value);
  }

  /**
   * An empty list prints {@code emptyText} inline with the label, same as {@link #infoLine}; a
   * non-empty one instead prints the label as its own header line, followed by one plugin per
   * indented line below - one long comma-separated line becomes unreadable once there is more than
   * a handful of plugins.
   */
  private void printPluginList(String label, List<String> descriptors, String emptyText) {
    if (descriptors.isEmpty()) {
      logger.info(infoLine(label, emptyText));
      return;
    }
    logger.info(String.format("  %s:", label));
    descriptors.forEach(descriptor -> logger.info("    " + descriptor));
  }

  private static List<String> describe(List<Plugin> plugins) {
    return plugins.stream()
        // the version is deliberately omitted: filtering matches on artifactId/groupId only
        // (see FilteringModelProcessor#matches), never on version, so the same plugin pinned to
        // a different version in each module of a reactor is genuinely the same entry here, not
        // several - showing the version would only make that look like more plugins than there
        // actually are
        .map(plugin -> String.format("%s:%s", plugin.getGroupId(), plugin.getArtifactId()))
        // a plugin's own POM is commonly read more than once per build (see
        // FilteringModelProcessor's javadoc), and a delegate ModelProcessor from another core
        // extension (e.g. one that injects a plugin of its own into the build, such as
        // maven-git-versioning-extension) multiplies that further - dedupe for a readable summary
        .distinct()
        .collect(Collectors.toList());
  }
}
