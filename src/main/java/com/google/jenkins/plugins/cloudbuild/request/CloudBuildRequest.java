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
package com.google.jenkins.plugins.cloudbuild.request;

import com.google.jenkins.plugins.cloudbuild.context.BuildContext;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import java.io.IOException;
import java.io.Serializable;

/** Generates the request to submit to Google Cloud Container Builder. */
public abstract class CloudBuildRequest extends AbstractDescribableImpl<CloudBuildRequest>
    implements ExtensionPoint, Serializable {
  public abstract String expand(BuildContext context) throws IOException, InterruptedException;

  @Override
  public CloudBuildRequestDescriptor getDescriptor() {
    return (CloudBuildRequestDescriptor) super.getDescriptor();
  }
}