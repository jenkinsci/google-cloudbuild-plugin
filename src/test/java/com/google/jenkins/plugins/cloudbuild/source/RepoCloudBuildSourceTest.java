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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.api.services.cloudbuild.v1.model.Source;
import com.google.jenkins.plugins.cloudbuild.CloudBuildBuilder;
import com.google.jenkins.plugins.cloudbuild.CloudBuildInput;
import com.google.jenkins.plugins.cloudbuild.MockCloudServices;
import com.google.jenkins.plugins.cloudbuild.context.BuildContext;
import com.google.jenkins.plugins.cloudbuild.request.InlineCloudBuildRequest;
import com.google.jenkins.plugins.cloudbuild.source.RepoCloudBuildSource.DescriptorImpl;
import com.google.jenkins.plugins.cloudbuild.source.RepoCloudBuildSource.RevisionType;
import hudson.EnvVars;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import hudson.util.FormValidation.Kind;

/** Tests for {@link RepoCloudBuildSource}. */
public class RepoCloudBuildSourceTest {
  @Rule
  public JenkinsRule j = new JenkinsRule();

  @Mock
  private BuildContext context;

  private final EnvVars env = new EnvVars();

  private final MockCloudServices cloud = new MockCloudServices();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(context.expand(any())).thenAnswer(invocation -> env.expand(invocation.getArgument(0)));
    when(context.getListener()).thenReturn(TaskListener.NULL);
  }

  /**
   * Creates a freestyle project containing the provided source, then saves that project using a
   * web client and reloads it. The reloaded source is then returned.
   * <p>
   * By sending the source through a save/reload cycle, we test everything involved in this, which
   * is non-trivial for {@link RepoCloudBuildSource}.
   *
   * @param source the {@link RepoCloudBuildSource} to save and reload.
   * @return the reloaded {@link RepoCloudBuildSource}
   * @throws Exception if an error occurs during the process of saving and reloading the temporary
   *     freestyle project containing {@code source}
   * @see DescriptorImpl#newInstance(org.kohsuke.stapler.StaplerRequest, net.sf.json.JSONObject)
   * @see RepoCloudBuildSource#getRevision()
   * @see RepoCloudBuildSource#getRevisionType()
   * @see <a href="https://wiki.jenkins.io/display/JENKINS/Unit+Test#UnitTest-Configurationround-triptesting">
   *        Configuration round-trip testing</a>
   */
  private RepoCloudBuildSource saveAndReload(RepoCloudBuildSource source) throws Exception {
    FreeStyleProject project = j.createFreeStyleProject();
    CloudBuildInput input = new CloudBuildInput("test-project", new InlineCloudBuildRequest(""));
    input.setSource(source);
    project.getBuildersList().add(new CloudBuildBuilder(input));
    project = j.configRoundtrip(project);
    CloudBuildBuilder afterReload = project.getBuildersList().get(CloudBuildBuilder.class);
    return (RepoCloudBuildSource) afterReload.getInput().getSource();
  }

  @Test
  public void prepare() throws Exception {
    cloud.prepare(j.jenkins);
    env.put("PROJECT_ID", "project");
    env.put("REPO_NAME", "repo");
    env.put("BRANCH", "branch");
    env.put("TAG", "tag");
    env.put("COMMIT", "0123456789abcdef0123456789ABCDEF01234567");

    RepoCloudBuildSource source = new RepoCloudBuildSource();
    source.setProjectId("$PROJECT_ID");
    source.setRepoName("$REPO_NAME");
    source.setBranch("$BRANCH");
    source = saveAndReload(source);
    Source apiSource = source.prepare(context, null);
    assertEquals("project", apiSource.getRepoSource().getProjectId());
    assertEquals("repo", apiSource.getRepoSource().getRepoName());
    assertEquals("branch", apiSource.getRepoSource().getBranchName());

    source = new RepoCloudBuildSource();
    source.setProjectId("$PROJECT_ID");
    source.setRepoName("$REPO_NAME");
    source.setTag("$TAG");
    source = saveAndReload(source);

    apiSource = source.prepare(context, null);
    assertEquals("project", apiSource.getRepoSource().getProjectId());
    assertEquals("repo", apiSource.getRepoSource().getRepoName());
    assertEquals("tag", apiSource.getRepoSource().getTagName());

    source = new RepoCloudBuildSource();
    source.setProjectId("$PROJECT_ID");
    source.setRepoName("$REPO_NAME");
    source.setCommit("$COMMIT");
    source = saveAndReload(source);

    apiSource = source.prepare(context, null);
    assertEquals("project", apiSource.getRepoSource().getProjectId());
    assertEquals("repo", apiSource.getRepoSource().getRepoName());
    assertEquals(
        "0123456789abcdef0123456789ABCDEF01234567", apiSource.getRepoSource().getCommitSha());
  }

  @Test
  @WithoutJenkins
  public void doCheckRevision() throws Exception {
    DescriptorImpl descriptor = new DescriptorImpl();
    assertEquals(Kind.ERROR, descriptor.doCheckRevision("", RevisionType.BRANCH.name()).kind);

    assertEquals(Kind.OK, descriptor.doCheckRevision("foo", RevisionType.BRANCH.name()).kind);
    assertEquals(Kind.OK, descriptor.doCheckRevision("foo", RevisionType.TAG.name()).kind);
    assertEquals(Kind.OK,
        descriptor.doCheckRevision(
            "0123456789abcdef0123456789ABCDEF01234567", RevisionType.COMMIT.name()).kind);

    // Commit must be exactly 40 characters
    assertEquals(Kind.ERROR,
        descriptor.doCheckRevision(
            "0123456789abcdef0123456789ABCDEF012345678", RevisionType.COMMIT.name()).kind);
    assertEquals(Kind.ERROR,
        descriptor.doCheckRevision(
            "0123456789abcdef0123456789ABCDEF0123456", RevisionType.COMMIT.name()).kind);

    // Commit may only contain hexadecimal digits.
    assertEquals(Kind.ERROR,
        descriptor.doCheckRevision(
            "0123456789abcdefg0123456789ABCDEFG012345", RevisionType.COMMIT.name()).kind);

    // Don't check commit if there is a variable substitution.
    assertEquals(Kind.OK, descriptor.doCheckRevision("$FOO", RevisionType.COMMIT.name()).kind);
  }
}