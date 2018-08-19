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
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.google.api.client.googleapis.GoogleUtils;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
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
  private static final Logger LOG = Logger.getLogger(ClientFactory.class.getName());

  private static HttpTransport DEFAULT_TRANSPORT;
  @Nonnull
  private static Map<String, HttpTransport> proxyTransportCache
      = new HashMap<>();

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

  /*package*/ static synchronized HttpTransport getDefaultTransport()
      throws GeneralSecurityException, IOException {
    if (DEFAULT_TRANSPORT == null) {
      DEFAULT_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    }
    return DEFAULT_TRANSPORT;
  }

  /*package*/ static synchronized HttpTransport getTransportWithProxy(@CheckForNull String proxy)
      throws GeneralSecurityException, IOException {
    if (proxy == null || proxy.isEmpty()) {
      return getDefaultTransport();
    }
    if (proxyTransportCache.containsKey(proxy)) {
      return proxyTransportCache.get(proxy);
    }

    URI proxyUri;
    try {
      proxyUri = new URI(proxy);
    } catch (URISyntaxException e) {
      LOG.log(
          Level.WARNING,
          String.format("Invalid proxy. ignored: %s", proxy),
          e
      );
      return getDefaultTransport();
    }
    if (
        proxyUri.getScheme() == null
        || !"http".equals(proxyUri.getScheme().toLowerCase())
        || proxyUri.getHost() == null
    ) {
      LOG.log(
          Level.WARNING,
          "Invalid proxy. ignored: {0}",
          proxy
        );
        return getDefaultTransport();
    }
    HttpTransport transport = new NetHttpTransport.Builder()
        .trustCertificates(GoogleUtils.getCertificateTrustStore())
        .setProxy(new Proxy(
            Proxy.Type.HTTP,
            new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort())))
        .build();
    proxyTransportCache.put(proxy, transport);
    return transport;
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

  /**
   * @param proxy proxy to use in "http://..." format. can be null or empty.
   * @return Client for Cloud Build with the proxy.
   * @throws IOException error in certificate store handling
   * @since 0.3
   */
  public CloudBuildClient cloudBuild(@CheckForNull String proxy)
      throws IOException {
    try {
      return new CloudBuildClient(
          new CloudBuild.Builder(
              getTransportWithProxy(proxy),
              jsonFactory,
              gcred
          )
              .setRootUrl("https://cloudbuild.googleapis.com/")
              .setApplicationName(APPLICATION_NAME)
              .build(),
          credentials.getProjectId(), run, listener);
    } catch (GeneralSecurityException e) {
      throw new AbortException(
          Messages.ClientFactory_FailedToInitializeHTTPTransport(e.getMessage()));
    }
  }

  public CloudStorageClient storage() {
    return new CloudStorageClient(
        new Storage.Builder(transport, jsonFactory, gcred)
            .setApplicationName(APPLICATION_NAME)
            .build(),
        credentials.getProjectId(), listener);
  }

  /**
   * @param proxy proxy to use in "http://..." format. can be null or empty.
   * @return Client for Cloud Storage with the proxy.
   * @throws IOException error in certificate store handling
   * @since 0.3
   */
  public CloudStorageClient storage(@CheckForNull String proxy)
      throws IOException {
    try {
      return new CloudStorageClient(
          new Storage.Builder(
              getTransportWithProxy(proxy),
              jsonFactory,
              gcred
          )
              .setApplicationName(APPLICATION_NAME)
              .build(),
          credentials.getProjectId(), listener);
    } catch (GeneralSecurityException e) {
      throw new AbortException(
          Messages.ClientFactory_FailedToInitializeHTTPTransport(e.getMessage()));
    }
  }
}
