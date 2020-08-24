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

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import org.eclipse.aether.artifact.Artifact;

/**
 * {@code implementationClass} does not implement the abstract method {@code methodSymbol} declared
 * by {@code supertype} (an interface or an abstract class). Such unimplemented methods manifest as
 * {@link AbstractMethodError}s at runtime.
 */
final class AbstractMethodProblem extends LinkageProblem {
  MethodSymbol methodSymbol;

  AbstractMethodProblem(
      ClassFile implementationClass, MethodSymbol methodSymbol, ClassFile supertype) {

    // implementationClass is the source of the invalid symbolic reference, and supertype is the
    // target of the symbolic reference.
    super(
        " does not exist in the implementing class", implementationClass, methodSymbol, supertype);
    this.methodSymbol = methodSymbol;
  }

  @Override
  public final String toString() {
    ClassFile implementationClass = getSourceClass();
    ClassPathEntry sourceClassPathEntry = implementationClass.getClassPathEntry();
    ClassFile supertype = getTargetClass();
    return String.format(
        "%s (in %s) does not implement %s, required by %s (in %s)",
        implementationClass.getBinaryName(),
        sourceClassPathEntry,
        methodSymbol.getMethodNameWithSignature(),
        supertype.getBinaryName(),
        supertype.getClassPathEntry());
  }

  @Override
  String describe(DependencyConflict conflict) {
    DependencyPath pathToSelectedArtifact = conflict.getPathToSelectedArtifact();
    Artifact selected = pathToSelectedArtifact.getLeaf();
    String selectedCoordinates = Artifacts.toCoordinates(selected);
    DependencyPath pathToArtifactThruSource = conflict.getPathToArtifactThruSource();
    Artifact unselected = pathToArtifactThruSource.getLeaf();
    String unselectedCoordinates = Artifacts.toCoordinates(unselected);
    ClassFile supertype = getTargetClass();

    return "Dependency conflict: "
        + selectedCoordinates
        + " defines incompatible version of "
        + supertype.getBinaryName()
        + " but "
        + unselectedCoordinates
        + " defines compatible one.\n"
        + "  selected: "
        + pathToSelectedArtifact
        + "\n  unselected: "
        + pathToArtifactThruSource;
  }
}
