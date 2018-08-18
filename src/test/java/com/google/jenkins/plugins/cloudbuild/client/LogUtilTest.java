/*
 * Copyright 2018 Google Inc.
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
package com.google.jenkins.plugins.cloudbuild.client;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests for {@link LogUtil}
 */
public class LogUtilTest {
  @Test
  public void testLogLocationBasic() throws Exception {
    LogUtil.LogLocation location = new LogUtil.LogLocation(
        "gs://1234567890.cloudbuild-logs.googleusercontent.com/log-xxx.txt"
    );
    assertEquals(
        "1234567890.cloudbuild-logs.googleusercontent.com",
        location.getBucketName()
    );
    assertEquals("log-xxx.txt", location.getObjectName());
  }

  @Test
  public void testLogLocationOwnBucket() throws Exception {
    LogUtil.LogLocation location = new LogUtil.LogLocation(
        "gs://my-project_cloudbuild-log/log-xxx.txt"
    );
    assertEquals("my-project_cloudbuild-log", location.getBucketName());
    assertEquals("log-xxx.txt", location.getObjectName());
  }

  @Test
  public void testLogLocationInDirectory() throws Exception {
    LogUtil.LogLocation location = new LogUtil.LogLocation(
        "gs://my-project_cloudbuild/logs/log-xxx.txt"
    );
    assertEquals("my-project_cloudbuild", location.getBucketName());
    assertEquals("logs/log-xxx.txt", location.getObjectName());
  }
}
