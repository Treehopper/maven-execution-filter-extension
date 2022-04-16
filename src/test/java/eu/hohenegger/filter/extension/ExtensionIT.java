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
import static com.soebes.itf.jupiter.extension.MavenCLIOptions.NO_TRANSFER_PROGRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.soebes.itf.jupiter.extension.MavenCLIOptions;
import com.soebes.itf.jupiter.extension.MavenJupiterExtension;
import com.soebes.itf.jupiter.extension.MavenOption;
import com.soebes.itf.jupiter.extension.MavenRepository;
import com.soebes.itf.jupiter.extension.MavenTest;
import com.soebes.itf.jupiter.maven.MavenExecutionResult;
import com.soebes.itf.jupiter.maven.MavenExecutionResult.ExecutionResult;
import org.junit.jupiter.api.DisplayName;

@MavenJupiterExtension
public class ExtensionIT {

  @MavenTest
  @MavenOption(NO_TRANSFER_PROGRESS)
  @MavenOption(MavenCLIOptions.DEBUG)
  @DisplayName("Running a basic test which makes sure the groupId/artifact of the plugin are ok.")
  void the_first_test_case(MavenExecutionResult result) {
    assertThat(result).out().debug().anyMatch(line -> line.matches(".*filtered.*"));
    assertEquals(ExecutionResult.Successful, result.getResult());
    // assertThat(result).build().isSuccessful();
  }
}
