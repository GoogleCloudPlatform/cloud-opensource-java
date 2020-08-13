/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.opensource.classpath;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.common.collect.ImmutableList;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.junit.Test;

public class MemoryUsageTest {
  private ClassPathBuilder classPathBuilder = new ClassPathBuilder();
  
  @Test
  public void testSpringCloudGcpDependenciesBomOutOfMemoryError() throws ArtifactDescriptorException {
    
    long before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

    Bom bom = Bom.readBom("org.springframework.cloud:spring-cloud-gcp-dependencies:1.2.4.RELEASE");

    try {
      ClassPathResult result = classPathBuilder.resolve(bom.getManagedDependencies(), false);
      long after = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
      double mb = (after - before) / (1024.0 * 1024.0);
      System.err.println("Memory used: " + mb + "MB");      
      
      assertNotNull(result);
    } catch (OutOfMemoryError failure) {
      failure.printStackTrace();
      fail("Ran out of memory");
    } finally {
      System.gc();      
    }
  }
  
}
