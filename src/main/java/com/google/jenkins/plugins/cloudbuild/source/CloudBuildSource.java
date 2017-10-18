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
package com.google.jenkins.plugins.cloudbuild.source;

import java.io.IOException;
import java.io.Serializable;

import com.google.api.services.cloudbuild.v1.model.Source;
import com.google.jenkins.plugins.cloudbuild.client.ClientFactory;
import com.google.jenkins.plugins.cloudbuild.context.BuildContext;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;

/**
 * The source to be used along with a Google Cloud Container Builder build request.
 *
 * @see <a href="https://cloud.google.com/container-builder/docs/concepts/build-requests#source_location">
 *        Build Requests - Source location</a>
 */
public abstract class CloudBuildSource extends AbstractDescribableImpl<CloudBuildSource> implements
    ExtensionPoint, Serializable {
  /**
   * Prepares the source and returns the {@link Source} object to be used for the build request.
   *
   * @param context the context of the currently running Jenkins build
   * @param clients the factory to use for accessing Google Cloud services
   * @return the {@link Source} to attach to the build request
   * @throws IOException if an I/O error occurs while preparing the source
   * @throws InterruptedException if an operation involving communicating with another Jenkins node
   *     is interrupted
   */
  public abstract Source prepare(BuildContext context, ClientFactory clients)
      throws IOException, InterruptedException;

  public static final CloudBuildSource NULL = new CloudBuildSource() {
    @Override
    public Source prepare(BuildContext context, ClientFactory clients)
        throws IOException, InterruptedException {
      return null;
    }
  };

  @Override
  public CloudBuildSourceDescriptor getDescriptor() {
    return (CloudBuildSourceDescriptor) super.getDescriptor();
  }
}
