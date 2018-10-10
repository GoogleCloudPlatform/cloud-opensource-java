/*
 * Copyright 2018 Google LLC.
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

import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Assert;
import org.junit.Test;

public class ArtifactResultsTest {
  
  private ArtifactResults results =
      new ArtifactResults(new DefaultArtifact("com.google.guava:guava:23.0"));  
  
  @Test
  public void testAddResult() {
    results.addResult("foo", 0);
    results.addResult("bar", 10);
    Assert.assertTrue(results.getResult("foo"));    
    Assert.assertFalse(results.getResult("bar"));
    Assert.assertNull(results.getResult("baz"));
  }
  
  @Test
  public void testGetFailureCount() {
    results.addResult("foo", 0);
    results.addResult("bar", 10);
    Assert.assertEquals(0, results.getFailureCount("foo"));    
    Assert.assertEquals(10, results.getFailureCount("bar"));    
    Assert.assertEquals(0, results.getFailureCount("baz"));
  }
  
  @Test
  public void testGetCoordinates() {
    Assert.assertEquals("com.google.guava:guava:23.0", results.getCoordinates());
  }

}
