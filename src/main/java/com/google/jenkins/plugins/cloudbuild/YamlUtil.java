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
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import javax.annotation.Nonnull;

/** Helper methods for processing YAML. */
public final class YamlUtil {
  private YamlUtil() {}

  /**
   * Converts {@code yaml} from YAML to JSON. Note that, since YAML is a superset of JSON, this
   * method also accepts JSON.
   *
   * @param yaml the YAML to convert
   * @return a JSON document representing the same content as the provided YAML document
   * @throws IOException if an I/O error occurs while parsing the YAML document or while writing the
   *     JSON document
   */
  public static String toJson(@Nonnull String yaml) throws IOException {
    ObjectMapper reader = new ObjectMapper(new YAMLFactory());
    Object obj = reader.readValue(yaml, Object.class);
    ObjectMapper writer = new ObjectMapper();
    return writer.writeValueAsString(obj);
  }
}
