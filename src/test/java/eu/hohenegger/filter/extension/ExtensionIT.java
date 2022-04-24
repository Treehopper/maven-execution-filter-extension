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

import static com.soebes.itf.extension.assertj.MavenITAssertions.assertThat;
import static com.soebes.itf.jupiter.extension.MavenCLIOptions.*;
import static eu.hohenegger.filter.extension.PropertiesProvider.FILTER_PLUGINS_SYS_PROP;

import com.soebes.itf.jupiter.extension.MavenJupiterExtension;
import com.soebes.itf.jupiter.extension.MavenOption;
import com.soebes.itf.jupiter.extension.MavenTest;
import com.soebes.itf.jupiter.extension.SystemProperty;
import com.soebes.itf.jupiter.maven.MavenExecutionResult;

@MavenJupiterExtension
public class ExtensionIT {

  @MavenTest
  @MavenOption(NO_TRANSFER_PROGRESS)
  @MavenOption(ERRORS)
  void no_config(MavenExecutionResult result) {
    assertThat(result).isFailure();
    assertThat(result)
        .out()
        .info()
        .contains("--- maven-checkstyle-plugin:3.1.2:check (default) @ bar ---");
  }

  @MavenTest
  @MavenOption(NO_TRANSFER_PROGRESS)
  @SystemProperty(value = FILTER_PLUGINS_SYS_PROP, content = "maven-checkstyle-plugin")
  void artifactid_only(MavenExecutionResult result) {
    assertThat(result).isSuccessful();
    assertThat(result)
        .out()
        .info()
        .contains("Plugin [org.apache.maven.plugins:maven-checkstyle-plugin:3.1.2] filtered");
  }

  @MavenTest
  @MavenOption(NO_TRANSFER_PROGRESS)
  @SystemProperty(
      value = FILTER_PLUGINS_SYS_PROP,
      content = "maven-checkstyle-plugin:org.apache.maven.plugins")
  void artifactid_groupid(MavenExecutionResult result) {
    assertThat(result).isSuccessful();
    assertThat(result)
        .out()
        .info()
        .contains("Plugin [org.apache.maven.plugins:maven-checkstyle-plugin:3.1.2] filtered");
  }

  @MavenTest
  @MavenOption(NO_TRANSFER_PROGRESS)
  void jvm_config(MavenExecutionResult result) {
    assertThat(result).isSuccessful();
    assertThat(result)
        .out()
        .info()
        .contains("Plugin [org.apache.maven.plugins:maven-checkstyle-plugin:3.1.2] filtered")
        .contains("Plugin [org.apache.maven.plugins:maven-pmd-plugin:2.4] filtered");
  }
}
