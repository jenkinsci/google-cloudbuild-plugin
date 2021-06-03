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

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/**
 * Tests for {@link ClientFactory}
 */
public class ClientFactoryTest {
  @Test
  public void getTransportWithProxy() throws Exception {
    assertNotSame(
        ClientFactory.getDefaultTransport(),
        ClientFactory.getTransportWithProxy("http://localhost:3128/")
    );
  }

  @Test
  public void getTransportWithProxyCached() throws Exception {
    assertSame(
        ClientFactory.getTransportWithProxy("http://localhost:3128/"),
        ClientFactory.getTransportWithProxy("http://localhost:3128/")
    );
  }

  @Test
  public void getTransportWithProxyNull() throws Exception {
    assertSame(
        ClientFactory.getDefaultTransport(),
        ClientFactory.getTransportWithProxy(null)
    );
  }

  @Test
  public void getTransportWithProxyEmpty() throws Exception {
    assertSame(
        ClientFactory.getDefaultTransport(),
        ClientFactory.getTransportWithProxy(null)
    );
  }

  @Test
  public void getTransportWithProxyInvalid() throws Exception {
    assertSame(
        ClientFactory.getDefaultTransport(),
        ClientFactory.getTransportWithProxy("badproxyformat")
    );
  }

  @Test
  public void getTransportWithProxyInvlidHost() throws Exception {
    assertSame(
        ClientFactory.getDefaultTransport(),
        ClientFactory.getTransportWithProxy(
            "http://underscore_is_not_valid_hostname:8080/"
        )
    );
  }
}
