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

import org.junit.Assert;
import org.junit.Test;

import hudson.util.FormValidation.Kind;

/** Unit tests for RequestProcessor */
public class RequestProcessorTest {

  @Test
  public void validateBuildRequest() throws Exception {
    // Unparsable.
    Assert.assertEquals(Kind.ERROR, RequestProcessor.validateBuildRequest("invalid YAML").kind);

    // Does not match correct type for Build object.
    Assert.assertEquals(Kind.ERROR, RequestProcessor.validateBuildRequest("42").kind);
    Assert.assertEquals(Kind.ERROR, RequestProcessor.validateBuildRequest("[]").kind);
    Assert.assertEquals(Kind.ERROR, RequestProcessor.validateBuildRequest("\"foo\"").kind);

    // Does not match correct type for a field in the Build object.
    Assert.assertEquals(Kind.ERROR, RequestProcessor.validateBuildRequest("source: 42").kind);
    Assert.assertEquals(Kind.ERROR, RequestProcessor.validateBuildRequest("source: []").kind);
    Assert.assertEquals(Kind.ERROR, RequestProcessor.validateBuildRequest("steps: 42").kind);
    Assert.assertEquals(Kind.ERROR, RequestProcessor.validateBuildRequest("steps: {}").kind);
    Assert.assertEquals(Kind.ERROR, RequestProcessor.validateBuildRequest("steps: [42]").kind);

    // An empty build is okay.
    Assert.assertEquals(Kind.OK, RequestProcessor.validateBuildRequest("{}").kind);

    // Unknown keys are okay.
    Assert.assertEquals(Kind.OK, RequestProcessor.validateBuildRequest("{\"foo\": 42}").kind);
  }
}
