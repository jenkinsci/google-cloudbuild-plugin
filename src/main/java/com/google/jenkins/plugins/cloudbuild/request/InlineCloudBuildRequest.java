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

import com.google.jenkins.plugins.cloudbuild.RequestProcessor;
import com.google.jenkins.plugins.cloudbuild.context.BuildContext;
import hudson.Extension;
import hudson.util.FormValidation;
import java.io.IOException;
import java.io.Serializable;
import javax.annotation.Nonnull;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/** A build request that is specified inline. */
public class InlineCloudBuildRequest extends CloudBuildRequest implements Serializable {
  private static final long serialVersionUID = 1L;

  @Nonnull
  private final String request;

  @DataBoundConstructor
  public InlineCloudBuildRequest(@Nonnull String request) {
    this.request = request;
  }

  @Nonnull
  public String getRequest() {
    return request;
  }

  @Override
  public String expand(BuildContext context) throws IOException, InterruptedException {
    return request; // No Jenkins expansions for this build request
  }

  @Extension @Symbol("inline")
  public static class DescriptorImpl extends CloudBuildRequestDescriptor {
    @Override @Nonnull
    public String getDisplayName() {
      return Messages.InlineCloudBuildRequest_DisplayName();
    }

    public FormValidation doCheckRequest(@QueryParameter String value) {
      if (value.length() == 0) {
        return FormValidation.error(Messages.InlineCloudBuildRequest_BuildRequestRequired());
      }

      return RequestProcessor.validateBuildRequest(value);
    }
  }
}
