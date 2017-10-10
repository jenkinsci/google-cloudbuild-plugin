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

import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.isIn;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import com.google.jenkins.plugins.cloudbuild.context.BuildContext;
import hudson.EnvVars;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.WithoutJenkins;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link SubstitutionList}. */
public class SubstitutionListTest {
  @Mock
  private BuildContext context;

  @Before
  public void setUp() throws IOException, InterruptedException {
    MockitoAnnotations.initMocks(this);
    when(context.getListener()).thenReturn(TaskListener.NULL);
  }

  private void assertMapEquals(Map<String, String> expected, Map<String, String> actual) {
    assertThat(actual.entrySet(),
        both(everyItem(isIn(expected.entrySet())))
            .and(containsInAnyOrder(
                expected.entrySet().stream().map(Matchers::equalTo).collect(Collectors.toList()))));
  }

  private Map<String, String> toMap(SubstitutionList subs, EnvVars env)
      throws IOException, InterruptedException {
    when(context.expand(any())).thenAnswer(invocation -> env.expand(invocation.getArgument(0)));
    return subs.toMap(context);
  }

  @Test
  @WithoutJenkins
  public void toMap() throws IOException, InterruptedException {
    SubstitutionList subs;

    // Empty or null substitution lists are okay.
    subs = new SubstitutionList(Collections.emptyList());
    assertTrue(toMap(subs, new EnvVars()).isEmpty());
    assertTrue(toMap(subs, new EnvVars("FOO", "bar")).isEmpty());

    subs = new SubstitutionList(null);
    assertMapEquals(Collections.emptyMap(), toMap(subs, new EnvVars()));

    // Test with a single, simple substitution (i.e., no Jenkins variable references).
    subs = new SubstitutionList(Collections.singletonList(new Substitution("_FOO", "bar")));
    assertMapEquals(Collections.singletonMap("_FOO", "bar"), toMap(subs, new EnvVars()));
    assertMapEquals(Collections.singletonMap("_FOO", "bar"),
        toMap(subs, new EnvVars("FOO", "baz")));

    // Test substitutions whose value contains a Jenkins variable reference.
    subs = new SubstitutionList(Collections.singletonList(new Substitution("_FOO", "$FOO")));
    assertMapEquals(Collections.singletonMap("_FOO", "$FOO"), toMap(subs, new EnvVars()));
    assertMapEquals(Collections.singletonMap("_FOO", "$FOO"),
        toMap(subs, new EnvVars("BAR", "baz")));
    assertMapEquals(Collections.singletonMap("_FOO", "bar"),
        toMap(subs, new EnvVars("FOO", "bar")));
    assertMapEquals(Collections.singletonMap("_FOO", ""),
        toMap(subs, new EnvVars("FOO", "")));

    // Test substitution list with many substitutions.
    subs = new SubstitutionList(Lists.newArrayList(
        new Substitution("_FOO", "foo"),
        new Substitution("_BAR", "$BAR"),
        new Substitution("_BAZ", "baz")));
    Map<String, String> expected = new HashMap<>();
    expected.put("_FOO", "foo");
    expected.put("_BAR", "$BAR");
    expected.put("_BAZ", "baz");
    assertMapEquals(expected, toMap(subs, new EnvVars()));
    assertMapEquals(expected, toMap(subs, new EnvVars("FOO", "foo")));

    expected.put("_BAR", "bar");
    assertMapEquals(expected, toMap(subs, new EnvVars("BAR", "bar")));
  }

}