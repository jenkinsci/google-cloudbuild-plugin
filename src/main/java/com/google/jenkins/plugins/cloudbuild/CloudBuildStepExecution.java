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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import com.google.jenkins.plugins.cloudbuild.client.ClientFactory;
import com.google.jenkins.plugins.cloudbuild.client.CloudBuildClient;
import com.google.jenkins.plugins.cloudbuild.client.CloudStorageClient;
import com.google.jenkins.plugins.cloudbuild.client.LogUtil;
import com.google.jenkins.plugins.cloudbuild.context.BuildContext;
import com.google.jenkins.plugins.cloudbuild.context.PipelineBuildContext;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.DaemonThreadFactory;
import hudson.util.NamingThreadFactory;

/** Submits a build request to Google Cloud Build. */
public final class CloudBuildStepExecution extends StepExecution {
  private static final long serialVersionUID = 1L;

  private final CloudBuildInput input;
  private transient volatile Future<?> task;
  private String buildId;

  public CloudBuildStepExecution(CloudBuildInput input, StepContext context) {
    super(context);
    this.input = input;
  }

  /** API client for Google Cloud Platform. */
  private transient volatile ClientFactory clients;

  /** Returns the API client for Google Cloud Platform (recreating it if necessary). */
  private synchronized ClientFactory getClients() throws IOException, InterruptedException {
    if (clients == null) {
      clients = new ClientFactory(
          getContext().get(Run.class), getContext().get(TaskListener.class),
          input.getCredentialsId());
    }
    return clients;
  }

  /** The executor to be used for polling tasks. */
  private static volatile ExecutorService executorService;

  /**
   * Returns a shared thread-pool executor on which polling tasks will run. It will create the
   * executor if it has not already been created.
   */
  private static synchronized ExecutorService getExecutorService() {
    if (executorService == null) {
      executorService = Executors.newCachedThreadPool(
          new NamingThreadFactory(
              new DaemonThreadFactory(), CloudBuildStepExecution.class.getCanonicalName()));
    }
    return executorService;
  }

  /** Starts the thread to poll Google Cloud Build. */
  private void startPolling() {
    task = getExecutorService().submit(() -> {
      try {
        CloudBuildClient cloudBuild = getClients().cloudBuild();

        LogUtil.LogPoller poller = null;
        if (input.isStreamLog()) {
          LogUtil.LogLocation logLocation = cloudBuild.getGcsLogUrl(buildId);
          CloudStorageClient storage = clients.storage();
          poller = storage.createStreamContentPoller(logLocation);
        }

        cloudBuild.waitForSuccess(buildId, poller);
        getContext().onSuccess(null);
      } catch (Exception e) {
        getContext().onFailure(e);
      }
    });
  }

  @Override
  public boolean start() throws Exception {
    BuildContext context = new PipelineBuildContext(getContext());
    buildId = getClients().cloudBuild().sendBuildRequest(
        input.getRequest().expand(context),
        input.getSourceOrDefault().prepare(context, getClients()),
        input.getSubstitutionMap(context));
    startPolling();
    return false;
  }

  @Override
  public void stop(@Nonnull Throwable throwable) throws Exception {
    if (task != null) {
      task.cancel(true);
    }
  }

  @Override
  public void onResume() {
    startPolling();
  }
}
