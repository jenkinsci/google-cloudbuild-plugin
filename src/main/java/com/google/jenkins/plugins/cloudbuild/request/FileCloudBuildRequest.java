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
import java.io.Serializable;

import javax.annotation.Nonnull;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.jenkins.plugins.cloudbuild.context.BuildContext;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;

/** Reads the build request from a file within the workspace. */
public class FileCloudBuildRequest extends CloudBuildRequest implements Serializable {
  private static final long serialVersionUID = 1L;

  @Nonnull
  private final String filename;

  @DataBoundConstructor
  public FileCloudBuildRequest(@Nonnull String filename) {
    this.filename = filename;
  }

  @Nonnull
  public String getFilename() {
    return filename;
  }

  @Override
  public String expand(BuildContext context) throws IOException, InterruptedException {
    FilePath workspace = context.getWorkspace();
    if (workspace == null) {
      throw new AbortException(Messages.FileCloudBuildRequest_WorkspaceRequired());
    }
    String expandedFilename = context.expand(filename);
    return workspace.child(expandedFilename).readToString();
  }

  /** Descriptor for {@link FileCloudBuildRequest}. */
  @Extension @Symbol("file")
  public static class DescriptorImpl extends CloudBuildRequestDescriptor {
    @Override @Nonnull
    public String getDisplayName() {
      return Messages.FileCloudBuildRequest_DisplayName();
    }
  }
}