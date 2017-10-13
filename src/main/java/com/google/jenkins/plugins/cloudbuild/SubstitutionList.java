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

import com.google.jenkins.plugins.cloudbuild.context.BuildContext;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.kohsuke.stapler.DataBoundConstructor;

/** A list of substitutions to be applied to a build request. */
public class SubstitutionList extends AbstractDescribableImpl<SubstitutionList> implements
    Serializable {
  private static final long serialVersionUID = 1L;

  private final List<Substitution> items;

  @DataBoundConstructor
  public SubstitutionList(List<Substitution> items) {
    this.items = items;
  }

  public List<Substitution> getItems() {
    return items;
  }

  /**
   * Converts the list of substitutions to a Map to be added to the build request, expanding each
   * value.
   *
   * @param context the context of the currently running Jenkins build to use for expanding variable
   *     references in the substitution values
   * @return a mapping of keys in this {@link SubstitutionList} to their corresponding expanded
   *     values
   * @throws IOException if an I/O error occurs while attempting to expand variables
   * @throws InterruptedException if an interruption occurs while attempting to expand variables
   * @see BuildContext#expand(String)
   */
  public Map<String, String> toMap(BuildContext context) throws IOException, InterruptedException {
    HashMap<String, String> subMap = new HashMap<>();
    if (items != null) {
      for (Substitution s : items) {
        subMap.put(s.getKey(), context.expand(s.getValue()));
      }
    }
    return subMap;
  }

  /** Descriptor for {@link SubstitutionList}. */
  @Extension
  public static class DescriptorImpl extends Descriptor<SubstitutionList> {
    @Override @Nonnull
    public String getDisplayName() {
      return Messages.SubstitutionList_DisplayName();
    }
  }
}
