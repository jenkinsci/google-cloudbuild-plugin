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

/** Provides a link to a Google Cloud Storage directory. */
public class StorageAction implements Action {
  private final String bucket;
  private final String path;

  public StorageAction(String bucket, String path) {
    this.bucket = bucket;
    this.path = path;
  }

  @Override
  public String getIconFileName() {
    return "/plugin/google-cloudbuild/images/Cloud_Storage.svg";
  }

  @Override
  public String getDisplayName() {
    return "Google Cloud Storage";
  }

  @Override
  public String getUrlName() {
    String fullPath = path.isEmpty() ? bucket : String.format("%s/%s", bucket, path);
    return String.format("https://console.cloud.google.com/storage/browser/%s", fullPath);
  }
}
