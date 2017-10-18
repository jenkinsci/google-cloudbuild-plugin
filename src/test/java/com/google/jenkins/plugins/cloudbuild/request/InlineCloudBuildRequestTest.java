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

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.jenkins.plugins.cloudbuild.context.BuildContext;
import hudson.EnvVars;
import hudson.model.TaskListener;

/** Tests for {@link InlineCloudBuildRequest}. */
public class InlineCloudBuildRequestTest {
  @Rule
  public JenkinsRule j = new JenkinsRule();

  @Mock
  private BuildContext context;

  private final EnvVars env = new EnvVars();

  @Before
  public void setUp() throws IOException, InterruptedException {
    MockitoAnnotations.initMocks(this);
    when(context.expand(any())).thenAnswer(invocation -> env.expand(invocation.getArgument(0)));
    when(context.getListener()).thenReturn(TaskListener.NULL);
  }

  @Test
  public void expand() throws Exception {
    assertEquals("steps:", new InlineCloudBuildRequest("steps:").expand(context));

    // Inline request should not expand request document.
    String requestWithVariableRef =
        "steps:\n" +
        "- name: '$FOO'\n";
    env.put("FOO", "bar");
    assertEquals(requestWithVariableRef,
        new InlineCloudBuildRequest(requestWithVariableRef).expand(context));
  }
}