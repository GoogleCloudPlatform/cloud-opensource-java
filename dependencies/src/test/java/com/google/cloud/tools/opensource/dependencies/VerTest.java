/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.opensource.dependencies;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.junit.Assert;
import org.junit.Test;

public class VerTest {

  @Test
  public void testVersion() {
    ComparableVersion sts = new ComparableVersion("1.38.0");
    ComparableVersion lts = new ComparableVersion("1.38.0.1");
    Assert.assertTrue(sts.compareTo(lts) < 0);
    Assert.assertTrue(lts.compareTo(new ComparableVersion("1.38.0.2")) < 0);
    Assert.assertTrue(new ComparableVersion("1.38.0.1").compareTo(new ComparableVersion("1.39.0")) < 0);
  }

  @Test
  public void testTwoDigits() {
    ComparableVersion lesser = new ComparableVersion("1.38.9.1");
    ComparableVersion greater = new ComparableVersion("1.38.20.1");
    Assert.assertTrue(lesser.compareTo(greater) < 0);
  }
  
  @Test
  public void testTwoDigits_2() {
    ComparableVersion lesser = new ComparableVersion("1.38.10");
    ComparableVersion greater = new ComparableVersion("1.38.20.1");
    Assert.assertTrue(lesser.compareTo(greater) < 0);
  }
  
  @Test
  public void testTwoDigits_3() {
    ComparableVersion lesser = new ComparableVersion("1.0.9.3");
    ComparableVersion greater = new ComparableVersion("1.0.10.2");
    Assert.assertTrue(lesser.compareTo(greater) < 0);
  }
 
}
