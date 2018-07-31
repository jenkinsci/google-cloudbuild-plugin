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

import java.io.IOException;
import java.security.GeneralSecurityException;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudbuild.v1.CloudBuild;
import com.google.api.services.storage.Storage;
import com.google.jenkins.plugins.cloudbuild.CloudBuildScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import hudson.AbortException;
import hudson.model.Run;
import hudson.model.TaskListener;

/** Creates clients for communicating with Google APIs. */
public class ClientFactory {
  public static final String APPLICATION_NAME = "cloud-build-plugin";

  private static HttpTransport DEFAULT_TRANSPORT;

  private final Run<?, ?> run;
  private final TaskListener listener;
  private final HttpTransport transport;
  private final JsonFactory jsonFactory;
  private final GoogleRobotCredentials credentials;
  private final HttpRequestInitializer gcred;

  public ClientFactory(Run<?, ?> run, TaskListener listener, String credentialsId)
      throws IOException {
    if (credentialsId == null) {
      throw new IllegalArgumentException(Messages.ClientFactory_CredentialsIdRequired());
    }
    this.run = run;
    this.listener = listener;

    try {
      this.transport = getDefaultTransport();
    } catch (GeneralSecurityException e) {
      throw new AbortException(
          Messages.ClientFactory_FailedToInitializeHTTPTransport(e.getMessage()));
    }
    this.jsonFactory = new JacksonFactory();

    CloudBuildScopeRequirement requirement = new CloudBuildScopeRequirement();
    this.credentials = CredentialsProvider.findCredentialById(
        credentialsId, GoogleRobotCredentials.class, run, requirement);
    if (credentials == null) {
      throw new AbortException(Messages.ClientFactory_FailedToRetrieveCredentials(credentialsId));
    }
    try {
      this.gcred = credentials.getGoogleCredential(requirement);
    } catch (GeneralSecurityException e) {
      throw new AbortException(
          Messages.ClientFactory_FailedToRetrieveGoogleCredentials(
              credentialsId,
              e.getMessage()));
    }
  }

  private static synchronized HttpTransport getDefaultTransport()
      throws GeneralSecurityException, IOException {
    if (DEFAULT_TRANSPORT == null) {
      DEFAULT_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    }
    return DEFAULT_TRANSPORT;
  }

  public static synchronized void setDefaultTransport(HttpTransport transport) {
    DEFAULT_TRANSPORT = transport;
  }

  public CloudBuildClient cloudBuild() {
    return new CloudBuildClient(
        new CloudBuild.Builder(transport, jsonFactory, gcred)
            .setRootUrl("https://cloudbuild.googleapis.com/")
            .setApplicationName(APPLICATION_NAME)
            .build(),
        credentials.getProjectId(), run, listener);
  }

  public CloudStorageClient storage() {
    return new CloudStorageClient(
        new Storage.Builder(transport, jsonFactory, gcred)
            .setApplicationName(APPLICATION_NAME)
            .build(),
        credentials.getProjectId(), listener);
  }
}
