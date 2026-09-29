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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.junit.jupiter.api.Test;

public class FilteringLifecycleParticipantTest {

  private static FilteringLifecycleParticipant participantFor(String... pluginDescriptors) {
    return new FilteringLifecycleParticipant(
        new ConsoleLogger(), propertiesProviderFor(false, pluginDescriptors));
  }

  private static Plugin plugin(String groupId, String artifactId) {
    var plugin = new Plugin();
    plugin.setGroupId(groupId);
    plugin.setArtifactId(artifactId);
    return plugin;
  }

  private static MavenProject projectWith(Plugin... plugins) {
    var build = new Build();
    for (var plugin : plugins) {
      build.addPlugin(plugin);
    }
    var model = new Model();
    model.setBuild(build);
    return new MavenProject(model);
  }

  private static MavenSession sessionWith(MavenProject... projects) {
    var session = mock(MavenSession.class);
    when(session.getAllProjects()).thenReturn(List.of(projects));
    return session;
  }

  @Test
  public void filtersConfiguredPluginByArtifactAndGroupId() throws MavenExecutionException {
    var participant = participantFor("maven-checkstyle-plugin:org.apache.maven.plugins");
    var project =
        projectWith(
            plugin("foo", "foo.bar"),
            plugin("foo", "bar.foo"),
            plugin("org.apache.maven.plugins", "maven-checkstyle-plugin"));

    participant.afterProjectsRead(sessionWith(project));

    var plugins = project.getBuild().getPlugins();
    assertThat(plugins).hasSize(2);
    assertThat(plugins).extracting(Plugin::getGroupId).containsOnly("foo");
  }

  @Test
  public void filtersByArtifactIdAloneRegardlessOfGroupId() throws MavenExecutionException {
    var participant = participantFor("maven-checkstyle-plugin");
    var project = projectWith(plugin("org.apache.maven.plugins", "maven-checkstyle-plugin"));

    participant.afterProjectsRead(sessionWith(project));

    assertThat(project.getBuild().getPlugins()).isEmpty();
  }

  /**
   * Regression test: {@link Plugin}'s own no-arg constructor defaults groupId to
   * "org.apache.maven.plugins" rather than leaving it null (see {@code
   * FilteringLifecycleParticipant#loadPluginToBeFiltered}'s javadoc) - a plugin declared under any
   * *other* groupId is exactly the case that would have silently failed to match an artifactId-only
   * descriptor if that default had leaked through uncorrected. The other artifactId-only test above
   * uses org.apache.maven.plugins for the declared plugin too, which would not have caught this -
   * both sides of the comparison would happen to agree by accident.
   */
  @Test
  public void filtersByArtifactIdAloneEvenWhenGroupIdIsNotOrgApacheMavenPlugins()
      throws MavenExecutionException {
    var participant = participantFor("swagger-codegen-maven-plugin");
    var project = projectWith(plugin("io.swagger.codegen.v3", "swagger-codegen-maven-plugin"));

    participant.afterProjectsRead(sessionWith(project));

    assertThat(project.getBuild().getPlugins()).isEmpty();
  }

  @Test
  public void doesNotFilterWhenVersionDoesNotMatch() throws MavenExecutionException {
    var participant = participantFor("maven-checkstyle-plugin:org.apache.maven.plugins:3.1.2");
    var checkstyle = plugin("org.apache.maven.plugins", "maven-checkstyle-plugin");
    checkstyle.setVersion("3.0.0");
    var project = projectWith(checkstyle);

    participant.afterProjectsRead(sessionWith(project));

    assertThat(project.getBuild().getPlugins()).hasSize(1);
  }

  @Test
  public void doesNotTouchBuildsWhenNoPluginsAreConfigured() throws MavenExecutionException {
    var participant = participantFor();
    var project = projectWith(plugin("org.apache.maven.plugins", "maven-checkstyle-plugin"));

    participant.afterProjectsRead(sessionWith(project));

    assertThat(project.getBuild().getPlugins()).hasSize(1);
  }

  @Test
  public void filtersEveryProjectInTheReactorInOneCall() throws MavenExecutionException {
    var participant = participantFor("maven-checkstyle-plugin:org.apache.maven.plugins");
    // Simulates a multi-module reactor: a parent aggregator with nothing to filter, alongside a
    // module that actually has a matching plugin - both are read in the same afterProjectsRead
    // call and must each be filtered independently.
    var parent = projectWith(plugin("org.apache.maven.plugins", "maven-surefire-plugin"));
    var module = projectWith(plugin("org.apache.maven.plugins", "maven-checkstyle-plugin"));

    participant.afterProjectsRead(sessionWith(parent, module));

    assertThat(parent.getBuild().getPlugins()).extracting(Plugin::getArtifactId)
        .containsExactly("maven-surefire-plugin");
    assertThat(module.getBuild().getPlugins()).isEmpty();
  }

  /**
   * Filtering matches on artifactId/groupId only, never on version (see {@link
   * FilteringLifecycleParticipant#isFilteredPlugin}), so the same plugin inherited by several
   * modules of a reactor - each possibly pinned to its own version - is genuinely one filtered
   * plugin, not several: it must be logged exactly once, and without a version that wouldn't mean
   * anything for the whole reactor anyway.
   */
  @Test
  public void logsEachFilteredPluginExactlyOnceAcrossTheWholeReactorAndWithoutVersion()
      throws MavenExecutionException {
    var logger = new CapturingLogger();
    var participant =
        new FilteringLifecycleParticipant(
            logger, propertiesProviderFor(false, "maven-checkstyle-plugin:org.apache.maven.plugins"));
    var checkstyleInModuleOne = plugin("org.apache.maven.plugins", "maven-checkstyle-plugin");
    checkstyleInModuleOne.setVersion("3.1.2");
    var checkstyleInModuleTwo = plugin("org.apache.maven.plugins", "maven-checkstyle-plugin");
    checkstyleInModuleTwo.setVersion("3.0.0");

    participant.afterProjectsRead(
        sessionWith(projectWith(checkstyleInModuleOne), projectWith(checkstyleInModuleTwo)));

    assertThat(logger.infoMessages)
        .containsOnlyOnce("Plugin [org.apache.maven.plugins:maven-checkstyle-plugin] filtered");
  }

  @Test
  public void doesNotCancelTheBuildWhenFilterInfoIsNotRequested() throws MavenExecutionException {
    var participant =
        new FilteringLifecycleParticipant(
            new ConsoleLogger(), propertiesProviderFor(false, "maven-checkstyle-plugin"));

    participant.afterProjectsRead(
        sessionWith(projectWith(plugin("org.apache.maven.plugins", "maven-checkstyle-plugin"))));
    // no exception - the build is allowed to proceed
  }

  @Test
  public void cancelsTheBuildAfterPrintingWithAnExplanatoryMessage() {
    var logger = new CapturingLogger();
    var participant =
        new FilteringLifecycleParticipant(
            logger, propertiesProviderFor(true, "maven-checkstyle-plugin"));

    assertThatThrownBy(() -> participant.afterProjectsRead(sessionWith()))
        .isInstanceOf(MavenExecutionException.class)
        .hasMessageContaining("-DfilterInfo")
        .hasMessageContaining("does not run the build");
  }

  @Test
  public void printsResultsAcrossAllProjectsInTheSession() {
    var logger = new CapturingLogger();
    var participant =
        new FilteringLifecycleParticipant(
            logger, propertiesProviderFor(true, "maven-checkstyle-plugin"));
    var parent = projectWith(plugin("org.apache.maven.plugins", "maven-surefire-plugin"));
    var module = projectWith(plugin("org.apache.maven.plugins", "maven-checkstyle-plugin"));

    assertThatThrownBy(() -> participant.afterProjectsRead(sessionWith(parent, module)))
        .isInstanceOf(MavenExecutionException.class);

    assertThat(logger.infoMessages)
        .filteredOn(message -> message.contains("maven-execution-filter-extension"))
        .hasSize(1);
    assertThat(logger.infoMessages)
        .containsSubsequence(
            "  filtered from this build:", "    org.apache.maven.plugins:maven-checkstyle-plugin");
    assertThat(logger.infoMessages)
        .containsSubsequence(
            "  could still be filtered:", "    org.apache.maven.plugins:maven-surefire-plugin");
    assertThat(logger.infoMessages)
        .containsSubsequence("  configured to be filtered:", "    maven-checkstyle-plugin");
    assertThat(logger.infoMessages).anyMatch(message -> message.contains("-DfilterPlugins="));
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
}
