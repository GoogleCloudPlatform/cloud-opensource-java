/*
 * Copyright 2021 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.opensource.dashboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.util.List;
import org.junit.Test;

public class CoordinatesExtractorTest {

  @Test
  public void testProcessLine() throws IOException {
    CoordinatesExtractor extractor = new CoordinatesExtractor();
    assertTrue(extractor.processLine("some random junk"));
    assertTrue(extractor.getResult().isEmpty());
  }

  @Test
  public void testProcessLine_withCoordinates() throws IOException {
    CoordinatesExtractor extractor = new CoordinatesExtractor();
    assertTrue(extractor.processLine(
        "[INFO]    com.google.guava:guava:jar:30.1.1-android:compile"));
    List<String> result = extractor.getResult();
    assertFalse(result.isEmpty());
    assertEquals("com.google.guava:guava:jar:30.1.1-android", result.get(0));
  }

}
