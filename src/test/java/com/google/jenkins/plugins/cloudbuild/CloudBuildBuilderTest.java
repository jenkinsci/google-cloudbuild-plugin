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

import java.util.Arrays;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.google.api.services.cloudbuild.v1.model.Build;
import com.google.api.services.cloudbuild.v1.model.BuildOperationMetadata;
import com.google.api.services.cloudbuild.v1.model.Operation;
import com.google.jenkins.plugins.cloudbuild.request.FileCloudBuildRequest;
import com.google.jenkins.plugins.cloudbuild.request.InlineCloudBuildRequest;
import com.google.jenkins.plugins.cloudbuild.source.RepoCloudBuildSource;
import com.google.jenkins.plugins.cloudbuild.source.StorageCloudBuildSource;
import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;

/** Integration tests for {@link CloudBuildBuilder}. */
public class CloudBuildBuilderTest {
  @Rule
  public JenkinsRule j = new JenkinsRule();

  private MockCloudServices cloud = new MockCloudServices();

  private FreeStyleProject project;

  @Before
  public void setUp() throws Exception {
    cloud.prepare(j.jenkins);
    project = j.createFreeStyleProject("test-job");
  }

  @Test
  public void freeStyleProject1() throws Exception {
    if (Functions.isWindows()) {
      project.getBuildersList().add(new BatchFile(
          "(\n" +
          "echo.steps:\n" +
          "echo.- name: ubuntu\n" +
          "echo.  args: [echo, '$_MESSAGE', '$_JOB_NAME']\n" +
          ")>cloudbuild.yaml"));
    } else {
      project.getBuildersList().add(new Shell(
          "cat <<EOF >cloudbuild.yaml\n" +
          "steps:\n" +
          "- name: ubuntu\n" +
          "  args: [echo, '\\$_MESSAGE', '\\$_JOB_NAME']\n" +
          "EOF"));
    }

    CloudBuildInput input =
        new CloudBuildInput("test-project", new FileCloudBuildRequest("cloudbuild.yaml"));
    input.setSource(new StorageCloudBuildSource("bucket", "object/path/source.tgz"));
    input.setSubstitutionList(new SubstitutionList(Arrays.asList(
        new Substitution("_MESSAGE", "Hello, World!"),
        new Substitution("_JOB_NAME", "$JOB_NAME"))));
    project.getBuildersList().add(new CloudBuildBuilder(input));

    project = j.configRoundtrip(project);

    cloud.onStartBuild((build, req, resp) -> {
      assertEquals(1, build.getSteps().size());
      assertEquals("ubuntu", build.getSteps().get(0).getName());
      assertThat(build.getSteps().get(0).getArgs(),
          Matchers.contains("echo", "$_MESSAGE", "$_JOB_NAME"));
      assertEquals("Hello, World!", build.getSubstitutions().get("_MESSAGE"));
      assertEquals("test-job", build.getSubstitutions().get("_JOB_NAME"));
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

    FreeStyleBuild build = j.buildAndAssertSuccess(project);

    assertEquals("https://logurl", build.getAction(BuildLogAction.class).getUrlName());
    assertThat(build.getAction(StorageAction.class).getUrlName(),
        containsString("bucket/object/path"));
  }

  @Test
  public void freeStyleProject2() throws Exception {
    String requestYaml =
        "steps:\n" +
        "- name: alpine";
    CloudBuildInput input =
        new CloudBuildInput("test-project", new InlineCloudBuildRequest(requestYaml));
    RepoCloudBuildSource source = new RepoCloudBuildSource();
    source.setProjectId("some-other-project");
    source.setRepoName("test-repo");
    source.setBranch("release");
    input.setSource(source);
    project.getBuildersList().add(new CloudBuildBuilder(input));

    project = j.configRoundtrip(project);

    cloud.onStartBuild((build, req, resp) -> {
      assertEquals(1, build.getSteps().size());
      assertEquals("alpine", build.getSteps().get(0).getName());
      assertEquals("some-other-project", build.getSource().getRepoSource().getProjectId());
      assertEquals("test-repo", build.getSource().getRepoSource().getRepoName());
      assertEquals("release", build.getSource().getRepoSource().getBranchName());
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

    FreeStyleBuild build = j.buildAndAssertSuccess(project);

    assertEquals("https://logurl", build.getAction(BuildLogAction.class).getUrlName());
    assertThat(build.getAction(RepoAction.class).getUrlName(),
        containsString("test-repo/release?project=some-other-project"));

    cloud.onCheckBuild((x, req, resp) -> {
      assertThat(req.getUrl(), containsString("/builds/42"));
      return new Build()
          .setId("42")
          .setStatus("FAILURE")
          .setLogUrl("https://logurl");
    });

    build = project.scheduleBuild2(0).get();
    j.assertBuildStatus(Result.FAILURE, build);

    assertEquals("https://logurl", build.getAction(BuildLogAction.class).getUrlName());
    assertThat(build.getAction(RepoAction.class).getUrlName(),
        containsString("test-repo/release?project=some-other-project"));
  }

  @Test
  public void freeStyleProjectWithStreamLog() throws Exception {
    String requestYaml =
        "steps:\n" +
        "- name: alpine";
    CloudBuildInput input =
        new CloudBuildInput("test-project", new InlineCloudBuildRequest(requestYaml));
    RepoCloudBuildSource source = new RepoCloudBuildSource();
    source.setProjectId("some-other-project");
    source.setRepoName("test-repo");
    source.setBranch("release");
    input.setSource(source);
    input.setStreamLog(true);
    project.getBuildersList().add(new CloudBuildBuilder(input));

    project = j.configRoundtrip(project);

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

    FreeStyleBuild build = j.buildAndAssertSuccess(project);

    j.assertLogContains("foobar\n", build);
  }
}
