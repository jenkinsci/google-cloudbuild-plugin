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

import static org.junit.Assert.assertEquals;

import com.google.jenkins.plugins.cloudbuild.context.BuildContext;
import com.google.jenkins.plugins.cloudbuild.context.FreeStyleBuildContext;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.CreateFileBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

/** Tests for {@link FileCloudBuildRequest}. */
public class FileCloudBuildRequestTest {
  @Rule
  public JenkinsRule j = new JenkinsRule();

  @Test
  public void expand() throws Exception {
    EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
    EnvVars env = prop.getEnvVars();
    env.put("FOO", "bar");
    env.put("REQUEST_FILENAME", "cloudbuild.yaml");
    j.jenkins.getGlobalNodeProperties().add(prop);

    FreeStyleProject project = j.createFreeStyleProject();
    String request =
        "steps:\n" +
        "- name: ubuntu\n" +
        "  args: [echo, '$FOO']";
    project.getBuildersList().add(new CreateFileBuilder("cloudbuild.yaml", request));

    // Check that Jenkins substitutions are performed on the filename, that the corresponding file
    // is read from the workspace, and that Jenkins substitutions are *not* applied to the resulting
    // build request.
    FileCloudBuildRequest fileRequest = new FileCloudBuildRequest("$REQUEST_FILENAME");
    project.getBuildersList().add(new TestBuilder() {
      @Override
      public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
          throws InterruptedException, IOException {
        BuildContext context = new FreeStyleBuildContext(build, listener);
        assertEquals(request, fileRequest.expand(context));
        return true;
      }
    });

    j.buildAndAssertSuccess(project);
  }
}