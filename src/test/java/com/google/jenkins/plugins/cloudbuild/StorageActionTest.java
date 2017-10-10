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

/** Tests for {@link StorageAction}. */
public class StorageActionTest {
  private static final String CLOUD_STORAGE_ROOT =
      "https://console.cloud.google.com/storage/browser/";

  @Test
  @WithoutJenkins
  public void getUrlName() {
    assertEquals(CLOUD_STORAGE_ROOT + "foo", new StorageAction("foo", "").getUrlName());
    assertEquals(CLOUD_STORAGE_ROOT + "foo/bar/baz",
        new StorageAction("foo", "bar/baz").getUrlName());
  }
}