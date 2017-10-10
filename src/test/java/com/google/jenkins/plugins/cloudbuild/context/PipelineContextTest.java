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
import static org.mockito.Mockito.when;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;
import java.io.IOException;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link BuildContext}. */
public class PipelineContextTest {
  @Rule
  public JenkinsRule j = new JenkinsRule();

  @Mock
  private StepContext stepContext;

  private final EnvVars env = new EnvVars();

  @Before
  public void setUp() throws IOException, InterruptedException {
    MockitoAnnotations.initMocks(this);
    when(stepContext.get(FilePath.class)).thenReturn(null);
    when(stepContext.get(EnvVars.class)).thenReturn(env);
    when(stepContext.get(TaskListener.class)).thenReturn(TaskListener.NULL);
  }

  @Test
  public void expand() throws Exception {
    env.put("FOO", "bar");
    BuildContext context = new PipelineBuildContext(stepContext);
    assertEquals("$FOO", context.expand("$FOO"));  // should not expand variables in pipeline
  }
}
