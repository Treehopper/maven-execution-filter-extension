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
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FilteringModelProcessorTest {

  private FilteringModelProcessor modelProcessor;

  @BeforeEach
  public void setUp() {
    modelProcessor = new FilteringModelProcessor();
  }

  @Test
  public void testFilter() {
    Model model = new Model();
    model.setParent(new Parent());
    Build build = new Build();
    Plugin pluginA = new Plugin();
    pluginA.setArtifactId("foo.bar");
    pluginA.setGroupId("foo");
    Plugin pluginB = new Plugin();
    pluginB.setArtifactId("bar.foo");
    pluginB.setGroupId("foo");
    Plugin pluginC = new Plugin();
    pluginC.setArtifactId("maven-checkstyle-plugin");
    pluginC.setGroupId("org.apache.maven.plugins");
    build.addPlugin(pluginA);
    build.addPlugin(pluginB);
    build.addPlugin(pluginC);
    model.setBuild(build);
    Model filteredModel = modelProcessor.filter(model);

    List<Plugin> plugins = filteredModel.getBuild().getPlugins();
    assertThat(plugins).hasSize(2);

    assertThat(plugins).extracting(Plugin::getGroupId).anyMatch(value -> value.matches("foo"));
  }
}
