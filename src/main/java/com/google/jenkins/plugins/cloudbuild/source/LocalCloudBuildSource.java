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

import com.google.api.services.cloudbuild.v1.model.Source;
import com.google.api.services.cloudbuild.v1.model.StorageSource;
import com.google.jenkins.plugins.cloudbuild.CloudBuildBuilder;
import com.google.jenkins.plugins.cloudbuild.client.ClientFactory;
import com.google.jenkins.plugins.cloudbuild.client.CloudStorageClient;
import com.google.jenkins.plugins.cloudbuild.context.BuildContext;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.FastPipedInputStream;
import hudson.remoting.FastPipedOutputStream;
import hudson.util.DaemonThreadFactory;
import hudson.util.DirScanner;
import hudson.util.FormValidation;
import hudson.util.NamingThreadFactory;
import hudson.util.io.ArchiverFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nonnull;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Uploads a file, or a gzipped-tarball of a directory, within the workspace to a temporary Google
 * Storage bucket and uses that as the source.
 */
public class LocalCloudBuildSource extends CloudBuildSource implements Serializable {
  private static final long serialVersionUID = 1L;

  @Nonnull
  private final String path;

  @DataBoundConstructor
  public LocalCloudBuildSource(@Nonnull String path) {
    this.path = path;
  }

  @Nonnull
  public String getPath() {
    return path;
  }

  /** The executor to be used for asynchronous tasks. */
  private static volatile ExecutorService executorService;

  /** Returns a shared thread-pool executor on which asynchronous tasks will be run. */
  private static synchronized ExecutorService getExecutorService() {
    if (executorService == null) {
      executorService = Executors.newCachedThreadPool(
          new NamingThreadFactory(
              new DaemonThreadFactory(), CloudBuildBuilder.class.getCanonicalName()));
    }
    return executorService;
  }

  /**
   * Uploads a local file or directory to Cloud Storage and uses that as the source.
   * <p>
   * If the path refers to a file, upload that file to a temporary Cloud Storage bucket. If the path
   * refers to a directory, then create a gzipped-tarball containing the contents of that directory
   * and upload that to a temporary Cloud Storage bucket.
   *
   * @return a {@link Source} that refers to the uploaded object
   */
  @Override
  public Source prepare(BuildContext context, ClientFactory clients)
      throws IOException, InterruptedException {
    String expandedPath = context.expand(path);
    context.getListener().getLogger().println(
        Messages.LocalCloudBuildSource_Preparing(expandedPath));

    FilePath workspace = context.getWorkspace();
    if (workspace == null) {
      throw new AbortException(Messages.LocalCloudBuildSource_WorkspaceRequired());
    }
    FilePath root = workspace.child(expandedPath);
    if (!root.exists()) {
      throw new AbortException(Messages.LocalCloudBuildSource_SourcePathDoesNotExist());
    }

    CloudStorageClient storage = clients.storage();
    String bucket = storage.createTempBucket();
    String objectBaseName = String.format("source/%d-%s", System.currentTimeMillis(),
        UUID.randomUUID().toString());

    InputStream contents;
    String object;
    if (root.isDirectory()) {
      FastPipedInputStream in = new FastPipedInputStream();
      TaskListener listener = context.getListener();
      getExecutorService().submit(() -> {
        try {
          FastPipedOutputStream out = new FastPipedOutputStream(in);
          root.archive(ArchiverFactory.TARGZ, out, new DirScanner.Glob("**", ""));
        } catch (Exception e) {
          e.printStackTrace(listener.getLogger());
          listener.fatalError(Messages.LocalCloudBuildSource_CouldNotArchiveSource());
        }
      });
      contents = in;
      object = String.format("%s.tgz", objectBaseName);
    } else {
      contents = root.read();
      object = String.format("%s/%s", objectBaseName, root.getName());
    }

    storage.putCloudFiles(bucket, object, "application/gzip", contents);
    return new Source().setStorageSource(
        new StorageSource()
            .setBucket(bucket)
            .setObject(object));
  }

  @Extension(ordinal = 3.0) @Symbol("local")
  public static class DescriptorImpl extends CloudBuildSourceDescriptor {
    @Override @Nonnull
    public String getDisplayName() {
      return Messages.LocalCloudBuildSource_DisplayName();
    }

    public FormValidation doCheckPath(@QueryParameter String value) {
      if (value.isEmpty()) {
        return FormValidation.error(Messages.LocalCloudBuildSource_PathRequired());
      }
      return FormValidation.ok();
    }
  }
}
