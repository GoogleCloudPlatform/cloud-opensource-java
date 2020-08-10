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

package com.google.cloud.tools.opensource.classpath;

import java.util.Objects;

/** Symbol for a class. */
public class ClassSymbol extends Symbol {
  public ClassSymbol(String className) {
    super(className);
  }

  @Override
  public String toString() {
    return "Class " + getClassBinaryName();
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(this.getClassBinaryName());
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    Symbol symbol = (Symbol) other;
    return this.getClassBinaryName().equals(symbol.getClassBinaryName());
  }
}
