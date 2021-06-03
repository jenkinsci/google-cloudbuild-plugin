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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.google.api.services.cloudbuild.v1.model.Build;
import com.google.api.services.cloudbuild.v1.model.BuildOperationMetadata;
import com.google.api.services.cloudbuild.v1.model.Operation;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.Buckets;
import com.google.api.services.storage.model.StorageObject;
import com.google.common.io.Resources;
import hudson.model.Result;

/** Integration tests for {@link CloudBuildStep}. */
public class CloudBuildStepTest {
  private static final String RESOURCE_BASE =
      "/com/google/jenkins/plugins/cloudbuild/CloudBuildStepTest/";

  @Rule
  public JenkinsRule j = new JenkinsRule();

  private MockCloudServices cloud = new MockCloudServices();

  private WorkflowJob job;

  @Before
  public void setUp() throws Exception {
    cloud.prepare(j.jenkins);
    job = j.createProject(WorkflowJob.class, "test-workflow");
  }

  @Test
  public void pipeline1() throws Exception {
    String script = Resources.toString(
        getClass().getResource(RESOURCE_BASE + "pipeline1.groovy"),
        Charset.defaultCharset());
    job.setDefinition(new CpsFlowDefinition(script, false));

    job = j.configRoundtrip(job);

    cloud.onStartBuild((build, req, resp) -> {
      assertEquals(1, build.getSteps().size());
      assertEquals("ubuntu", build.getSteps().get(0).getName());
      assertThat(build.getSteps().get(0).getArgs(),
          Matchers.contains("echo", "$_MESSAGE"));
      assertEquals("Hello, World!", build.getSubstitutions().get("_MESSAGE"));
      assertEquals("bucket", build.getSource().getStorageSource().getBucket());
      assertEquals("object/path/source.tgz", build.getSource().getStorageSource().getObject());
      return new Operation()
          .setName("build-42")
          .setMetadata(new BuildOperationMetadata()
              .setBuild(build
                  .setId("42")
                  .setLogUrl("https://logurl")));
    });

    cloud.onCheckBuild((x, req, resp) -> {
      assertThat(req.getUrl(), containsString("/builds/42"));
      return new Build()
          .setId("42")
          .setStatus("SUCCESS")
          .setLogUrl("https://logurl");
    });

    WorkflowRun run = j.buildAndAssertSuccess(job);

    assertEquals("https://logurl", run.getAction(BuildLogAction.class).getUrlName());
    assertThat(run.getAction(StorageAction.class).getUrlName(),
        containsString("bucket/object/path"));
  }

  @Test
  public void pipeline2() throws Exception {
    String script = Resources.toString(
        getClass().getResource(RESOURCE_BASE + "pipeline2.groovy"),
        Charset.defaultCharset());
    job.setDefinition(new CpsFlowDefinition(script, false));

    job = j.configRoundtrip(job);

    cloud.onStartBuild((build, req, resp) -> {
      assertEquals(1, build.getSteps().size());
      assertEquals("alpine", build.getSteps().get(0).getName());
      assertEquals("some-other-project", build.getSource().getRepoSource().getProjectId());
      assertEquals("test-repo", build.getSource().getRepoSource().getRepoName());
      assertEquals("3.14.159", build.getSource().getRepoSource().getTagName());
      return new Operation()
          .setName("build-42")
          .setMetadata(new BuildOperationMetadata()
              .setBuild(build
                  .setId("42")
                  .setLogUrl("https://logurl")));
    });

    cloud.onCheckBuild((x, req, resp) -> {
      assertThat(req.getUrl(), containsString("/builds/42"));
      return new Build()
          .setId("42")
          .setStatus("SUCCESS")
          .setLogUrl("https://logurl");
    });

    WorkflowRun run = j.buildAndAssertSuccess(job);

    assertEquals("https://logurl", run.getAction(BuildLogAction.class).getUrlName());
    assertThat(run.getAction(RepoAction.class).getUrlName(),
        containsString("test-repo/3.14.159?project=some-other-project"));

    cloud.onCheckBuild((x, req, resp) -> {
      assertThat(req.getUrl(), containsString("/builds/42"));
      return new Build()
          .setId("42")
          .setStatus("FAILURE")
          .setLogUrl("https://logurl");
    });

    run = job.scheduleBuild2(0).get();
    j.assertBuildStatus(Result.FAILURE, run);

    assertEquals("https://logurl", run.getAction(BuildLogAction.class).getUrlName());
    assertThat(run.getAction(RepoAction.class).getUrlName(),
        containsString("test-repo/3.14.159?project=some-other-project"));
  }

  @Test
  public void pipeline3() throws Exception {
    String script = Resources.toString(
        getClass().getResource(RESOURCE_BASE + "pipeline3.groovy"),
        Charset.defaultCharset());
    job.setDefinition(new CpsFlowDefinition(script, false));

    job = j.configRoundtrip(job);

    cloud.onListBuckets((x, req, resp) ->
        new Buckets().setItems(Collections.singletonList(new Bucket()
            .setName("jenkins-tmp_foo"))));

    List<String> uploadedObjects = new ArrayList<>();
    cloud.onCreateObject((obj, req, resp) -> {
      uploadedObjects.add(obj.getName());
      return null;
    });

    cloud.onUploadObject((x, req, resp) -> new StorageObject().setId("42"));

    cloud.onStartBuild((build, req, resp) -> {
      assertEquals(1, build.getSteps().size());
      assertEquals("alpine", build.getSteps().get(0).getName());
      assertEquals("jenkins-tmp_foo", build.getSource().getStorageSource().getBucket());
      assertEquals(uploadedObjects.get(0), build.getSource().getStorageSource().getObject());
      return new Operation()
          .setName("build-42")
          .setMetadata(new BuildOperationMetadata()
              .setBuild(build
                  .setId("42")
                  .setLogUrl("https://logurl")));
    });

    cloud.onCheckBuild((x, req, resp) -> {
      assertThat(req.getUrl(), containsString("/builds/42"));
      return new Build()
          .setId("42")
          .setStatus("SUCCESS")
          .setLogUrl("https://logurl");
    });

    WorkflowRun run = j.buildAndAssertSuccess(job);

    assertEquals(1, uploadedObjects.size());
    assertThat(run.getAction(StorageAction.class).getUrlName(), containsString("jenkins-tmp_foo"));
    assertEquals("https://logurl", run.getAction(BuildLogAction.class).getUrlName());
  }

  @Test
  public void pipelineWithLog() throws Exception {
    String script = Resources.toString(
        getClass().getResource(RESOURCE_BASE + "pipelineWithLog.groovy"),
        Charset.defaultCharset());
    job.setDefinition(new CpsFlowDefinition(script, false));

    job = j.configRoundtrip(job);

    cloud.onStartBuild((build, req, resp) -> {
      return new Operation()
          .setName("build-42")
          .setMetadata(new BuildOperationMetadata()
              .setBuild(build
                  .setId("42")
                  .setLogUrl("https://logurl")
                  .setLogsBucket("gs://logbucket")));
    });

    cloud.onCheckBuild((x, req, resp) -> {
      return new Build()
          .setId("42")
          .setStatus("SUCCESS")
          .setLogUrl("https://logurl")
          .setLogsBucket("gs://logbucket");
    });

    cloud.onGetObjectMedia((x, req, resp) -> {
        assertThat(req.getUrl(), containsString("/b/logbucket/o/log-42.txt"));
        return "foobar\n";
    });

    WorkflowRun run = j.buildAndAssertSuccess(job);

    j.assertLogContains("foobar\n", run);
  }
}
