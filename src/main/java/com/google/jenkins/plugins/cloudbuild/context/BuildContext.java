/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.jenkins.plugins.cloudbuild.context;

import java.io.IOException;

import javax.annotation.CheckForNull;

import hudson.FilePath;
import hudson.model.TaskListener;

/** The context of the currently running Jenkins build. */
public interface BuildContext {
  /**
   * Expands environment variable references in the provided string if the current build is for a
   * freestyle project. Otherwise, returns the given string unchanged. For pipeline jobs, we should
   * not apply variable expansion since Groovy already does this.
   *
   * @param s the string to apply variable expansion to
   * @return the value of {@code s} with variables expanded, if the current build is for a freestyle
   *     job, or {@code s} otherwise
   * @throws IOException if an I/O error occurs attempting to retrieve the necessary context to
   *     perform variable expansion
   * @throws InterruptedException if an interruption occurs attempting to retrieve the necessary
   *     context to perform variable expansion
   */
  String expand(String s) throws IOException, InterruptedException;

  @CheckForNull
  FilePath getWorkspace() throws IOException, InterruptedException;

  TaskListener getListener() throws IOException, InterruptedException;
}
