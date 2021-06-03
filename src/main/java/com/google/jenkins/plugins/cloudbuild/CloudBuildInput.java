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
package com.google.jenkins.plugins.cloudbuild;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import com.google.jenkins.plugins.cloudbuild.context.BuildContext;
import com.google.jenkins.plugins.cloudbuild.request.CloudBuildRequest;
import com.google.jenkins.plugins.cloudbuild.source.CloudBuildSource;
import com.google.jenkins.plugins.credentials.domains.RequiresDomain;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import net.sf.json.JSONObject;

/** All inputs required to submit a Google Cloud Build build request. */
@RequiresDomain(value = CloudBuildScopeRequirement.class)
public class CloudBuildInput extends AbstractDescribableImpl<CloudBuildInput> implements
    Serializable {
  private static final long serialVersionUID = 1L;

  @Nonnull
  private String credentialsId;

  @Nonnull
  private final CloudBuildRequest request;

  @CheckForNull
  private CloudBuildSource source;

  @CheckForNull
  private SubstitutionList substitutionList;

  private boolean streamLog;

  @DataBoundConstructor
  public CloudBuildInput(@Nonnull String credentialsId, @Nonnull CloudBuildRequest request) {
    this.credentialsId = credentialsId;
    this.request = request;
  }

  @Nonnull
  public String getCredentialsId() {
    return credentialsId;
  }

  public void setCredentialsId(@Nonnull String credentialsId) {
    this.credentialsId = credentialsId;
  }

  @Nonnull
  public CloudBuildRequest getRequest() {
    return request;
  }

  @DataBoundSetter
  public void setSource(@CheckForNull CloudBuildSource source) {
    this.source = source;
  }

  @CheckForNull
  public CloudBuildSource getSource() {
    return source;
  }

  @Nonnull
  public CloudBuildSource getSourceOrDefault() {
    return source != null ? source : CloudBuildSource.NULL;
  }

  @DataBoundSetter
  public void setSubstitutionList(@CheckForNull SubstitutionList substitutionList) {
    this.substitutionList = substitutionList;
  }

  @CheckForNull
  public SubstitutionList getSubstitutionList() {
    return substitutionList;
  }

  @DataBoundSetter
  public void setSubstitutions(Map<String, String> substitutions) {
    List<Substitution> items = new ArrayList<>();
    substitutions.forEach((key, value) -> items.add(new Substitution(key, value)));
    setSubstitutionList(new SubstitutionList(items));
  }

  public Map<String, String> getSubstitutions() {
    if (substitutionList == null) {
      return Collections.emptyMap();
    }
    Map<String, String> result = new LinkedHashMap<>();
    substitutionList.getItems().forEach(s -> result.put(s.getKey(), s.getValue()));
    return result;
  }

  public Map<String, String> getSubstitutionMap(BuildContext context)
      throws IOException, InterruptedException {
    return substitutionList != null ? substitutionList.toMap(context) : Collections.emptyMap();
  }

  /**
   * @param streamLog whether to stream log
   * @since 0.3
   */
  @DataBoundSetter
  public void setStreamLog(boolean streamLog) {
    this.streamLog = streamLog;
  }

  /**
   * @return whether to stream log
   * @since 0.3
   */
  public boolean isStreamLog() {
    return streamLog;
  }

  /** Descriptor for {@link CloudBuildInput}. */
  @Extension
  public static class DescriptorImpl extends Descriptor<CloudBuildInput> {
    @Override @Nonnull
    public String getDisplayName() {
      return Messages.CloudBuildInput_DisplayName();
    }

    @Override
    public CloudBuildInput newInstance(StaplerRequest req, @Nonnull JSONObject formData)
        throws FormException {
      // According to the documentation for the <optionalBlock> tag:
      //
      //   http://reports.jenkins.io/reports/core-taglib/jelly-taglib-ref.html#form:optionalBlock
      //
      // the child controls should not send their values to the server when the block is collapsed.
      // However, the child controls are being sent unconditionally when inline="true". So we'll
      // check if the block is collapsed and remove the child data manually.
      if (Boolean.FALSE.equals(formData.remove("attachSource"))) {
        formData.remove("source");
      }
      return super.newInstance(req, formData);
    }
  }
}
