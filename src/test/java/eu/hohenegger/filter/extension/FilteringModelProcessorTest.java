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

import java.util.List;
import org.apache.maven.model.Build;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.junit.jupiter.api.Test;

public class FilteringModelProcessorTest {

  private static FilteringModelProcessor processorFor(String... pluginDescriptors) {
    return new FilteringModelProcessor(
        new PropertiesProvider() {
          @Override
          public List<String> getPluginDescriptors() {
            return List.of(pluginDescriptors);
          }
        });
  }

  private static Plugin plugin(String groupId, String artifactId) {
    Plugin plugin = new Plugin();
    plugin.setGroupId(groupId);
    plugin.setArtifactId(artifactId);
    return plugin;
  }

  @Test
  public void filtersConfiguredPluginByArtifactAndGroupId() {
    FilteringModelProcessor modelProcessor =
        processorFor("maven-checkstyle-plugin:org.apache.maven.plugins");

    Model model = new Model();
    model.setParent(new Parent());
    Build build = new Build();
    build.addPlugin(plugin("foo", "foo.bar"));
    build.addPlugin(plugin("foo", "bar.foo"));
    build.addPlugin(plugin("org.apache.maven.plugins", "maven-checkstyle-plugin"));
    model.setBuild(build);

    Model filteredModel = modelProcessor.filter(model);

    List<Plugin> plugins = filteredModel.getBuild().getPlugins();
    assertThat(plugins).hasSize(2);
    assertThat(plugins).extracting(Plugin::getGroupId).containsOnly("foo");
  }

  @Test
  public void filtersByArtifactIdAloneRegardlessOfGroupId() {
    FilteringModelProcessor modelProcessor = processorFor("maven-checkstyle-plugin");

    Model model = new Model();
    Build build = new Build();
    build.addPlugin(plugin("org.apache.maven.plugins", "maven-checkstyle-plugin"));
    model.setBuild(build);

    Model filteredModel = modelProcessor.filter(model);

    assertThat(filteredModel.getBuild().getPlugins()).isEmpty();
  }

  @Test
  public void filtersPluginDeclaredWithoutExplicitGroupIdAsMavenDefaultGroupId() {
    FilteringModelProcessor modelProcessor =
        processorFor("maven-checkstyle-plugin:org.apache.maven.plugins");

    Model model = new Model();
    Build build = new Build();
    build.addPlugin(plugin(null, "maven-checkstyle-plugin"));
    model.setBuild(build);

    Model filteredModel = modelProcessor.filter(model);

    assertThat(filteredModel.getBuild().getPlugins()).isEmpty();
  }

  @Test
  public void doesNotFilterWhenVersionDoesNotMatch() {
    FilteringModelProcessor modelProcessor =
        processorFor("maven-checkstyle-plugin:org.apache.maven.plugins:3.1.2");

    Model model = new Model();
    Build build = new Build();
    Plugin checkstyle = plugin("org.apache.maven.plugins", "maven-checkstyle-plugin");
    checkstyle.setVersion("3.0.0");
    build.addPlugin(checkstyle);
    model.setBuild(build);

    Model filteredModel = modelProcessor.filter(model);

    assertThat(filteredModel.getBuild().getPlugins()).hasSize(1);
  }

  @Test
  public void filtersPluginsDeclaredInsideProfiles() {
    FilteringModelProcessor modelProcessor =
        processorFor("maven-checkstyle-plugin:org.apache.maven.plugins");

    Model model = new Model();
    Build build = new Build();
    build.addPlugin(plugin("foo", "foo.bar"));
    model.setBuild(build);

    BuildBase profileBuild = new BuildBase();
    profileBuild.addPlugin(plugin("org.apache.maven.plugins", "maven-checkstyle-plugin"));
    Profile profile = new Profile();
    profile.setBuild(profileBuild);
    model.addProfile(profile);

    Model filteredModel = modelProcessor.filter(model);

    assertThat(filteredModel.getBuild().getPlugins()).hasSize(1);
    assertThat(filteredModel.getProfiles().get(0).getBuild().getPlugins()).isEmpty();
  }

  @Test
  public void doesNotTouchModelWhenNoPluginsAreConfigured() {
    FilteringModelProcessor modelProcessor = processorFor();

    Model model = new Model();
    Build build = new Build();
    build.addPlugin(plugin("org.apache.maven.plugins", "maven-checkstyle-plugin"));
    model.setBuild(build);

    Model filteredModel = modelProcessor.filter(model);

    assertThat(filteredModel.getBuild().getPlugins()).hasSize(1);
  }
}
