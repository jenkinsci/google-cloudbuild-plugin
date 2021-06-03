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

import java.io.IOException;

import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;

import com.google.api.services.cloudbuild.v1.model.Source;
import com.google.jenkins.plugins.cloudbuild.client.ClientFactory;
import com.google.jenkins.plugins.cloudbuild.client.CloudBuildClient;
import com.google.jenkins.plugins.cloudbuild.client.CloudStorageClient;
import com.google.jenkins.plugins.cloudbuild.client.LogUtil;
import com.google.jenkins.plugins.cloudbuild.context.BuildContext;
import com.google.jenkins.plugins.cloudbuild.context.FreeStyleBuildContext;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

/** A Jenkins builder that submits a build request to Google Cloud Build. */
public class CloudBuildBuilder extends Builder {
  @Nonnull
  private final CloudBuildInput input;

  @DataBoundConstructor
  public CloudBuildBuilder(@Nonnull CloudBuildInput input) {
    this.input = input;
  }

  @Nonnull
  public CloudBuildInput getInput() {
    return input;
  }

  @Override
  public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
      throws IOException, InterruptedException {
    BuildContext context = new FreeStyleBuildContext(build, listener);
    ClientFactory clients = new ClientFactory(build, listener, input.getCredentialsId());
    String finalRequest = input.getRequest().expand(context);
    Source buildSource = input.getSourceOrDefault().prepare(context, clients);
    CloudBuildClient cloudBuild = clients.cloudBuild();
    String buildId = cloudBuild.sendBuildRequest(
        finalRequest, buildSource, input.getSubstitutionMap(context));

    LogUtil.LogPoller poller = null;

    if (input.isStreamLog()) {
      LogUtil.LogLocation logLocation = cloudBuild.getGcsLogUrl(buildId);
      CloudStorageClient storage = clients.storage();
      poller = storage.createStreamContentPoller(logLocation);
    }

    cloudBuild.waitForSuccess(buildId, poller);
    return true;
  }

  /** Descriptor for {@link CloudBuildBuilder}. */
  @Extension
  public static class Descriptor extends BuildStepDescriptor<Builder> {
    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return true;
    }

    @Override @Nonnull
    public String getDisplayName() {
      return Messages.CloudBuildBuilder_DisplayName();
    }
  }
}
