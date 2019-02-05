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

package com.google.cloud.tools.opensource.classpath;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * The result of a classpath check.
 */
@AutoValue
public abstract class ClasspathCheckReport {

  public abstract ImmutableList<JarLinkageReport> getJarLinkageReports();

  @VisibleForTesting
  public static ClasspathCheckReport create(Iterable<JarLinkageReport> jarLinkageReports) {
    return new AutoValue_ClasspathCheckReport(ImmutableList.copyOf(jarLinkageReports));
  }
  
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    for (JarLinkageReport jarLinkageReport : getJarLinkageReports()) {
      if (jarLinkageReport.getTotalErrorCount() > 0) {
        builder.append(jarLinkageReport.toString());
        builder.append('\n');
      }
    }
    
    String result = builder.toString();
    if (result.isEmpty()) {
      return "No static linkage errors\n";
    }
    return result;
  }
}
