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
package com.google.jenkins.plugins.cloudbuild.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.Bucket.Lifecycle;
import com.google.api.services.storage.model.Bucket.Lifecycle.Rule;
import com.google.api.services.storage.model.Bucket.Lifecycle.Rule.Action;
import com.google.api.services.storage.model.Bucket.Lifecycle.Rule.Condition;
import com.google.api.services.storage.model.StorageObject;
import hudson.model.TaskListener;

/**
 * Client for communicating with Google Cloud Storage API.
 *
 * @see <a href="https://cloud.google.com/storage/">Cloud Storage</a>
 */
public class CloudStorageClient {
  private final Storage storage;
  private final String projectId;
  private final TaskListener listener;

  CloudStorageClient(Storage storage, String projectId, TaskListener listener) {
    this.storage = storage;
    this.projectId = projectId;
    this.listener = listener;
  }

  private static final String STORAGE_BROWSE_ROOT = "https://storage.cloud.google.com";

  /**
   * Appends a link to a Cloud Storage bucket (in Cloud Console) to the log.
   *
   * @param bucket the name of the Cloud Storage bucket
   * @throws IOException if an I/O error occurs while writing the link to the Jenkins console log
   */
  private void hyperlinkBucket(String bucket) throws IOException {
    String url = String.format("%s/%s", STORAGE_BROWSE_ROOT, bucket);
    listener.hyperlink(url, bucket);
  }

  /**
   * Appends a link to a Cloud Storage object (in Cloud Console) to the log.
   *
   * @param bucket the name of the bucket containing the Cloud Storage object
   * @param object the path to the Cloud Storage object within the specified bucket
   * @throws IOException if an I/O error occurs while writing the link to the Jenkins console log
   */
  private void hyperlinkObject(String bucket, String object) throws IOException {
    String url = String.format("%s/%s/%s", STORAGE_BROWSE_ROOT, bucket, object);
    String gs = String.format("gs://%s/%s", bucket, object);
    listener.hyperlink(url, gs);
  }

  /**
   * Uploads an object to the Jenkins workspace.
   *
   * @param bucket the bucket to upload to
   * @param object the path to the object to write to within the bucket
   * @param type the MIME type of the object to upload
   * @param data an InputStream for reading the data to be uploaded
   * @throws IOException if an I/O error occurs while processing the request
   */
  public void putCloudFiles(String bucket, String object, String type, InputStream data)
      throws IOException {
    listener.getLogger().println(Messages.CloudStorageClient_UploadingFiles());

    StorageObject storageObject = new StorageObject();
    storageObject.setBucket(bucket);
    storageObject.setName(object);

    InputStreamContent content = new InputStreamContent(type, data);

    Storage.Objects.Insert insert = storage.objects().insert(bucket, storageObject, content);
    insert.execute();

    listener.getLogger().printf("%s: ", Messages.CloudStorageClient_FileUploadedTo());
    hyperlinkObject(bucket, object);
    listener.getLogger().println();
  }

  private static final String TEMP_BUCKET_PREFIX = "jenkins-tmp_";
  private static final int TEMP_BUCKET_TTL_DAYS = 3;
  private String tempBucketName = null;

  /**
   * Creates a bucket for storing temporary objects.
   *
   * @return the name of the Cloud Storage bucket to use for storing temporary objects
   * @throws IOException if an I/O error occurs while communicating with the Cloud Storage API to
   *     find or create the temporary bucket
   */
  public synchronized String createTempBucket() throws IOException {
    PrintStream logger = listener.getLogger();
    if (tempBucketName != null) {
      logger.printf("%s: ", Messages.CloudStorageClient_UsingExistingTempBucket());
      hyperlinkBucket(tempBucketName);
      logger.println();
      return tempBucketName;
    }

    logger.println(Messages.CloudStorageClient_LookingForTempBucket());
    List<Bucket> buckets =
        storage.buckets().list(projectId).setPrefix(TEMP_BUCKET_PREFIX).execute().getItems();
    if (buckets != null && !buckets.isEmpty()) {
      tempBucketName = buckets.get(0).getName();
      logger.printf("%s: ", Messages.CloudStorageClient_FoundExistingTempBucket());
      hyperlinkBucket(tempBucketName);
      logger.println();
      return tempBucketName;
    }

    logger.println(Messages.CloudStorageClient_CreatingNewTempBucket());
    String name = String.format("%s%s", TEMP_BUCKET_PREFIX, UUID.randomUUID().toString());
    tempBucketName =
        storage.buckets().insert(projectId,
            new Bucket()
                .setName(name)
                .setLifecycle(
                    new Lifecycle()
                        .setRule(Collections.singletonList(
                            new Rule()
                                .setAction(new Action().setType("Delete"))
                                .setCondition(new Condition().setAge(TEMP_BUCKET_TTL_DAYS))))))
            .execute().getName();
    logger.printf("%s: ", Messages.CloudStorageClient_CreatedNewTempBucket());
    hyperlinkBucket(tempBucketName);
    logger.println();
    return tempBucketName;
  }

  @Nonnull
  public LogUtil.LogPoller createStreamContentPoller(@Nonnull final LogUtil.LogLocation location) {
    return new LogUtil.LogPoller() {
      private long bytesRead = 0;

      @Override
      public void poll() throws IOException {
        Storage.Objects.Get req = storage.objects()
            .get(location.getBucketName(), location.getObjectName());
        req.getMediaHttpDownloader().setBytesDownloaded(bytesRead);

        // Somehow I can't use CountingOutputStream here.
        // executeMediaAndDownloadTo() looks stuck.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
          try {
            req.executeMediaAndDownloadTo(out);
          } catch (HttpResponseException e) {
            int code = e.getStatusCode();
            if (code == 404) {
              // Not available yet. No problem.
              listener.getLogger().print("Log is not available yet.\n");
            } else if (code == 416) {
              // Requested range not satisfiable
              // No new outputs. No problem.
            } else if (500 <= code && code < 600) {
              // Ignore server error.
              listener.getLogger().printf(
                  "Could not poll the log for server error: %d %s%n",
                  code,
                  e.getStatusMessage()
              );
            } else {
              throw e;
            }
          }
          listener.getLogger().write(out.toByteArray());
          bytesRead += out.size();
        } finally {
          out.close();
        }
      }
    };
  }
}
