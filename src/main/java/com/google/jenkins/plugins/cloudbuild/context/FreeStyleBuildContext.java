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
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.TaskListener;

/** The context of the currently running Jenkins build. */
public class FreeStyleBuildContext implements BuildContext {
  private final AbstractBuild<?, ?> build;
  private final BuildListener listener;
  @CheckForNull
  private final String proxy;

  public FreeStyleBuildContext(AbstractBuild build, BuildListener listener) {
      this(build, listener, null);
    }

  public FreeStyleBuildContext(AbstractBuild<?, ?> build,
      BuildListener listener, @CheckForNull String proxy) {
    this.build = build;
    this.listener = listener;
    this.proxy = proxy;
  }

  @Override
  public String expand(String s) throws IOException, InterruptedException {
    return build.getEnvironment(listener).expand(s);
  }

  @Override
  public FilePath getWorkspace() {
    return build.getWorkspace();
  }

  @Override
  public TaskListener getListener() {
    return listener;
  }

  @Override
  @CheckForNull
  public String getProxy() {
    return proxy;
  }
}
