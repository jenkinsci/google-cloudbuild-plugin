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
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.Json;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.cloudbuild.v1.CloudBuild;
import com.google.api.services.cloudbuild.v1.model.Build;
import com.google.api.services.cloudbuild.v1.model.BuildOperationMetadata;
import com.google.api.services.cloudbuild.v1.model.Operation;
import com.google.jenkins.plugins.cloudbuild.BuildLogAction;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

/** Tests for {@link CloudBuildClient}. */
public class CloudBuildClientTest {
  @Rule
  public JenkinsRule j = new JenkinsRule();

  @Mock(answer = Answers.CALLS_REAL_METHODS)
  private MockHttpTransport transport;

  private JsonFactory json = new JacksonFactory();

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  /**
   * Gets a {@link CloudBuildClient} to test.
   *
   * @param run the current Jenkins build
   * @return a client to run tests against
   */
  private CloudBuildClient cloudBuild(Run run) {
    return new CloudBuildClient(
        new CloudBuild.Builder(transport, json, req -> {})
            .setRootUrl("https://cloudbuild.googleapis.com/")
            .setApplicationName("google-cloudbuild-plugin-test")
            .build(),
        "test-project", run, TaskListener.NULL);
  }

  @Test
  public void sendBuildRequest() throws Exception {
    when(transport.buildRequest(eq(HttpMethods.POST), contains("/v1/projects/test-project/builds")))
        .thenReturn(new MockLowLevelHttpRequest() {
          @Override
          public LowLevelHttpResponse execute() throws IOException {
            MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
            response.setStatusCode(HttpStatusCodes.STATUS_CODE_OK);
            response.setContentType(Json.MEDIA_TYPE);
            response.setContent(json.toString(new Operation()
                .setName("operations/test")
                .setMetadata(new BuildOperationMetadata()
                    .setBuild(new Build()
                        .setId("build-42")
                        .setLogUrl("https://logurl")))));
            return response;
          }
        });

    FreeStyleProject project = j.createFreeStyleProject();
    project.getBuildersList().add(new TestBuilder() {
      @Override
      public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
          throws InterruptedException, IOException {
        CloudBuildClient cloudBuild = cloudBuild(build);
        String request =
            "steps: \n" +
                "- name: ubuntu\n" +
                "  args: [echo, hello, world]";
        assertEquals(
            "build-42", cloudBuild.sendBuildRequest(request, null, Collections.emptyMap()));
        return true;
      }
    });

    FreeStyleBuild build = project.scheduleBuild2(0).get();
    assertTrue(build.getResult().isBetterOrEqualTo(Result.SUCCESS));
    assertEquals("https://logurl", build.getAction(BuildLogAction.class).getUrlName());
    verify(transport, times(1)).buildRequest(
        eq(HttpMethods.POST), contains("/v1/projects/test-project/builds"));
  }

  /**
   * Gets the status for a simulated Cloud Build operation.
   * <p>
   * If the current system time (in milliseconds) is earlier than {@code startTime}, the status will
   * be {@code "QUEUED"}, otherwise if the system time is earlier than {@code endTime}, the status
   * will be {@code "WORKING"}. If the current system time is later than {@code endTime}, the status
   * will be either {@code "SUCCESS"} or {@code "FAILURE"}, as requested.
   *
   * @param startTime the system time (in milliseconds) at which the simulated build should start
   * @param endTime the system time (in milliseconds) at which the simulated build should complete
   * @param success whether the simulated build should complete successfully
   * @return the status of the simulated build, according to the current system time
   * @see System#currentTimeMillis()
   * @see Build#getStatus()
   * @see <a href="https://cloud.google.com/cloud-build/docs/api/reference/rest/v1/projects.builds#status">
   *          Cloud Build - Resource: Build - Status</a>
   */
  private String getStatus(long startTime, long endTime, boolean success) {
    long time = System.currentTimeMillis();
    if (time < startTime) {
      return "QUEUED";
    } else if (time < endTime) {
      return "WORKING";
    }
    return success ? "SUCCESS" : "FAILURE";
  }

  /**
   * Runs a test against {@link CloudBuildClient#waitForSuccess(String)}, simulating a cloud build
   * operation that is queued and then is processing for a short period of time, after which the
   * operation either succeeds or fails. The corresponding result should be applied to the Jenkins
   * build.
   *
   * @param cloudBuildSucceeds indicates whether the Cloud Build operation should succeed or fail
   * @throws Exception if an error occurs while running the test
   */
  public void testWaitForSuccess(boolean cloudBuildSucceeds) throws Exception {
    long startTime = System.currentTimeMillis() + 2000;
    long endTime = startTime + 3000;
    clearInvocations(transport);
    when(transport.buildRequest(eq(HttpMethods.GET),
                                contains("/v1/projects/test-project/builds/build-42")))
        .thenReturn(new MockLowLevelHttpRequest() {
          @Override
          public LowLevelHttpResponse execute() throws IOException {
            MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
            response.setStatusCode(HttpStatusCodes.STATUS_CODE_OK);
            response.setContentType(Json.MEDIA_TYPE);
            response.setContent(json.toString(new Build()
                .setStatus(getStatus(startTime, endTime, cloudBuildSucceeds))
                .setStatusDetail("foo bar baz")
                .setLogUrl("https://logurl")));
            return response;
          }
        });

    FreeStyleProject project = j.createFreeStyleProject();
    project.getBuildersList().add(new TestBuilder() {
      @Override
      public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
          throws InterruptedException, IOException {
        CloudBuildClient cloudBuild = cloudBuild(build);
        cloudBuild.waitForSuccess("build-42");
        return true;
      }
    });

    FreeStyleBuild build = project.scheduleBuild2(0).get();
    assertEquals(cloudBuildSucceeds, build.getResult().isBetterOrEqualTo(Result.SUCCESS));
    verify(transport, atLeast(1)).buildRequest(
        eq(HttpMethods.GET), contains("/v1/projects/test-project/builds/build-42"));
  }

  @Test
  public void waitForSuccess_BuildSucceeds() throws Exception {
    testWaitForSuccess(true);
  }

  @Test
  public void waitForSuccess_BuildFails() throws Exception {
    testWaitForSuccess(false);
  }

  @Test
  public void gcsLogUrl() throws Exception {
    when(transport.buildRequest(eq(HttpMethods.GET),
                                contains("/v1/projects/test-project/builds/build-42")))
        .thenReturn(new MockLowLevelHttpRequest() {
          @Override
          public LowLevelHttpResponse execute() throws IOException {
            MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
            response.setStatusCode(HttpStatusCodes.STATUS_CODE_OK);
            response.setContentType(Json.MEDIA_TYPE);
            response.setContent(json.toString(new Build()
                .setId("build-42")
                .setLogsBucket("gs://1234567890.cloudbuild-logs.googleusercontent.com")));
            return response;
          }
        });

    FreeStyleProject project = j.createFreeStyleProject();
    FreeStyleBuild build = j.assertBuildStatusSuccess(project.scheduleBuild2(0));
    CloudBuildClient client = cloudBuild(build);
    LogUtil.LogLocation logLocation = client.getGcsLogUrl("build-42");
    assertEquals(
        "1234567890.cloudbuild-logs.googleusercontent.com",
        logLocation.getBucketName()
    );
    assertEquals(
        "log-build-42.txt",
        logLocation.getObjectName()
    );
  }
}
