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

import com.google.api.services.cloudbuild.v1.model.Source;
import com.google.jenkins.plugins.cloudbuild.context.BuildContext;
import hudson.EnvVars;
import hudson.model.TaskListener;

/** Tests for {@link StorageCloudBuildSource}. */
public class StorageCloudBuildSourceTest {
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
  public void prepare() throws Exception {
    env.put("BUCKET", "foo");
    env.put("OBJECT", "bar");
    StorageCloudBuildSource source = new StorageCloudBuildSource("$BUCKET", "$OBJECT");
    Source apiSource = source.prepare(context, null);
    assertEquals("foo", apiSource.getStorageSource().getBucket());
    assertEquals("bar", apiSource.getStorageSource().getObject());
  }
}