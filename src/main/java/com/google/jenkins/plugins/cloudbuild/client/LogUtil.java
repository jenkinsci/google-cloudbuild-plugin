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

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Common classes to pass logs between cloudbuild and cloudstorage
 *
 * @since 0.3
 */
@Restricted(NoExternalUse.class)
public class LogUtil {
    /**
     * Contains the location of the log
     */
    @Restricted(NoExternalUse.class)
    public static class LogLocation {
      @Nonnull
      private final String bucketName;
      @Nonnull
      private final String objectName;

      private static final Pattern GSURI_REGEX = Pattern.compile(
          "^gs://(?<bucket>[^/]+)/(?<object>.*)$",
          Pattern.CASE_INSENSITIVE
      );

      /**
       * @param gsUri gs://bucketname/objectname
       * @throws IllegalArgumentException Invalid google storage URI
       */
      public LogLocation(@Nonnull String gsUri) throws IllegalArgumentException {
        // We can't use java.net.URI as it rejects invalid hostname
        // (like ones using undersore).
        Matcher m = GSURI_REGEX.matcher(gsUri);
        if (!m.matches()) {
          throw new IllegalArgumentException(String.format(
              "Invalid gs URI: %s",
              gsUri
          ));
        }
        this.bucketName = m.group("bucket");
        this.objectName = m.group("object");
      }

      /**
       * @return bucket name
       */
      @Nonnull
      public String getBucketName() {
        return bucketName;
      }

      /**
       * @return object name
       */
      @Nonnull
      public String getObjectName() {
        return objectName;
      }
    }

    /**
     * Interface form cloudbuild to trigger cloudstorage
     * to poll new log outputs.
     */
    @Restricted(NoExternalUse.class)
    public static interface LogPoller {
     /**
       * trigger cloud storage client to poll new log outputs
       * and output them to console.
       *
       * @throws IOException errors in communication to cloud storage
       */
      void poll() throws IOException;
    }
}
