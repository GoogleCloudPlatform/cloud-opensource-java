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

import static com.google.cloud.tools.opensource.classpath.SymbolNotResolvable.fromSymbolProblem;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import java.nio.file.Path;
import java.util.List;

/**
 * The result of a linkage check.
 */
@AutoValue
public abstract class LinkageCheckReport {

  public abstract ImmutableList<JarLinkageReport> getJarLinkageReports();

  @VisibleForTesting
  public static LinkageCheckReport create(Iterable<JarLinkageReport> jarLinkageReports) {
    return new AutoValue_LinkageCheckReport(ImmutableList.copyOf(jarLinkageReports));
  }
  
  public String getErrorString() {
    StringBuilder builder = new StringBuilder();
    for (JarLinkageReport jarLinkageReport : getJarLinkageReports()) {
      if (jarLinkageReport.getErrorCount() > 0) {
        builder.append(jarLinkageReport.getErrorString());
        builder.append('\n');
      }
    }
    
    String result = builder.toString();
    if (result.isEmpty()) {
      return "No linkage errors\n";
    }
    return result;
  }

  static LinkageCheckReport fromSymbolProblems(
      ImmutableSetMultimap<ClassFile, SymbolProblem> symbolProblems,
      List<Path> jars,
      ClassReferenceGraph reachableClasses) {
    // TODO(#574): This method will be removed once the refactoring is done.
    ImmutableList.Builder<JarLinkageReport> linkageReportBuilder = ImmutableList.builder();

    ImmutableSetMultimap.Builder<Path, SymbolNotResolvable<ClassSymbolReference>>
        classSymbolProblemsBuilder = ImmutableSetMultimap.builder();
    ImmutableSetMultimap.Builder<Path, SymbolNotResolvable<MethodSymbolReference>>
        methodSymbolProblemsBuilder = ImmutableSetMultimap.builder();
    ImmutableSetMultimap.Builder<Path, SymbolNotResolvable<FieldSymbolReference>>
        fieldSymbolProblemsBuilder = ImmutableSetMultimap.builder();

    symbolProblems.forEach(
        (classFile, symbolProblem) -> {
          Path jar = classFile.getJar();
          boolean isReachable = reachableClasses.isReachable(classFile.getClassName());
          Symbol symbol = symbolProblem.getSymbol();

          Path targetClassLocation = null;
          if (symbolProblem.getContainingClass() != null) {
            targetClassLocation = symbolProblem.getContainingClass().getJar();
          }
          if (symbol instanceof ClassSymbol) {
            ClassSymbolReference classSymbolReference =
                ClassSymbolReference.fromSymbol(classFile, (ClassSymbol) symbol);
            SymbolNotResolvable<ClassSymbolReference> symbolNotResolvable =
                fromSymbolProblem(
                    classSymbolReference, symbolProblem, targetClassLocation, isReachable);
            classSymbolProblemsBuilder.put(jar, symbolNotResolvable);
          } else if (symbol instanceof MethodSymbol) {
            MethodSymbolReference methodSymbolReference =
                MethodSymbolReference.fromSymbol(classFile, (MethodSymbol) symbol);
            SymbolNotResolvable<MethodSymbolReference> symbolNotResolvable =
                fromSymbolProblem(
                    methodSymbolReference, symbolProblem, targetClassLocation, isReachable);
            methodSymbolProblemsBuilder.put(jar, symbolNotResolvable);
          } else if (symbol instanceof FieldSymbol) {
            FieldSymbolReference fieldSymbolReference =
                FieldSymbolReference.fromSymbol(classFile, (FieldSymbol) symbol);
            SymbolNotResolvable<FieldSymbolReference> symbolNotResolvable =
                fromSymbolProblem(
                    fieldSymbolReference, symbolProblem, targetClassLocation, isReachable);
            fieldSymbolProblemsBuilder.put(jar, symbolNotResolvable);
          }
        });

    ImmutableSetMultimap<Path, SymbolNotResolvable<ClassSymbolReference>> jarToClassSymbolProblems =
        classSymbolProblemsBuilder.build();
    ImmutableSetMultimap<Path, SymbolNotResolvable<MethodSymbolReference>>
        jarToMethodSymbolProblems = methodSymbolProblemsBuilder.build();
    ImmutableSetMultimap<Path, SymbolNotResolvable<FieldSymbolReference>> jarToFieldSymbolProblems =
        fieldSymbolProblemsBuilder.build();

    for (Path jar : jars) {
      linkageReportBuilder.add(
          JarLinkageReport.builder()
              .setJarPath(jar)
              .setMissingClassErrors(jarToClassSymbolProblems.get(jar))
              .setMissingMethodErrors(jarToMethodSymbolProblems.get(jar))
              .setMissingFieldErrors(jarToFieldSymbolProblems.get(jar))
              .build());
    }

    return create(linkageReportBuilder.build());
  }
}
