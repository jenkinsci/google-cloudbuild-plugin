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

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.jvnet.hudson.test.WithoutJenkins;

import com.google.jenkins.plugins.cloudbuild.Substitution.DescriptorImpl;
import hudson.util.FormValidation.Kind;

/** Tests for {@link Substitution}. */
public class SubstitutionTest {

  @Test
  @WithoutJenkins
  public void doCheckKey() {
    DescriptorImpl descriptor = new DescriptorImpl();

    // Key is required.
    assertEquals(Kind.ERROR, descriptor.doCheckKey("").kind);

    // The following keys do not conform to the requirements for user-defined substitutions.
    // See https://cloud.google.com/cloud-build/docs/concepts/build-requests#substitutions
    assertEquals(Kind.ERROR, descriptor.doCheckKey("FOO").kind);
    assertEquals(Kind.ERROR, descriptor.doCheckKey("_foo").kind);
    assertEquals(Kind.ERROR, descriptor.doCheckKey("_FOO.BAR").kind);
    assertEquals(Kind.ERROR, descriptor.doCheckKey("_").kind);

    // Max length is 100 characters.
    assertEquals(Kind.ERROR, descriptor.doCheckKey(
        "_XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX" +
        "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX").kind);

    // The following keys do conform to the requirements for user-defined substitutions.
    assertEquals(Kind.OK, descriptor.doCheckKey("_FOO").kind);
    assertEquals(Kind.OK, descriptor.doCheckKey("_123").kind);
    assertEquals(Kind.OK, descriptor.doCheckKey("_FOO_BAR").kind);
    assertEquals(Kind.OK, descriptor.doCheckKey("_FOO_123").kind);
    assertEquals(Kind.OK, descriptor.doCheckKey("_X").kind);
    assertEquals(Kind.OK, descriptor.doCheckKey("_1").kind);
    assertEquals(Kind.OK, descriptor.doCheckKey("__FOO").kind);
    assertEquals(Kind.OK, descriptor.doCheckKey("__").kind);

    // Max length is 100 characters.
    assertEquals(Kind.OK, descriptor.doCheckKey(
        "_XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX" +
        "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX").kind);
  }

}
