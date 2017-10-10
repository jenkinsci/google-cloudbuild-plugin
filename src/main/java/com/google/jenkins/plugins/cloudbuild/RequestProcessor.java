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

import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudbuild.v1.model.Build;
import hudson.util.FormValidation;
import java.io.IOException;


/** Helper methods for handling the Build Request */
public final class RequestProcessor {
  private RequestProcessor() {}

  /**
   * Parses a YAML string representing a build request.
   *
   * @param request the YAML or JSON build request to parse
   * @return the parsed build request
   * @throws IOException if an I/O error occurs while parsing the request
   * @see <a href="https://cloud.google.com/container-builder/docs/concepts/build-requests">
   *        Cloud Container Builder - Build Requests</a>
   */
  public static Build parseBuildRequest(String request) throws IOException {
    // We have to convert the YAML to JSON, rather than using Jackson to parse the YAML directly
    // into a Build object. Jackson will interpret the Build object (as well as classes under nested
    // properties) as Maps (as these extend GenericJson), and try to set their properties using the
    // put method, rather than using setters. This results in errors because Jackson will not know
    // what type to use for the values and so will try to use a default type (e.g., HashMap) instead
    // of the type expected (e.g., import com.google.api.services.cloudbuild.v1.model.Source).
    String jsonRequest = YamlUtil.toJson(request);
    return new JacksonFactory().createJsonParser(jsonRequest).parseAndClose(Build.class);
  }

  /**
   * Validates the build request.
   *
   * @param request the request to validate
   * @return a {@link FormValidation} indicating whether the request is valid
   * */
  public static FormValidation validateBuildRequest(String request) {
    try {
      parseBuildRequest(request);
    } catch (Exception e) {
      return FormValidation.error(
          e, "Cannot parse build request:\n%s", e.getMessage().replaceAll("(?m)^", "  "));
    }
    return FormValidation.ok();
  }
}
