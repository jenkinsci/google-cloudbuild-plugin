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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.nio.charset.Charset;
import org.junit.Assert;
import org.junit.Test;

/** Unit tests for YamlUtil. */
public class YamlUtilTest {

  private static final String RESOURCE_BASE =
      "/com/google/jenkins/plugins/cloudbuild/YamlUtilTest/";

  @Test
  public void toJson() throws Exception {
    String input = Resources.toString(
        getClass().getResource(RESOURCE_BASE + "test1-input.yaml"),
        Charset.defaultCharset());
    String actual = YamlUtil.toJson(input);

    String expected = Resources.toString(
        getClass().getResource(RESOURCE_BASE + "test1-expected.json"),
        Charset.defaultCharset());

    ObjectMapper json = new ObjectMapper();
    Object actualObj = json.readValue(actual, Object.class);  // Ensure output is in fact JSON.
    Object expectedObj = json.readValue(expected, Object.class);

    String actualCanonical = json.writeValueAsString(actualObj);
    String expectedCanonical = json.writeValueAsString(expectedObj);
    Assert.assertEquals(expectedCanonical, actualCanonical);
  }

}