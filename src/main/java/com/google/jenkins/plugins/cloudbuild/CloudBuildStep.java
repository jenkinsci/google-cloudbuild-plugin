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

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;

/** A Jenkins build step that submits a build request to Google Cloud Container Builder. */
public final class CloudBuildStep extends Step implements Serializable {
  private static final long serialVersionUID = 1L;

  @Nonnull
  private final CloudBuildInput input;

  @DataBoundConstructor
  public CloudBuildStep(@Nonnull CloudBuildInput input) {
    this.input = input;
  }

  @Nonnull
  public CloudBuildInput getInput() {
    return input;
  }

  @Override
  public StepExecution start(StepContext context) throws Exception {
    return new CloudBuildStepExecution(input, context);
  }

  /** Descriptor for {@link CloudBuildStep}. */
  @Extension
  public static class Descriptor extends StepDescriptor {
    @Override @Nonnull
    public String getDisplayName() {
      return Messages.CloudBuildStep_DisplayName();
    }

    @Override
    public String getFunctionName() {
      return "googleCloudBuild";
    }

    @Override
    public Set<? extends Class<?>> getRequiredContext() {
      return new HashSet<>(Arrays.asList(
          EnvVars.class, FilePath.class, Run.class, TaskListener.class));
    }

    @Override
    public UninstantiatedDescribable uninstantiate(Step step) throws UnsupportedOperationException {
      CloudBuildStep s = (CloudBuildStep) step;

      // Uninstantiate the input parameter only. Jenkins knows how to handle instantiation when
      // there is exactly one required parameter, and the generated code is much nicer.
      UninstantiatedDescribable result =
          new DescribableModel<>(CloudBuildInput.class).uninstantiate2(s.input);

      // Remove the substitutionList property, since it duplicates the substitutions property and is
      // more verbose.
      result.getArguments().remove("substitutionList");

      return result;
    }
  }
}
