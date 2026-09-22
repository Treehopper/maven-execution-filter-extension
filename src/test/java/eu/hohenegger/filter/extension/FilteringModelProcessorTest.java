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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import org.apache.maven.model.Build;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.apache.maven.model.building.ModelProcessor;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.junit.jupiter.api.Test;

public class FilteringModelProcessorTest {

  private static FilteringModelProcessor processorFor(String... pluginDescriptors) {
    return new FilteringModelProcessor(
        new ConsoleLogger(), propertiesProviderFor(false, pluginDescriptors));
  }

  private static Plugin plugin(String groupId, String artifactId) {
    var plugin = new Plugin();
    plugin.setGroupId(groupId);
    plugin.setArtifactId(artifactId);
    return plugin;
  }

  @Test
  public void filtersConfiguredPluginByArtifactAndGroupId() {
    var modelProcessor = processorFor("maven-checkstyle-plugin:org.apache.maven.plugins");

    var model = new Model();
    model.setParent(new Parent());
    var build = new Build();
    build.addPlugin(plugin("foo", "foo.bar"));
    build.addPlugin(plugin("foo", "bar.foo"));
    build.addPlugin(plugin("org.apache.maven.plugins", "maven-checkstyle-plugin"));
    model.setBuild(build);

    var filteredModel = modelProcessor.filter(model);

    var plugins = filteredModel.getBuild().getPlugins();
    assertThat(plugins).hasSize(2);
    assertThat(plugins).extracting(Plugin::getGroupId).containsOnly("foo");
  }

  @Test
  public void filtersByArtifactIdAloneRegardlessOfGroupId() {
    var modelProcessor = processorFor("maven-checkstyle-plugin");

    var model = new Model();
    var build = new Build();
    build.addPlugin(plugin("org.apache.maven.plugins", "maven-checkstyle-plugin"));
    model.setBuild(build);

    var filteredModel = modelProcessor.filter(model);

    assertThat(filteredModel.getBuild().getPlugins()).isEmpty();
  }

  /**
   * Regression test: {@link org.apache.maven.model.Plugin}'s own no-arg constructor defaults
   * groupId to "org.apache.maven.plugins" rather than leaving it null (see {@code
   * FilteringModelProcessor#loadPluginToBeFiltered}'s javadoc) - a plugin declared under any
   * *other* groupId is exactly the case that would have silently failed to match an artifactId-only
   * descriptor if that default had leaked through uncorrected. The other artifactId-only test above
   * uses org.apache.maven.plugins for the declared plugin too, which would not have caught this -
   * both sides of the comparison would happen to agree by accident.
   */
  @Test
  public void filtersByArtifactIdAloneEvenWhenGroupIdIsNotOrgApacheMavenPlugins() {
    var modelProcessor = processorFor("swagger-codegen-maven-plugin");

    var model = new Model();
    var build = new Build();
    build.addPlugin(plugin("io.swagger.codegen.v3", "swagger-codegen-maven-plugin"));
    model.setBuild(build);

    var filteredModel = modelProcessor.filter(model);

    assertThat(filteredModel.getBuild().getPlugins()).isEmpty();
  }

  @Test
  public void filtersPluginDeclaredWithoutExplicitGroupIdAsMavenDefaultGroupId() {
    var modelProcessor = processorFor("maven-checkstyle-plugin:org.apache.maven.plugins");

    var model = new Model();
    var build = new Build();
    build.addPlugin(plugin(null, "maven-checkstyle-plugin"));
    model.setBuild(build);

    var filteredModel = modelProcessor.filter(model);

    assertThat(filteredModel.getBuild().getPlugins()).isEmpty();
  }

  @Test
  public void doesNotFilterWhenVersionDoesNotMatch() {
    var modelProcessor = processorFor("maven-checkstyle-plugin:org.apache.maven.plugins:3.1.2");

    var model = new Model();
    var build = new Build();
    var checkstyle = plugin("org.apache.maven.plugins", "maven-checkstyle-plugin");
    checkstyle.setVersion("3.0.0");
    build.addPlugin(checkstyle);
    model.setBuild(build);

    var filteredModel = modelProcessor.filter(model);

    assertThat(filteredModel.getBuild().getPlugins()).hasSize(1);
  }

  @Test
  public void filtersPluginsDeclaredInsideProfiles() {
    var modelProcessor = processorFor("maven-checkstyle-plugin:org.apache.maven.plugins");

    var model = new Model();
    var build = new Build();
    build.addPlugin(plugin("foo", "foo.bar"));
    model.setBuild(build);

    var profileBuild = new BuildBase();
    profileBuild.addPlugin(plugin("org.apache.maven.plugins", "maven-checkstyle-plugin"));
    var profile = new Profile();
    profile.setBuild(profileBuild);
    model.addProfile(profile);

    var filteredModel = modelProcessor.filter(model);

    assertThat(filteredModel.getBuild().getPlugins()).hasSize(1);
    assertThat(filteredModel.getProfiles().get(0).getBuild().getPlugins()).isEmpty();
  }

  @Test
  public void doesNotTouchModelWhenNoPluginsAreConfigured() {
    var modelProcessor = processorFor();

    var model = new Model();
    var build = new Build();
    build.addPlugin(plugin("org.apache.maven.plugins", "maven-checkstyle-plugin"));
    model.setBuild(build);

    var filteredModel = modelProcessor.filter(model);

    assertThat(filteredModel.getBuild().getPlugins()).hasSize(1);
  }

  @Test
  public void accumulatesResultsAcrossMultipleFilterCallsUntilReset() {
    var modelProcessor = processorFor("maven-checkstyle-plugin:org.apache.maven.plugins");

    var parentModel = new Model();
    var parentBuild = new Build();
    parentBuild.addPlugin(plugin("org.apache.maven.plugins", "maven-surefire-plugin"));
    parentModel.setBuild(parentBuild);

    var moduleModel = new Model();
    var moduleBuild = new Build();
    moduleBuild.addPlugin(plugin("org.apache.maven.plugins", "maven-checkstyle-plugin"));
    moduleModel.setBuild(moduleBuild);

    modelProcessor.filter(parentModel);
    modelProcessor.filter(moduleModel);

    assertThat(modelProcessor.accumulatedRemovedPlugins())
        .extracting(Plugin::getArtifactId)
        .containsExactly("maven-checkstyle-plugin");
    assertThat(modelProcessor.accumulatedRemainingPlugins())
        .extracting(Plugin::getArtifactId)
        .containsExactly("maven-surefire-plugin");

    modelProcessor.resetAccumulatedResults();

    assertThat(modelProcessor.accumulatedRemovedPlugins()).isEmpty();
    assertThat(modelProcessor.accumulatedRemainingPlugins()).isEmpty();
  }

  @Test
  public void delegatesRawReadToAnotherRegisteredModelProcessorWhenPresent() throws IOException {
    var modelProcessor = processorFor("maven-checkstyle-plugin:org.apache.maven.plugins");

    var rawModelFromDelegate = new Model();
    var build = new Build();
    build.addPlugin(plugin("org.apache.maven.plugins", "maven-checkstyle-plugin"));
    build.addPlugin(plugin("org.apache.maven.plugins", "maven-surefire-plugin"));
    rawModelFromDelegate.setBuild(build);

    modelProcessor.setDelegate(List.of(modelProcessor, fakeModelProcessor(rawModelFromDelegate)));

    var result = modelProcessor.read(new File("pom.xml"), Map.of());

    assertThat(result.getBuild().getPlugins())
        .extracting(Plugin::getArtifactId)
        .containsExactly("maven-surefire-plugin");
  }

  private static ModelProcessor fakeModelProcessor(Model modelToReturn) {
    return new ModelProcessor() {
      @Override
      public File locatePom(File projectDirectory) {
        return new File(projectDirectory, "pom.xml");
      }

      @Override
      public Model read(File input, Map<String, ?> options) {
        return modelToReturn;
      }

      @Override
      public Model read(Reader input, Map<String, ?> options) {
        return modelToReturn;
      }

      @Override
      public Model read(InputStream input, Map<String, ?> options) {
        return modelToReturn;
      }
    };
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
