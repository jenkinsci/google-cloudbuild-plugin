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
package com.google.jenkins.plugins.cloudbuild;

import hudson.model.Action;

/** Provides a link to the log for a Google Cloud Container Builder build request. */
public class BuildLogAction implements Action {
  private final String logUrl;

  public BuildLogAction(String logUrl) {
    this.logUrl = logUrl;
  }

  @Override
  public String getIconFileName() {
    return "/plugin/google-cloudbuild/images/Container_Registry.svg";
  }

  @Override
  public String getDisplayName() {
    return Messages.BuildLogAction_DisplayName();
  }

  @Override
  public String getUrlName() {
    return logUrl;
  }
}
