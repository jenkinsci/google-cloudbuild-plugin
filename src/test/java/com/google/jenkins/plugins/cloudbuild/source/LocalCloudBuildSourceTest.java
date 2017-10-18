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
import java.io.InputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.CreateFileBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static net.sf.ezmorph.test.ArrayAssertions.assertEquals;

import com.google.api.client.util.IOUtils;
import com.google.api.services.cloudbuild.v1.model.Source;
import com.google.jenkins.plugins.cloudbuild.client.ClientFactory;
import com.google.jenkins.plugins.cloudbuild.client.CloudStorageClient;
import com.google.jenkins.plugins.cloudbuild.context.BuildContext;
import com.google.jenkins.plugins.cloudbuild.context.FreeStyleBuildContext;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.FilePath.TarCompression;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.slaves.EnvironmentVariablesNodeProperty;

/** Tests for {@link LocalCloudBuildSource}. */
public class LocalCloudBuildSourceTest {
  @Rule
  public JenkinsRule j = new JenkinsRule();

  @Mock
  private ClientFactory clients;

  @Mock
  private CloudStorageClient storage;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    when(clients.storage()).thenReturn(storage);
  }

  @Test
  public void prepare() throws Exception {
    EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
    EnvVars env = prop.getEnvVars();
    env.put("SOURCE_PATH", "src");
    j.jenkins.getGlobalNodeProperties().add(prop);

    FreeStyleProject project = j.createFreeStyleProject();

    // These files should *not* be included in the source tarball.
    project.getBuildersList().add(new CreateFileBuilder("foo.txt", "foo"));
    project.getBuildersList().add(new CreateFileBuilder("bar/bar.txt", "bar"));

    // These files should be included in the source tarball.
    project.getBuildersList().add(new CreateFileBuilder("src/baz.txt", "baz"));
    project.getBuildersList().add(new CreateFileBuilder("src/qux/qux.txt", "qux"));

    LocalCloudBuildSource source = new LocalCloudBuildSource("$SOURCE_PATH");
    project.getBuildersList().add(new TestBuilder() {
      @Override
      public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
          throws InterruptedException, IOException {
        FilePath workspace = build.getWorkspace();

        String tmpBucket = "jenkins_tmp";
        when(storage.createTempBucket()).thenReturn(tmpBucket);

        // Simulate uploading to Cloud Storage by copying the file to a directory within the
        // workspace, specifically: _gcs/_buckets/<bucket>/_objects/<path/to/object>.
        doAnswer(invocation -> {
          String bucket = invocation.getArgument(0);
          String object = invocation.getArgument(1);
          String mimeType = invocation.getArgument(2);
          InputStream in = invocation.getArgument(3);

          assertEquals("application/gzip", mimeType);
          FilePath dest = workspace.child("_gcs")
              .child("_buckets").child(bucket)
              .child("_objects").child(object);
          IOUtils.copy(in, dest.write());
          return null;
        }).when(storage).putCloudFiles(any(), any(), any(), any());

        BuildContext context = new FreeStyleBuildContext(build, listener);
        Source apiSource = source.prepare(context, clients);

        // Verify that the source tarball was uploaded to the bucket returned by createTempBucket(),
        // that the Source returned by source.prepare() matches the file uploaded to Cloud Storage,
        // and that the tarball that was uploaded contains exactly the set of files specified in the
        // LocalCloudStorageSource configuration.
        assertEquals(tmpBucket, apiSource.getStorageSource().getBucket());
        workspace.child("_gcs")
            .child("_buckets").child(apiSource.getStorageSource().getBucket())
            .child("_objects").child(apiSource.getStorageSource().getObject())
            .untar(workspace.child("_source"), TarCompression.GZIP);

        assertEquals(2, workspace.child("_source").list("**").length);
        assertEquals("baz", workspace.child("_source/baz.txt").readToString());
        assertEquals("qux", workspace.child("_source/qux/qux.txt").readToString());
        return true;
      }
    });

    j.buildAndAssertSuccess(project);
  }
}