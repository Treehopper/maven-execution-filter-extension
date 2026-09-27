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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;

/**
 * Removes configured plugins from every project's build, and prints/cancels the {@value
 * PropertiesProvider#FILTER_INFO_SYS_PROP} summary - both in {@link #afterProjectsRead}, which
 * Maven calls exactly once per build, after the entire reactor's {@link MavenProject}s have been
 * built (model read, parent inheritance applied, active profiles merged in, default lifecycle
 * plugin bindings injected), but before any of them actually starts executing.
 *
 * <p>This used to work by overriding {@code ModelProcessor} instead, intercepting each POM as
 * Maven read it off disk. That approach has a fundamental problem this one does not: Maven only
 * lets <em>one</em> {@code ModelProcessor} implementation win a single, unqualified lookup, so any
 * other core extension attempting the same trick - e.g. maven-git-versioning-extension, which
 * rewrites the project version - was in direct, order-dependent competition with this one for that
 * one slot. A {@link AbstractMavenLifecycleParticipant} has no such conflict: Maven invokes every
 * registered one, in whatever order, so this extension and any number of others can all act on the
 * same build without needing to know about each other. It also sidesteps a whole class of problems
 * that came with the old approach: no more guessing whether a given {@code read()} call was for an
 * actual project POM or an unrelated artifact-metadata one (only real projects ever reach {@link
 * MavenSession#getAllProjects()}), no more thread-local "print once" bookkeeping for Maven daemon
 * (mvnd) reuse (this method's local variables are naturally fresh on every call), and - a genuine
 * capability gain, not just a simplification - plugins bound purely through Maven's default
 * lifecycle mapping (e.g. {@code maven-surefire-plugin}) are already present in {@link
 * MavenProject#getBuild()} by this point and can be filtered too, unlike before.
 *
 * <p>Confirmed by hand: removing an unresolvable plugin here still prevents Maven from ever trying
 * to resolve/download it - the same "removed, not skipped" guarantee the old approach made - since
 * this runs before the reactor's per-project execution plan is computed, which is what actually
 * triggers plugin resolution.
 */
@Named
@Singleton
public class FilteringLifecycleParticipant extends AbstractMavenLifecycleParticipant {

  private static final String DEFAULT_PLUGIN_GROUP_ID = "org.apache.maven.plugins";

  private final Logger logger;
  private final PropertiesProvider propertiesProvider;

  @Inject
  public FilteringLifecycleParticipant(Logger logger, PropertiesProvider propertiesProvider) {
    this.logger = logger;
    this.propertiesProvider = propertiesProvider;
  }

  @Override
  public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
    var configuredDescriptors = propertiesProvider.getPluginDescriptors();
    var filteredPlugins = parseFilteredPlugins(configuredDescriptors);

    var removedPlugins = new ArrayList<Plugin>();
    var remainingPlugins = new ArrayList<Plugin>();
    if (!filteredPlugins.isEmpty()) {
      for (MavenProject project : session.getAllProjects()) {
        filterBuild(project.getBuild(), filteredPlugins, removedPlugins, remainingPlugins);
      }
    }

    if (propertiesProvider.isFilterInfoRequested()) {
      printSummaryAndCancel(configuredDescriptors, removedPlugins, remainingPlugins);
    }
  }

  private List<Plugin> parseFilteredPlugins(List<String> descriptors) {
    return descriptors.stream().map(this::loadPluginToBeFiltered).collect(Collectors.toList());
  }

  private Plugin loadPluginToBeFiltered(String pluginDescriptor) {
    var segments = pluginDescriptor.split(":");
    var plugin = new Plugin();
    plugin.setArtifactId(segments[0]);
    // Plugin's own no-arg constructor defaults groupId to "org.apache.maven.plugins" (see its
    // generated source) rather than leaving it null - harmless for a plugin actually being
    // filtered (matches() only ever reads a *filter descriptor's* groupId, never this one), but
    // it would silently defeat "no groupId means match any groupId" below if left as-is: a
    // descriptor with no groupId segment would end up looking like it specified
    // org.apache.maven.plugins after all, matching only that one groupId instead of any.
    plugin.setGroupId(segments.length > 1 ? segments[1] : null);
    if (segments.length > 2) {
      plugin.setVersion(segments[2]);
    }
    return plugin;
  }

  void filterBuild(
      BuildBase build, List<Plugin> filteredPlugins, List<Plugin> removed, List<Plugin> remaining) {
    if (build == null) {
      return;
    }
    var partitioned =
        build.getPlugins().stream()
            .collect(
                Collectors.partitioningBy(plugin -> isFilteredPlugin(plugin, filteredPlugins)));
    build.setPlugins(partitioned.get(false));
    removed.addAll(partitioned.get(true));
    remaining.addAll(partitioned.get(false));
  }

  boolean isFilteredPlugin(Plugin plugin, List<Plugin> filteredPlugins) {
    var ofilteredPlugin =
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
   * #DEFAULT_PLUGIN_GROUP_ID}, since by the time {@link #afterProjectsRead} runs, that default has
   * already been applied to the plugin being checked - this is only ever relevant for a filter
   * descriptor's own, separately-constructed {@link Plugin} object (see {@link
   * #loadPluginToBeFiltered}).
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

  /**
   * {@value PropertiesProvider#FILTER_INFO_SYS_PROP} is a dry run, not just a report printed
   * alongside an otherwise normal build: once the summary is printed, this throws to cancel the
   * build before any project actually executes, the standard way for a Maven lifecycle participant
   * to abort - so a script or CI job that runs {@code -DfilterInfo} by mistake gets a non-zero
   * exit code instead of quietly building anyway.
   */
  private void printSummaryAndCancel(
      List<String> configuredDescriptors, List<Plugin> removedPlugins, List<Plugin> remainingPlugins)
      throws MavenExecutionException {
    logger.info("");
    logger.info(
        String.format(
            "maven-execution-filter-extension (-D%s):", PropertiesProvider.FILTER_INFO_SYS_PROP));
    printPluginList("filtered from this build", describe(removedPlugins), "none");
    printPluginList("could still be filtered", describe(remainingPlugins), "none");
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
        // the version is deliberately omitted: filtering matches on artifactId/groupId only (see
        // #matches), never on version, so the same plugin pinned to a different version in each
        // module of a reactor is genuinely the same entry here, not several - showing the version
        // would only make that look like more plugins than there actually are
        .map(plugin -> String.format("%s:%s", plugin.getGroupId(), plugin.getArtifactId()))
        // the same plugin can be inherited by many modules from a common parent - dedupe for a
        // readable summary
        .distinct()
        .collect(Collectors.toList());
  }
}
