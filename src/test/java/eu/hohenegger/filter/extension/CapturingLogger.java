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

import java.util.ArrayList;
import java.util.List;
import org.codehaus.plexus.logging.Logger;

/**
 * Hand-written test double rather than a mock or {@code ConsoleLogger} subclass: {@code
 * ConsoleLogger} (the only stock implementation) is {@code final}, so it can't be subclassed to
 * capture what's logged.
 */
final class CapturingLogger implements Logger {

  final List<String> infoMessages = new ArrayList<>();
  final List<String> warnMessages = new ArrayList<>();

  @Override
  public void info(String message) {
    infoMessages.add(message);
  }

  @Override
  public void info(String message, Throwable throwable) {
    infoMessages.add(message);
  }

  @Override
  public boolean isInfoEnabled() {
    return true;
  }

  @Override
  public void debug(String message) {}

  @Override
  public void debug(String message, Throwable throwable) {}

  @Override
  public boolean isDebugEnabled() {
    return true;
  }

  @Override
  public void warn(String message) {
    warnMessages.add(message);
  }

  @Override
  public void warn(String message, Throwable throwable) {
    warnMessages.add(message);
  }

  @Override
  public boolean isWarnEnabled() {
    return true;
  }

  @Override
  public void error(String message) {}

  @Override
  public void error(String message, Throwable throwable) {}

  @Override
  public boolean isErrorEnabled() {
    return true;
  }

  @Override
  public void fatalError(String message) {}

  @Override
  public void fatalError(String message, Throwable throwable) {}

  @Override
  public boolean isFatalErrorEnabled() {
    return true;
  }

  @Override
  public int getThreshold() {
    return Logger.LEVEL_DEBUG;
  }

  @Override
  public void setThreshold(int threshold) {}

  @Override
  public Logger getChildLogger(String name) {
    return this;
  }

  @Override
  public String getName() {
    return "capturing";
  }
}
