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

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import java.io.Serializable;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * A user-defined substitution to be added to the build request.
 * @see <a href="https://cloud.google.com/container-builder/docs/concepts/build-requests#user-defined_substitutions">
 *   Build Requests - User-defined substitutions</a>
 */
public final class Substitution extends AbstractDescribableImpl<Substitution> implements
    Serializable {
  private static final long serialVersionUID = 1L;

  private final String key;
  private final String value;

  @DataBoundConstructor
  public Substitution(String key, String value) {
    this.key = key;
    this.value = value;
  }

  public String getKey() { return key; }

  public String getValue() { return value; }

  @Extension
  public static class DescriptorImpl extends Descriptor<Substitution> {
    @Override @Nonnull
    public String getDisplayName() {
      return Messages.Substitution_DisplayName();
    }

    private static final Pattern KEY_PATTERN = Pattern.compile("_[A-Z0-9_]+");
    private static final int MAX_KEY_LENGTH = 100;

    /**
     * Validate the key.
     *
     * @param value The value of the {@code key} parameter being tested. Note that {@code value} is
     *     is a special parameter name referring to the value being tested. It does not refer to the
     *     {@link #value} binding above. See
     *     <a href="https://wiki.jenkins.io/display/JENKINS/Form+Validation">Form Validation</a>.
     * @return a {@link FormValidation} indicating the errors found in the {@code key} parameter, if
     *     any
     */
    public FormValidation doCheckKey(@QueryParameter String value) {
      if (value.isEmpty()) {
        return FormValidation.error(Messages.Substitution_KeyMustBeNonEmpty());
      }
      if (!KEY_PATTERN.matcher(value).matches()) {
        return FormValidation.errorWithMarkup(Messages.Substitution_InvalidKey_HTML());
      }
      if (value.length() > MAX_KEY_LENGTH) {
        return FormValidation.error(Messages.Substitution_KeyTooLong(MAX_KEY_LENGTH));
      }
      return FormValidation.ok();
    }
  }
}
