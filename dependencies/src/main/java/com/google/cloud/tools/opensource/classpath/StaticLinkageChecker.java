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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.DependencyGraph;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.commons.cli.ParseException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * A tool to find static linkage errors for a class path.
 */
class StaticLinkageChecker {
  // TODO(suztomo): enhance scope to include fields and classes. Issue #207

  private static final Logger logger = Logger.getLogger(StaticLinkageChecker.class.getName());

  static StaticLinkageChecker create(
      boolean reportOnlyReachable, List<Path> jarFilePaths, Iterable<Path> entryPoints)
      throws IOException, ClassNotFoundException {
    checkArgument(
        !jarFilePaths.isEmpty(),
        "The linkage classpath is empty. Specify input to supply one or more jar files");
    return new StaticLinkageChecker(
        reportOnlyReachable, ClassDumper.create(jarFilePaths), entryPoints);
  }

  /**
   * Flag on the reachability. This flag controls whether the report excludes the linkage errors
   * on classes that are not reachable from the entry points of the class usage graph.
   */
  private final boolean reportOnlyReachable;

  private final ClassDumper classDumper;

  private final ImmutableSet<Path> entryPoints;

  StaticLinkageChecker(
      boolean reportOnlyReachable, ClassDumper classDumper, Iterable<Path> entryPoints) {
    this.reportOnlyReachable = reportOnlyReachable;
    this.classDumper = classDumper;
    this.entryPoints = ImmutableSet.copyOf(entryPoints);
  }

  /**
   * Given Maven coordinates or list of the jar files as file names in filesystem, outputs the
   * report of static linkage check.
   *
   * @throws IOException when there is a problem in reading a jar file
   * @throws ClassNotFoundException when there is a problem in reading a class from a jar file
   * @throws RepositoryException when there is a problem in resolving the Maven coordinates to jar
   *     files
   * @throws ParseException when the arguments are invalid for the tool
   */
  public static void main(String[] arguments)
      throws IOException, ClassNotFoundException, RepositoryException, ParseException {
    StaticLinkageCheckOption commandLineOption = StaticLinkageCheckOption.parseArguments(arguments);
    ImmutableList<Path> inputClasspath =
        generateInputClasspathFromLinkageCheckOption(commandLineOption);
    // TODO(suztomo): to take command-line option to choose entry point classes for reachability
    ImmutableSet<Path> entryPoints = ImmutableSet.of(inputClasspath.get(0));
    StaticLinkageChecker staticLinkageChecker =
        create(commandLineOption.isReportOnlyReachable(), inputClasspath, entryPoints);

    StaticLinkageCheckReport report = staticLinkageChecker.findLinkageErrors();

    printStaticLinkageReport(report);
  }

  private static void printStaticLinkageReport(StaticLinkageCheckReport report) {
    for (JarLinkageReport jarLinkageReport : report.getJarLinkageReports()) {
      int totalErrors =
          jarLinkageReport.getMissingClassErrors().size()
              + jarLinkageReport.getMissingMethodErrors().size()
              + jarLinkageReport.getMissingFieldErrors().size();
      System.out.println(
          jarLinkageReport.getJarPath().getFileName() + "(" + totalErrors + " errors):");
      String indent = "  ";
      for (LinkageErrorMissingClass missingClass : jarLinkageReport.getMissingClassErrors()) {
        System.out.println(indent + missingClass.getReference());
      }
      for (LinkageErrorMissingMethod missingMethod : jarLinkageReport.getMissingMethodErrors()) {
        System.out.println(indent + missingMethod.getReference());
      }
      for (LinkageErrorMissingField missingField : jarLinkageReport.getMissingFieldErrors()) {
        System.out.println(indent + missingField.getReference());
      }
    }
  }

  /**
   * Resolves command line option to list of jar files as input class path for static linkage
   * checker.
   *
   * @param linkageCheckOption option through command-line arguments
   * @return input class path resolved as a list of absolute paths to jar files
   * @throws RepositoryException when there is a problem in resolving the Maven coordinates to jar
   */
  @VisibleForTesting
  static ImmutableList<Path> generateInputClasspathFromLinkageCheckOption(
      StaticLinkageCheckOption linkageCheckOption) throws RepositoryException {
    List<Artifact> artifacts =
        linkageCheckOption
            .getArtifacts()
            .stream()
            .map(DefaultArtifact::new)
            .collect(Collectors.toList());

    String bomCoordinates = linkageCheckOption.getBom();
    if (bomCoordinates != null) {
      DefaultArtifact bom = new DefaultArtifact(bomCoordinates);
      artifacts.addAll(RepositoryUtility.readBom(bom));
    }

    ImmutableList.Builder<Path> jarFileBuilder = ImmutableList.builder();
    jarFileBuilder.addAll(linkageCheckOption.getJarFiles());
    jarFileBuilder.addAll(artifactsToClasspath(artifacts));
    return jarFileBuilder.build();
  }

  /**
   * Finds jar file paths for the dependencies of the Maven artifacts.
   *
   * @param artifacts Maven artifacts to check its dependencies
   * @return list of absolute paths to jar files
   * @throws RepositoryException when there is a problem in retrieving jar files
   */
  @VisibleForTesting
  static ImmutableList<Path> artifactsToClasspath(List<Artifact> artifacts)
      throws RepositoryException {
    // dependencyGraph holds multiple versions for one artifact key (groupId:artifactId)
    DependencyGraph dependencyGraph =
        DependencyGraphBuilder.getStaticLinkageCheckDependencies(artifacts);
    List<DependencyPath> dependencyPaths = dependencyGraph.list();

    // Removes duplicates on (groupId:artifactId)
    Set<String> artifactInPaths = new HashSet<>();

    return dependencyPaths
        .stream()
        .map(DependencyPath::getLeaf)
        .filter(artifact -> artifactInPaths.add(Artifacts.makeKey(artifact)))
        .map(Artifact::getFile)
        .map(File::toPath)
        .map(Path::toAbsolutePath)
        .filter(path -> path.toString().endsWith(".jar"))
        .collect(toImmutableList());
  }

  /**
   * Finds linkage errors in the input classpath and generates a static linkage check report.
   */
  StaticLinkageCheckReport findLinkageErrors() throws ClassNotFoundException, IOException {
    ImmutableList<Path> jarFilePaths = classDumper.getInputClasspath();

    ImmutableMap.Builder<Path, SymbolReferenceSet> jarToSymbols = ImmutableMap.builder();
    for (Path jarPath : jarFilePaths) {
      jarToSymbols.put(jarPath, ClassDumper.scanSymbolReferencesInJar(jarPath));
    }

    // Validate linkage error of each reference
    ImmutableList.Builder<JarLinkageReport> jarLinkageReports = ImmutableList.builder();
    for (Map.Entry<Path, SymbolReferenceSet> entry : jarToSymbols.build().entrySet()) {
      jarLinkageReports.add(generateLinkageReport(entry.getKey(), entry.getValue()));
    }

    if (reportOnlyReachable) {
      // TODO: Optionally, report errors only reachable from entry point classes
      logger.warning("reportOnlyReachable is not yet implemented");
      throw new UnsupportedOperationException("reportOnlyReachable is not yet implemented");
    }

    return StaticLinkageCheckReport.create(jarLinkageReports.build());
  }

  /**
   * Generates a linkage report for a jar file, by checking linkage errors in the symbol
   * references against the input class path.
   *
   * @param jarPath absolute path to the jar file
   * @param symbolReferenceSet symbol references from {@code jarPath} to check its linkage errors
   * @return linkage report for the jar file, which includes linkage errors if any
   */
  @VisibleForTesting
  JarLinkageReport generateLinkageReport(Path jarPath, SymbolReferenceSet symbolReferenceSet) {
    JarLinkageReport.Builder reportBuilder = JarLinkageReport.builder().setJarPath(jarPath);

    // Because the Java compiler ensures that there are no static linkage errors between classes
    // defined in the same jar file, this validation excludes reference within the same jar file.
    ImmutableSet<String> classesDefinedInJar = classDumper.classesDefinedInJar(jarPath);

    reportBuilder.setMissingMethodErrors(
        symbolReferenceSet
            .getMethodReferences()
            .stream()
            .filter(reference -> !classesDefinedInJar.contains(reference.getTargetClassName()))
            .map(this::checkLinkageErrorAt)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(toImmutableList()));

    // TODO(#243 and #242): implement validation for class and field references in the table
    reportBuilder
        .setMissingClassErrors(ImmutableList.of())
        .setMissingFieldErrors(ImmutableList.of());

    return reportBuilder.build();
  }

  /**
   * Returns an {@code Optional} describing the linkage error for the method reference if the
   * reference does not have a valid referent in the input class path; otherwise an empty {@code
   * Optional}.
   */
  private Optional<LinkageErrorMissingMethod> checkLinkageErrorAt(MethodSymbolReference reference) {
    if (validateMethodReference(reference)) {
      return Optional.empty();
    }
    return Optional.of(LinkageErrorMissingMethod.errorAt(reference));
  }

  /**
   * Returns true if the method reference has a valid referent in the classpath via Java class
   * loader.
   */
  private boolean validateMethodReferenceByClassLoader(MethodSymbolReference methodReference) {
    String className = methodReference.getTargetClassName();
    String methodName = methodReference.getMethodName();
    try {
      Class[] parameterTypes = classDumper.methodDescriptorToClass(methodReference.getDescriptor());
      Class clazz = className.startsWith("[") ? Array.class : classDumper.loadClass(className);
      if ("<init>".equals(methodName)) {
        clazz.getConstructor(parameterTypes);
      } else if ("clone".equals(methodName) && clazz == Array.class) {
        // Array's clone method is not returned by getMethod
        // https://docs.oracle.com/javase/8/docs/api/java/lang/Class.html#getMethod-java.lang.String-java.lang.Class...-
        return true;
      } else {
        clazz.getMethod(methodName, parameterTypes);
      }
      return true;
    } catch (NoSuchMethodException | ClassNotFoundException ex) {
      return false;
    }
  }

  /**
   * Returns true if the method reference has a valid referent in the classpath via BCEL API.
   */
  private boolean validateMethodReferenceByBcelRepository(MethodSymbolReference methodReference) {
    String className = methodReference.getTargetClassName();
    String methodName = methodReference.getMethodName();
    try {
      JavaClass javaClass = classDumper.loadJavaClass(className);
      while (javaClass != null) {
        Method[] methods = javaClass.getMethods();
        for (Method methodInJavaClass : methods) {
          String methodNameInJavaClass = methodInJavaClass.getName();
          String descriptorInJavaClass = methodInJavaClass.getSignature();
          if (methodNameInJavaClass.equals(methodName)
              && descriptorInJavaClass.equals(methodReference.getDescriptor())) {
            return true;
          }
        }
        // null if java.lang.Object
        javaClass = javaClass.getSuperClass();
      }
      return false;
    } catch (ClassNotFoundException ex) {
      return false;
    }
  }

  /**
   * Returns true if the method reference has a valid referent in the classpath.
   */
  private boolean validateMethodReference(MethodSymbolReference methodReference) {
    // Attempt 1: Find the class and method via the BCEL synthetic repository in ClassDumper.
    // BCEL API helps to search availability of (package) private class, constructors and
    // methods that are inaccessible to Java's reflection API or the class loader.

    // Attempt 2: Find the class and method via the class loader of the input class path
    // in ClassDumper. Class loaders help to resolve methods defined in Java built-in classes.
    // TODO(#253): check accessor to verify source class has valid access to the symbol
    return validateMethodReferenceByBcelRepository(methodReference)
        || validateMethodReferenceByClassLoader(methodReference);
  }
}
