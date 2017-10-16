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

import java.time.Duration;
import org.junit.Test;

/** Tests for {@link DurationUtil}. */
public class DurationUtilTest {
  @Test
  public void toDecimalSecondsString() throws Exception {
    assertEquals("36000s", DurationUtil.toDecimalSecondsString(Duration.parse("PT10H")));
    assertEquals("600s", DurationUtil.toDecimalSecondsString(Duration.parse("PT10M")));
    assertEquals("10s", DurationUtil.toDecimalSecondsString(Duration.parse("PT10S")));
    assertEquals("11655.900000000s",
        DurationUtil.toDecimalSecondsString(Duration.parse("PT3H14M15.9S")));
    assertEquals("3.141592653s",
        DurationUtil.toDecimalSecondsString(Duration.parse("PT3.141592653S")));
    assertEquals("3s", DurationUtil.toDecimalSecondsString(Duration.parse("PT3S")));
    assertEquals("310509.260000000s",
        DurationUtil.toDecimalSecondsString(Duration.parse("P3DT14H15M9.26S")));
  }

  @Test
  public void parseOrNull() throws Exception {
    assertEquals(Duration.parse("PT10H"), DurationUtil.parseOrNull("10h"));
    assertEquals(Duration.parse("PT10M"), DurationUtil.parseOrNull("10m"));
    assertEquals(Duration.parse("PT10S"), DurationUtil.parseOrNull("10s"));
    assertEquals(Duration.parse("PT3H14M15.9S"), DurationUtil.parseOrNull("3h14m15.9s"));
    assertEquals(Duration.parse("PT3.141592653S"), DurationUtil.parseOrNull("3.141592653"));
    assertEquals(Duration.parse("PT3S"), DurationUtil.parseOrNull("3"));
    assertEquals(Duration.parse("PT10M"), DurationUtil.parseOrNull("600"));
    assertEquals(Duration.parse("P3DT14H15M9.26S"), DurationUtil.parseOrNull("P3DT14H15M9.26S"));

    assertEquals(null, DurationUtil.parseOrNull("4.1h"));
    assertEquals(null, DurationUtil.parseOrNull("1.2.3"));
    assertEquals(null, DurationUtil.parseOrNull("foo"));

    // If the implementation blindly fell back to s -> "PT" + s + "S", this would be accepted as
    // equivalent to "PT3H14S".
    assertEquals(null, DurationUtil.parseOrNull("3h14"));
  }
}