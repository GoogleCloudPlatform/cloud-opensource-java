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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.eclipse.aether.RepositoryException;

/** Exception aggregating one or more underlying {@link RepositoryException}s. */
public class AggregatedRepositoryException extends RepositoryException {

  private final ImmutableList<ExceptionAndPath> underlyingFailures;

  AggregatedRepositoryException(List<ExceptionAndPath> failures) {
    super("There were failure(s) in dependency resolution");
    this.underlyingFailures = ImmutableList.copyOf(failures);
  }
  
  @Override
  public String getMessage() {
    StringBuilder builder = new StringBuilder(super.getMessage());
    builder.append("\n");

    for (ExceptionAndPath exceptionAndPath : underlyingFailures) {
      builder.append(Joiner.on(" / ").join(exceptionAndPath.getPath()));
      builder.append(": ");
      builder.append(exceptionAndPath.getException());
      builder.append("\n");
    }

    return builder.toString();
  }
}
