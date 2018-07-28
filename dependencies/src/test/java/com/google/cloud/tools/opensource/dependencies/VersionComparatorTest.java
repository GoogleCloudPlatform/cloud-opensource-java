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

package com.google.cloud.tools.opensource.dependencies;

import org.junit.Assert;
import org.junit.Test;

public class VersionComparatorTest {

  private VersionComparator comparator = new VersionComparator();
  
  @Test
  public void testGuava() {
    Assert.assertTrue(comparator.compare("20.0","10.0") > 0);
    Assert.assertTrue(comparator.compare("20.0.1","20.0") > 0);
    Assert.assertTrue(comparator.compare("20.0.1","20.0.0") > 0);
    Assert.assertTrue(comparator.compare("25.1-jre","25.1-android") > 0);
  }  
}
