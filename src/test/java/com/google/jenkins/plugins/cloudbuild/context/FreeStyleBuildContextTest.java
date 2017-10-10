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
package com.google.jenkins.plugins.cloudbuild.context;

import static org.junit.Assert.assertEquals;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

/** Tests for {@link BuildContext}. */
public class FreeStyleBuildContextTest {
  @Rule
  public JenkinsRule j = new JenkinsRule();

  @Before
  public void setUp() {
    EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
    EnvVars env = prop.getEnvVars();
    env.put("FOO", "bar");
    j.jenkins.getGlobalNodeProperties().add(prop);
  }

  @Test
  public void expand() throws Exception {
    FreeStyleProject project = j.createFreeStyleProject();

    project.getBuildersList().add(new TestBuilder() {
      @Override
      public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
          throws InterruptedException, IOException {
        BuildContext context = new FreeStyleBuildContext(build, listener);
        assertEquals("bar", context.expand("$FOO"));
        return true;
      }
    });

    j.buildAndAssertSuccess(project);
  }
}
