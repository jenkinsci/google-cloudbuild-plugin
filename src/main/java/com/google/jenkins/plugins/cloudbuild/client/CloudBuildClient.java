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
package com.google.jenkins.plugins.cloudbuild.client;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudbuild.v1.CloudBuild;
import com.google.api.services.cloudbuild.v1.model.Build;
import com.google.api.services.cloudbuild.v1.model.BuildOperationMetadata;
import com.google.api.services.cloudbuild.v1.model.Operation;
import com.google.api.services.cloudbuild.v1.model.RepoSource;
import com.google.api.services.cloudbuild.v1.model.Source;
import com.google.api.services.cloudbuild.v1.model.StorageSource;
import com.google.common.base.Strings;
import com.google.jenkins.plugins.cloudbuild.BuildLogAction;
import com.google.jenkins.plugins.cloudbuild.RepoAction;
import com.google.jenkins.plugins.cloudbuild.RequestProcessor;
import com.google.jenkins.plugins.cloudbuild.StorageAction;
import hudson.AbortException;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * Client for communicating with Google Cloud Build API.
 *
 * @see <a href="https://cloud.google.com/cloud-build/">Cloud Build</a>
 */
public class CloudBuildClient {
  private final CloudBuild cloudBuild;
  private final String projectId;
  private final Run<?, ?> run;
  private final PrintStream logger;

  CloudBuildClient(CloudBuild cloudBuild, String projectId, Run<?, ?> run, TaskListener listener) {
    this.cloudBuild = cloudBuild;
    this.projectId = projectId;
    this.run = run;
    this.logger = listener.getLogger();
  }

  /**
   * Sends a build request to Cloud Build.
   *
   * @param request the YAML or JSON request to send
   * @param source the {@link Source} to use for the build request
   * @param substitutions the custom substitutions to apply
   * @return the ID of the newly-submitted build
   * @throws IOException if an I/O error occurs in processing the request
   * @see <a href="https://cloud.google.com/cloud-build/docs/concepts/build-requests">
   *        Cloud Build - Build Requests</a>
   */
  public String sendBuildRequest(String request, Source source, Map<String, String> substitutions)
      throws IOException {
    logger.println(Messages.CloudBuildClient_StartingBuildRequest());
    logger.println(request);
    logger.println(Messages.CloudBuildClient_ProjectId(projectId));

    if (projectId == null) {
      throw new AbortException(Messages.CloudBuildClient_ProjectIdRequired());
    }
    Build buildRequest = RequestProcessor.parseBuildRequest(request)
        .setSource(source)
        .setSubstitutions(substitutions);
    addSourceActions(source);

    Operation operation = cloudBuild.projects().builds().create(projectId, buildRequest).execute();
    logger.println(Messages.CloudBuildClient_Operation(operation));

    JsonFactory jsonFactory = new JacksonFactory();
    BuildOperationMetadata metadata =
        jsonFactory.fromString(
            jsonFactory.toString(operation.getMetadata()), BuildOperationMetadata.class);
    run.addAction(new BuildLogAction(metadata.getBuild().getLogUrl()));
    return metadata.getBuild().getId();
  }

  @Nonnull
  public LogUtil.LogLocation getGcsLogUrl(@Nonnull String buildId) throws IOException {
    Build build = cloudBuild.projects().builds().get(projectId, buildId).execute();
    // https://cloud.google.com/cloud-build/docs/api/reference/rest/v1/projects.builds?#Build.FIELDS.logs_bucket
    try {
      return new LogUtil.LogLocation(String.format(
          "%s/log-%s.txt",
          build.getLogsBucket(),
          build.getId()
      ));
    } catch (IllegalArgumentException e) {
      throw new IOException("Failed to retrieve the log url", e);
    }
  }

  /**
   * Waits for the cloud build operation to complete successfully or throws an exception if the
   * operation fails.
   *
   * @param buildId the ID of the build to wait for
   * @throws InterruptedException if polling was interrupted
   * @throws IOException if an I/O error occurs while polling for build completion
   */
  public void waitForSuccess(String buildId) throws InterruptedException, IOException {
    waitForSuccess(buildId, null);
  }

  /**
   * Waits for the cloud build operation to complete successfully or throws an exception if the
   * operation fails.
   *
   * @param buildId the ID of the build to wait for
   * @param poller interface to trigger poll and output logs
   * @throws InterruptedException if polling was interrupted
   * @throws IOException if an I/O error occurs while polling for build completion
   * @since 0.3
   */
  public void waitForSuccess(String buildId, @CheckForNull LogUtil.LogPoller poller)
      throws InterruptedException, IOException {
    // Wait for the build to complete
    while (true) {
      Build buildCheck = cloudBuild.projects().builds().get(projectId, buildId).execute();
      String status = buildCheck.getStatus();

      if (poller != null) {
        poller.poll();
      } else {
        logger.println(Messages.CloudBuildClient_CurrentBuildStatus(status));
      }
      if (status.equals("QUEUED") || status.equals("WORKING")) {
        // Continue iterating
        TimeUnit.SECONDS.sleep(1);
        continue;
      }

      if (status.equals("SUCCESS")) {
        break;
      }

      logger.println(Messages.CloudBuildClient_BuildFailedWithStatus(status));
      logger.println(" -> " + buildCheck.getStatusDetail());
      logger.println(Messages.CloudBuildClient_LogUrl(buildCheck.getLogUrl()));
      throw new AbortException(Messages.CloudBuildClient_BuildFailed());
    }

    logger.println(Messages.CloudBuildClient_BuildSucceeded());
    logger.println(Messages.CloudBuildClient_BuildId(buildId));
  }

  /**
   * Add actions linking to the source used for the current Jenkins build.
   *
   * @param source the {@link Source} attached to the current Cloud Build request
   */
  private void addSourceActions(Source source) {
    if (source == null) {
      return;
    }
    RepoSource rs = source.getRepoSource();
    if (rs != null) {
      String repoProjectId = rs.getProjectId();
      if (Strings.isNullOrEmpty(repoProjectId)) {
        repoProjectId = projectId;
      }
      String repoName = rs.getRepoName();
      if (Strings.isNullOrEmpty(repoName)) {
        repoName = "default";
      }
      run.addAction(new RepoAction(repoProjectId, repoName, getRepoSourceRevision(rs)));
    }

    StorageSource ss = source.getStorageSource();
    if (ss != null) {
      run.addAction(new StorageAction(ss.getBucket(), getParentDirectory(ss.getObject())));
    }
  }

  /**
   * Get the parent of the provided {@code path}.
   *
   * @param path the path for which to get the parent directory
   * @return the parent of {@code path}
   */
  private static String getParentDirectory(String path) {
    int pos = path.lastIndexOf('/');
    return pos < 0 ? "" : path.substring(0, pos);
  }

  /**
   * Get the revision (branch, tag, or commit) referred to by the provided {@code RepoSource}.
   *
   * @param rs the {@link RepoSource} attached to a Cloud Build request
   * @return the branch, tag, or commit referred to by {@code rs}, or the empty string if neither
   *     the branch, tag, nor commit are set
   */
  private static String getRepoSourceRevision(RepoSource rs) {
    String revision = rs.getBranchName();
    if (!Strings.isNullOrEmpty(revision)) {
      return revision;
    }
    revision = rs.getTagName();
    if (!Strings.isNullOrEmpty(revision)) {
      return revision;
    }
    revision = rs.getCommitSha();
    if (!Strings.isNullOrEmpty(revision)) {
      return revision;
    }
    return "";
  }
}
