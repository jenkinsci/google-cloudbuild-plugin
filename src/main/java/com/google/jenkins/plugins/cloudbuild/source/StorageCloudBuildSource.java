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
import com.google.jenkins.plugins.cloudbuild.client.ClientFactory;
import com.google.jenkins.plugins.cloudbuild.context.BuildContext;
import hudson.Extension;
import hudson.util.FormValidation;
import java.io.IOException;
import java.io.Serializable;
import javax.annotation.Nonnull;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/** Indicates that an existing Google Cloud Storage object should be used as the source. */
public final class StorageCloudBuildSource extends CloudBuildSource implements Serializable {
  private static final long serialVersionUID = 1L;

  @Nonnull
  private final String bucket;

  @Nonnull
  private final String object;

  @DataBoundConstructor
  public StorageCloudBuildSource(@Nonnull String bucket, @Nonnull String object) {
    this.bucket = bucket;
    this.object = object;
  }

  @Nonnull
  public String getBucket() {
    return bucket;
  }

  @Nonnull
  public String getObject() {
    return object;
  }

  @Override
  public Source prepare(BuildContext context, ClientFactory clients)
      throws IOException, InterruptedException {
    String expandedBucket = context.expand(bucket);
    String expandedObject = context.expand(object);
    context.getListener().getLogger().println(
        Messages.StorageCloudBuildSource_Preparing(expandedBucket, expandedObject));
    return new Source().setStorageSource(
        new StorageSource()
            .setBucket(expandedBucket)
            .setObject(expandedObject));
  }

  /** Descriptor for {@link StorageCloudBuildSource}. */
  @Extension(ordinal = 2.0) @Symbol("storage")
  public static class DescriptorImpl extends CloudBuildSourceDescriptor {
    @Override @Nonnull
    public String getDisplayName() {
      return Messages.StorageCloudBuildSource_DisplayName();
    }

    public FormValidation doCheckBucket(@QueryParameter String value) {
      if (value.isEmpty()) {
        return FormValidation.error(Messages.StorageCloudBuildSource_BucketRequired());
      }
      return FormValidation.ok();
    }

    public FormValidation doCheckObject(@QueryParameter String value) {
      if (value.isEmpty()) {
        return FormValidation.error(Messages.StorageCloudBuildSource_ObjectRequired());
      }
      return FormValidation.ok();
    }
  }
}