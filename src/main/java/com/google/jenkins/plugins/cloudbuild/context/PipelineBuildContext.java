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

import hudson.FilePath;
import hudson.model.TaskListener;
import java.io.IOException;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/** The context of the currently running Jenkins build. */
public class PipelineBuildContext implements BuildContext {
  private final StepContext stepContext;

  public PipelineBuildContext(StepContext stepContext) {
    this.stepContext = stepContext;
  }

  @Override
  public String expand(String s) {
    return s;  // We should not expand variables for pipeline builds.
  }

  @Override
  public FilePath getWorkspace() throws IOException, InterruptedException {
    return stepContext.get(FilePath.class);
  }

  @Override
  public TaskListener getListener() throws IOException, InterruptedException {
    return stepContext.get(TaskListener.class);
  }
}
