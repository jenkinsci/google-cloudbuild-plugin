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

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

/**
 * Helper methods for parsing and formatting {@link Duration}s.
 */
public class DurationUtil {

  /**
   * Formats the provided {@link Duration} as a decimal number of seconds followed by {@code s}.
   * For example, {@code PT3H14M15.9S} would be formatted as {@code "11655.900000000s"}.
   *
   * @param d the {@link Duration} to format
   * @return a string indicating the number of seconds that {@code d} represents
   */
  public static String toDecimalSecondsString(Duration d) {
    if (d.getNano() == 0) {
      return String.format("%ds", d.getSeconds());
    } else {
      return String.format("%d.%09ds", d.getSeconds(), d.getNano());
    }
  }

  /**
   * A pattern representing the value of the "seconds" segment only from an ISO-8601 duration (i.e.,
   * a decimal number and optional fractional part).
   */
  private static final Pattern SECONDS_ONLY_PATTERN = Pattern.compile("\\d+(?:[,\\.]\\d+)?");

  /**
   * Parses a {@link Duration} provided in a human-readable form.
   *
   * @param s the string representing the human-readable form of a duration. This may be an ISO-8601
   *     duration as parsed by {@link Duration#parse(CharSequence)}. It may be a string of the form
   *     [n]H[n]M[n.nnn]S (which is interpreted as if it were {@code "PT" + s}), or it may be a
   *     string representing a decimal number (interpreted as {@code "PT" + s + "S"}).
   * @return the successfully-parsed {@link Duration}, or {@code null} if {@code s} could not be
   *     parsed
   * @see Duration#parse(CharSequence)
   */
  public static Duration parseOrNull(@Nonnull String s) {
    s = s.toUpperCase();
    Duration duration = parseISO8601DurationOrNull(s);
    if (duration != null) {
      return duration;
    }
    duration = parseISO8601DurationOrNull("PT" + s);
    if (duration != null) {
      return duration;
    }

    // Check that the input is a valid decimal value before attempting to parse "PT<s>S", so that we
    // don't accept input such as "3h14".
    if (SECONDS_ONLY_PATTERN.matcher(s).matches()) {
      duration = parseISO8601DurationOrNull("PT" + s + "S");
      if (duration != null) {
        return duration;
      }
    }
    return null;
  }

  /**
   * Tries to parse a {@link Duration}, returning {@code null} if the operation fails.
   *
   * @param value the string to parse as a {@link Duration}
   * @return the successfully-parsed {@link Duration}, or {@code null} if parsing failed
   * @see Duration#parse(CharSequence)
   */
  private static Duration parseISO8601DurationOrNull(String value) {
    try {
      return Duration.parse(value);
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
