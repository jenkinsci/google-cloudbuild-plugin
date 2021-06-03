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

import java.io.IOException;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.when;

import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.Json;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.cloudbuild.v1.model.Build;
import com.google.api.services.cloudbuild.v1.model.Operation;
import com.google.api.services.storage.model.Buckets;
import com.google.api.services.storage.model.StorageObject;
import com.google.common.io.Resources;
import com.google.common.net.HttpHeaders;
import com.google.jenkins.plugins.cloudbuild.client.ClientFactory;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentialsModule;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.JsonServiceAccountConfig;
import hudson.FilePath;
import jenkins.model.Jenkins;

/**
 * Provides mock handlers for Cloud Service API endpoints.
 */
public class MockCloudServices {
  private static final String RESOURCE_BASE =
      "/com/google/jenkins/plugins/cloudbuild/MockCloudServices/";

  @Mock(answer = Answers.CALLS_REAL_METHODS)
  private MockHttpTransport transport;

  @Mock
  private GoogleRobotCredentialsModule credentialsModule;

  private JsonFactory json = new JacksonFactory();

  /**
   * A user-provided handler for a Cloud API endpoint.
   *
   * @param <T> the type of the payload
   * @param <R> the type of the response
   */
  public interface MockRequestHandler<T, R> {
    /**
     * Handles a mock HTTP request to a Cloud API endpoint.
     *
     * @param in the deserialized payload
     * @param req the HTTP request received by the endpoint
     * @param resp the HTTP response to return to the client
     * @return the response payload. If non-null, this will be serialized as JSON and added to
     *     {@code resp} before returning the response to the client
     * @throws IOException if an error occurs while handling the request
     */
    R handle(T in, MockLowLevelHttpRequest req, MockLowLevelHttpResponse resp) throws IOException;
  }

  /**
   * Function to serialize response contents from a object
   *
   * @param <R> type to serialize the response from
   */
  @FunctionalInterface
  public interface ResponseConverter<R> {
     String apply(R t) throws IOException;
  }

  /**
   * Creates a handler for mocking a call to {@link #transport} which checks the authorization
   * token, parses the request object from JSON, calls a user-provided handler, and serializes the
   * response to JSON.
   *
   * @param clazz the class that the request payload is expected to represent, which will be
   *     deserialized from JSON
   * @param handler the user-provided handler to invoke
   * @param <T> the type of the request payload
   * @param <R> the type of the response payload
   * @return an {@link Answer} to be provided as a mock to handle the corresponding HTTP requests
   * @see MockHttpTransport#buildRequest(String, String)
   */
  private <T, R> Answer<MockLowLevelHttpRequest> mockRequest(
      Class<T> clazz, MockRequestHandler<T, R> handler) {
    return mockRequestBase(clazz, handler, r -> { return json.toString(r); });
  }

  private <T, R> Answer<MockLowLevelHttpRequest> mockRequestBase(
      Class<T> clazz, MockRequestHandler<T, R> handler, ResponseConverter<R> converter) {
    return invocation -> new MockLowLevelHttpRequest(invocation.getArgument(1)) {
      @Override
      public LowLevelHttpResponse execute() throws IOException {
        MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
        if (!getFirstHeaderValue("authorization").equals("Bearer super-secret-token")) {
          response.setStatusCode(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED);
          return response;
        }
        response.setStatusCode(HttpStatusCodes.STATUS_CODE_OK);

        T in = null;
        if (!clazz.equals(Void.class)) {
          in = json.createJsonParser(getContentAsString()).parse(clazz);
        }
        R result = handler.handle(in, this, response);
        if (result != null) {
          response.setContent(converter.apply(result));
        }
        return response;
      }
    };
  }

  /**
   * Creates a handler for mocking a call to {@link #transport} which checks the authorization
   * token, parses the request object from JSON, calls a user-provided handler,
   * and returns plain text.
   *
   * @param clazz the class that the request payload is expected to represent, which will be
   *     deserialized from JSON
   * @param handler the user-provided handler to invoke
   * @param <T> the type of the request payload
   * @return an {@link Answer} to be provided as a mock to handle the corresponding HTTP requests
   * @see MockHttpTransport#buildRequest(String, String)
   */
  private <T> Answer<MockLowLevelHttpRequest> mockRequestReturningPlainText(
      Class<T> clazz, MockRequestHandler<T, String> handler) {
    return mockRequestBase(clazz, handler, r -> { return r; });
  }

  /**
   * Calls the provided {@code handler} when a request is made to start a Cloud Build build.
   *
   * @param handler the handler to call
   * @throws IOException if an error occurs while setting up the mock
   */
  public void onStartBuild(MockRequestHandler<Build, Operation> handler) throws IOException {
    when(transport.buildRequest(eq(HttpMethods.POST), contains("/v1/projects/test-project/builds")))
        .thenAnswer(mockRequest(Build.class, handler));
  }

  /**
   * Calls the provided {@code handler} when a request is made to check the status of a Cloud Build
   * build.
   *
   * @param handler the handler to call
   * @throws IOException if an error occurs while setting up the mock
   */
  public void onCheckBuild(MockRequestHandler<Void, Build> handler) throws IOException {
    when(transport.buildRequest(eq(HttpMethods.GET), contains("/v1/projects/test-project/builds/")))
        .thenAnswer(mockRequest(Void.class, handler));
  }

  /**
   * Calls the provided {@code handler} when a request is made to get a list of Cloud Storage
   * buckets.
   *
   * @param handler the handler to call
   * @throws IOException if an error occurs while setting up the mock
   */
  public void onListBuckets(MockRequestHandler<Void, Buckets> handler) throws IOException {
    when(transport.buildRequest(eq(HttpMethods.GET), contains("/storage/v1/b?")))
        .thenAnswer(mockRequest(Void.class, handler));
  }

  /**
   * Calls the provided {@code handler} when a request is made to create a Cloud Storage object.
   *
   * @param handler the handler to call
   * @throws IOException if an error occurs while setting up the mock
   */
  public void onCreateObject(MockRequestHandler<StorageObject, Void> handler) throws IOException {
    when(transport.buildRequest(eq(HttpMethods.POST), matches(".*/upload/storage/v1/b/[^/]+/o.*")))
        .thenAnswer(mockRequest(StorageObject.class, (obj, req, resp) -> {
          handler.handle(obj, req, resp);
          resp.addHeader(HttpHeaders.LOCATION, req.getUrl());
          return null;
        }));
  }

  /**
   * Calls the provided {@code handler} when a request is made to upload the contents for a Cloud
   * Storage object.
   *
   * @param handler the handler to call
   * @throws IOException if an error occurs while setting up the mock
   */
  public void onUploadObject(MockRequestHandler<Void, StorageObject> handler) throws IOException {
    when(transport.buildRequest(eq(HttpMethods.PUT), matches(".*/upload/storage/v1/b/[^/]+/o.*")))
        .thenAnswer(mockRequest(Void.class, handler));
  }

  /**
   * Calls the provided {@code handler} when a request is made to get a object
   * in a Cloud Storage bucket
   *
   * @param handler the handler to call
   * @throws IOException if an error occurs while setting up the mock
   */
  public void onGetObjectMedia(MockRequestHandler<Void, String> handler) throws IOException {
    when(transport.buildRequest(
            eq(HttpMethods.GET),
            matches(".*/storage/v1/b/[^/]+/o/.*\\?.*alt=media.*")
    ))
        .thenAnswer(mockRequestReturningPlainText(Void.class, handler));
  }

  /**
   * Registers credentials with Jenkins and prepares this mock to handle HTTP requests.
   *
   * @param jenkins the Jenkins instance to add credentials to
   * @throws Exception if an error occurs while preparing this mock
   */
  public void prepare(Jenkins jenkins) throws Exception {
    MockitoAnnotations.initMocks(this);
    ClientFactory.setDefaultTransport(transport);

    when(credentialsModule.getHttpTransport()).thenReturn(transport);
    when(credentialsModule.getJsonFactory()).thenReturn(json);

    FilePath keyFile = jenkins.getRootPath().child("key.json");
    Resources.copy(
        getClass().getResource(RESOURCE_BASE + "key.json"),
        keyFile.write());

    SystemCredentialsProvider.getInstance().getCredentials().add(
        new GoogleRobotPrivateKeyCredentials(
            "test-project",
            new JsonServiceAccountConfig(null, keyFile.toString()),
            credentialsModule));

    when(transport.buildRequest(eq(HttpMethods.POST), contains("/o/oauth2/token")))
        .thenAnswer(invocation -> new MockLowLevelHttpRequest()
            .setResponse(new MockLowLevelHttpResponse()
                .setStatusCode(HttpStatusCodes.STATUS_CODE_OK)
                .setContentType(Json.MEDIA_TYPE)
                .setContent(json.toString(new GenericJson()
                    .set("access_token", "super-secret-token")
                    .set("expires_in", 1234)
                    .set("token_type", "Bearer")))));
  }
}
