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
import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.google.api.services.cloudbuild.v1.model.RepoSource;
import com.google.api.services.cloudbuild.v1.model.Source;
import com.google.common.base.Strings;
import com.google.jenkins.plugins.cloudbuild.client.ClientFactory;
import com.google.jenkins.plugins.cloudbuild.context.BuildContext;
import hudson.Extension;
import hudson.Util;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;

/** Indicates that an existing Google Cloud Repo should be used as the source. */
public class RepoCloudBuildSource extends CloudBuildSource implements Serializable {
  private static final long serialVersionUID = 1L;

  @CheckForNull private String projectId;
  @CheckForNull private String repoName;
  @CheckForNull private String branch;
  @CheckForNull private String tag;
  @CheckForNull private String commit;

  /** Indicates the type of Git revision (branch, tag, or commit) to be specified as the source. */
  public enum RevisionType {
    BRANCH(Messages.RepoCloudBuildSource_RevisionType_Branch()),
    TAG(Messages.RepoCloudBuildSource_RevisionType_Tag()),
    COMMIT(Messages.RepoCloudBuildSource_RevisionType_Commit());

    public String getDisplayName() {
      return displayName;
    }

    private final String displayName;

    RevisionType(String displayName) {
      this.displayName = displayName;
    }
  }

  @DataBoundConstructor
  public RepoCloudBuildSource() {}

  @DataBoundSetter
  public void setProjectId(@CheckForNull String projectId) {
    this.projectId = Util.fixEmpty(projectId);
  }

  @CheckForNull
  public String getProjectId() {
    return projectId;
  }

  @DataBoundSetter
  public void setRepoName(@CheckForNull String repoName) {
    this.repoName = Util.fixEmpty(repoName);
  }

  @CheckForNull
  public String getRepoName() {
    return repoName;
  }

  @CheckForNull
  public String getBranch() {
    return branch;
  }

  @DataBoundSetter
  public void setBranch(@CheckForNull String branch) {
    this.branch = Util.fixEmpty(branch);
  }

  @CheckForNull
  public String getTag() {
    return tag;
  }

  @DataBoundSetter
  public void setTag(@CheckForNull String tag) {
    this.tag = Util.fixEmpty(tag);
  }

  @CheckForNull
  public String getCommit() {
    return commit;
  }

  @DataBoundSetter
  public void setCommit(@CheckForNull String commit) {
    this.commit = Util.fixEmpty(commit);
  }

  public RevisionType getRevisionType() {
    if (!Strings.isNullOrEmpty(commit)) {
      return RevisionType.COMMIT;
    }
    if (!Strings.isNullOrEmpty(tag)) {
      return RevisionType.TAG;
    }
    return RevisionType.BRANCH;
  }

  public String getRevision() {
    if (!Strings.isNullOrEmpty(commit)) {
      return commit;
    }
    if (!Strings.isNullOrEmpty(tag)) {
      return tag;
    }
    return branch;
  }

  @Override
  public Source prepare(BuildContext context, ClientFactory clients)
      throws IOException, InterruptedException {
    return new Source().setRepoSource(
        new RepoSource()
            .setProjectId(context.expand(projectId))
            .setRepoName(context.expand(repoName))
            .setBranchName(context.expand(branch))
            .setTagName(context.expand(tag))
            .setCommitSha(context.expand(commit)));
  }

  /** Descriptor for {@link RepoCloudBuildSource}. */
  @Extension(ordinal = 1.0) @Symbol("repo")
  public static class DescriptorImpl extends CloudBuildSourceDescriptor {
    @Override @Nonnull
    public String getDisplayName() {
      return Messages.RepoCloudBuildSource_DisplayName();
    }

    public ListBoxModel doFillRevisionTypeItems() {
      ListBoxModel items = new ListBoxModel();
      for (RevisionType type : RevisionType.values()) {
        items.add(type.getDisplayName(), type.name());
      }
      return items;
    }

    private static final Pattern SHA_REGEX = Pattern.compile("(?i)[0-9a-f]{40}");
    public FormValidation doCheckRevision(
        @QueryParameter String value,
        @QueryParameter String revisionType) {
      if (value.isEmpty()) {
        return FormValidation.error(Messages.RepoCloudBuildSource_RevisionRequired());
      }
      // Don't bother checking the format of the commit SHA if there are variables present.
      if (!Objects.equals(Util.replaceMacro(value, x -> ""), value)) {
        return FormValidation.ok();
      }
      RevisionType type = RevisionType.valueOf(revisionType);
      if (type == RevisionType.COMMIT && !SHA_REGEX.matcher(value).matches()) {
        return FormValidation.error(
            Messages.RepoCloudBuildSource_CommitSHAMustMatchPattern(SHA_REGEX.pattern()));
      }
      return FormValidation.ok();
    }

    @Override
    public CloudBuildSource newInstance(@Nullable StaplerRequest req, @Nonnull JSONObject formData)
        throws FormException {
      if (formData.has("revisionType") && formData.has("revision")) {
        RevisionType type = RevisionType.valueOf(formData.remove("revisionType").toString());
        String revision = formData.remove("revision").toString();
        RepoCloudBuildSource result = (RepoCloudBuildSource) super.newInstance(req, formData);
        switch (type) {
          case BRANCH:
            result.setBranch(revision);
            break;
          case TAG:
            result.setTag(revision);
            break;
          case COMMIT:
            result.setCommit(revision);
            break;
        }
        return result;
      } else {
        return super.newInstance(req, formData);
      }
    }
  }
}
