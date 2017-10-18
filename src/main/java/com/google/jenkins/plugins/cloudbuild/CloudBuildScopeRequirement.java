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

import java.util.Collection;
import java.util.Collections;

import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement;

/** Indicates OAuth2 scopes required to access Cloud Container Builder and Cloud Storage. */
public class CloudBuildScopeRequirement extends GoogleOAuth2ScopeRequirement {
  @Override
  public Collection<String> getScopes() {
    return Collections.singletonList("https://www.googleapis.com/auth/cloud-platform");
  }
}
