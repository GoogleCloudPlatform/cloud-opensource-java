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

import java.util.Objects;

import org.eclipse.aether.artifact.Artifact;

/**
 * Holder for a suggested dependency update. 
 */
public class Update {
  
  private final Artifact parent; 
  private final Artifact from; 
  private final Artifact to; 
 
  private Update(Artifact parent, Artifact from, Artifact to) {
    this.parent = parent;
    this.from = from;
    this.to = to;
  }
  
  @Override
  public String toString() {
    return Artifacts.toCoordinates(parent) + " needs to upgrade "
        + Artifacts.toCoordinates(from) + " to " + to.getVersion();
  }

  @Override 
  public int hashCode() {
    return Objects.hash(parent, from, to);
  }
  
  @Override
  public boolean equals(Object o) {
    if (o instanceof Update) {
      Update other = (Update) o;
      return Objects.equals(other.from, from)
          && Objects.equals(other.to, to)
          && Objects.equals(other.parent, parent);
    }
    return false;
  }
  
  static class Builder {

    private Artifact parent; 
    private Artifact from; 
    private Artifact to; 
    
    Builder setParent(Artifact parent) {
      this.parent = parent;
      return this;
    }

    Builder setFrom(Artifact from) {
      this.from = from;
      return this;
    }

    Builder setTo(Artifact to) {
      this.to = to;
      return this;
    }

    Update build() {
      return new Update(parent, from, to);
    }
    
  }

  static Builder builder() {
    return new Builder();
  }

}
