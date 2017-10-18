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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.Json;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.Buckets;
import com.google.api.services.storage.model.StorageObject;
import com.google.common.net.HttpHeaders;
import hudson.model.TaskListener;

/** Tests for {@link CloudStorageClient}. */
public class CloudStorageClientTest {

  @Mock(answer = Answers.CALLS_REAL_METHODS)
  public MockHttpTransport transport;

  private JsonFactory json = new JacksonFactory();

  private CloudStorageClient storage;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    storage = new CloudStorageClient(
        new Storage.Builder(transport, json, req -> {})
            .setApplicationName("google-cloudbuild-plugin-test")
            .build(),
        "test-project", TaskListener.NULL);
  }

  @Test
  public void putCloudFiles() throws Exception {
    when(transport.buildRequest(eq(HttpMethods.POST), contains("/upload/storage/v1/b/foo/o")))
        .thenAnswer(invocation -> {
          String url = invocation.getArgument(1);
          return new MockLowLevelHttpRequest() {
            @Override
            public LowLevelHttpResponse execute() throws IOException {
              URL u = new URL(url);
              StorageObject object =
                  json.createJsonParser(getContentAsString()).parse(StorageObject.class);
              assertEquals("bar", object.getName());

              MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
              response.setStatusCode(HttpStatusCodes.STATUS_CODE_OK);
              response.setContentLength(0);
              response.addHeader(HttpHeaders.LOCATION, url);
              return response;
            }
          };
        });

    when(transport.buildRequest(eq(HttpMethods.PUT), contains("/upload/storage/v1/b/foo/o")))
        .thenReturn(new MockLowLevelHttpRequest() {
          @Override
          public LowLevelHttpResponse execute() throws IOException {
            assertEquals("text/plain", getContentType());
            assertEquals("baz", getContentAsString());

            MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
            response.setStatusCode(HttpStatusCodes.STATUS_CODE_OK);
            response.setContentType(Json.MEDIA_TYPE);
            response.setContent(json.toString(new StorageObject().setId("42")));
            return response;
          }
        });

    storage.putCloudFiles("foo", "bar", "text/plain", new ByteArrayInputStream("baz".getBytes()));

    verify(transport, times(1)).buildRequest(
        eq(HttpMethods.POST), contains("/upload/storage/v1/b/foo/o"));
    verify(transport, times(1)).buildRequest(
        eq(HttpMethods.PUT), contains("/upload/storage/v1/b/foo/o"));
  }

  @Test
  public void createTempBucket_AlreadyExists() throws Exception {
    when(transport.buildRequest(eq(HttpMethods.GET), contains("/storage/v1/b?")))
        .thenAnswer(invocation -> {
          URL url = new URL(invocation.getArgument(1));
          assertThat(url.getQuery(), containsString("project=test-project"));
          assertThat(url.getQuery(), containsString("prefix=jenkins-tmp_"));
          return new MockLowLevelHttpRequest() {
            @Override
            public LowLevelHttpResponse execute() throws IOException {
              MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
              response.setStatusCode(HttpStatusCodes.STATUS_CODE_OK);
              response.setContentType(Json.MEDIA_TYPE);
              response.setContent(json.toString(new Buckets()
                  .setItems(Collections.singletonList(new Bucket()
                      .setName("jenkins-tmp_foo")))));
              return response;
            }
          };
        });

    assertEquals("jenkins-tmp_foo", storage.createTempBucket());
  }

  @Test
  public void createTempBucket_CreatesNewBucket() throws Exception {
    when(transport.buildRequest(eq(HttpMethods.GET), contains("/storage/v1/b?")))
        .thenAnswer(invocation -> {
          URL url = new URL(invocation.getArgument(1));
          assertThat(url.getQuery(), containsString("project=test-project"));
          assertThat(url.getQuery(), containsString("prefix=jenkins-tmp_"));
          return new MockLowLevelHttpRequest() {
            @Override
            public LowLevelHttpResponse execute() throws IOException {
              MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
              response.setStatusCode(HttpStatusCodes.STATUS_CODE_OK);
              response.setContentType(Json.MEDIA_TYPE);
              response.setContent(json.toString(new Buckets().setItems(Collections.emptyList())));
              return response;
            }
          };
        });

    final List<String> bucketsCreated = new ArrayList<>();
    when(transport.buildRequest(eq(HttpMethods.POST), contains("/storage/v1/b?")))
        .thenAnswer(invocation -> {
          URL url = new URL(invocation.getArgument(1));
          assertThat(url.getQuery(), containsString("project=test-project"));
          return new MockLowLevelHttpRequest() {
            @Override
            public LowLevelHttpResponse execute() throws IOException {
              Bucket bucket = json.createJsonParser(getContentAsString()).parse(Bucket.class);
              assertThat(bucket.getName(), startsWith("jenkins-tmp_"));
              assertEquals("Delete", bucket.getLifecycle().getRule().get(0).getAction().getType());
              assertThat(
                  bucket.getLifecycle().getRule().get(0).getCondition().getAge(), greaterThan(0));
              bucketsCreated.add(bucket.getName());
              MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
              response.setStatusCode(HttpStatusCodes.STATUS_CODE_OK);
              response.setContentType(Json.MEDIA_TYPE);
              response.setContent(getContentAsString());
              return response;
            }
          };
        });

    String tempBucket = storage.createTempBucket();

    assertEquals(1, bucketsCreated.size());
    assertEquals(tempBucket, bucketsCreated.get(0));
  }
}