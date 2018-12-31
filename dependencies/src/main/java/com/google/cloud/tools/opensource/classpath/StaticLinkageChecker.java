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

import static com.google.cloud.tools.opensource.classpath.ClassDumper.getClassHierarchy;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.cloud.tools.opensource.dependencies.DependencyGraph;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.FieldOrMethod;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;

/**
 * A tool to find static linkage errors for a class path.
 */
public class StaticLinkageChecker {

  private static final Logger logger = Logger.getLogger(StaticLinkageChecker.class.getName());

  public static StaticLinkageChecker create(
      boolean onlyReachable, List<Path> jarPaths, Iterable<Path> entryPoints)
      throws IOException {
    Preconditions.checkArgument(
        !jarPaths.isEmpty(),
        "The linkage classpath is empty. Specify input to supply one or more jar files");
    ClassDumper dumper = ClassDumper.create(jarPaths);
    return new StaticLinkageChecker(onlyReachable, dumper, entryPoints);
  }
  
  public static StaticLinkageChecker create(boolean onlyReachable,
      LinkedListMultimap<Path, DependencyPath> paths, ImmutableSet<Path> entryPoints)
      throws IOException {
    List<Path> jarPaths = new ArrayList<>(paths.keySet());
    ClassDumper dumper = ClassDumper.create(jarPaths);
    return new StaticLinkageChecker(onlyReachable, dumper, entryPoints, paths);
  }

  /**
   * If true, the report excludes linkage errors on classes that are not reachable
   * from the entry points of the class usage graph.
   */
  private final boolean reportOnlyReachable;

  private final ClassDumper classDumper;

  private final ImmutableSet<Path> entryPoints;
  
  private final ListMultimap<Path, DependencyPath> paths;

  private StaticLinkageChecker(
      boolean reportOnlyReachable, ClassDumper classDumper, Iterable<Path> entryPoints) {
    this(reportOnlyReachable, classDumper, entryPoints, ArrayListMultimap.create());
  }

  private StaticLinkageChecker(
      boolean reportOnlyReachable,
      ClassDumper classDumper,
      Iterable<Path> entryPoints,
      ListMultimap<Path, DependencyPath> paths) {
    this.reportOnlyReachable = reportOnlyReachable;
    this.classDumper = Preconditions.checkNotNull(classDumper);
    this.entryPoints = ImmutableSet.copyOf(entryPoints);
    this.paths = Preconditions.checkNotNull(paths);
  }

  /**
   * Given Maven coordinates or list of the jar files as file names in filesystem, outputs the
   * report of static linkage check.
   *
   * @throws IOException when there is a problem in reading a jar file
   * @throws RepositoryException when there is a problem in resolving the Maven coordinates to jar
   *     files
   * @throws ParseException when the arguments are invalid for the tool
   */
  public static void main(String[] arguments)
      throws IOException, RepositoryException, ParseException {
    
    CommandLine commandLine = StaticLinkageCheckOption.readCommandLine(arguments);
    ImmutableList<Path> inputClasspath =
        StaticLinkageCheckOption.generateInputClasspath(commandLine);
    // TODO(suztomo): take command-line option to choose entry point classes for reachability
    ImmutableSet<Path> entryPoints = ImmutableSet.of(inputClasspath.get(0));

    boolean onlyReachable = commandLine.hasOption("r");
    StaticLinkageChecker staticLinkageChecker = create(onlyReachable, inputClasspath, entryPoints);
    StaticLinkageCheckReport report = staticLinkageChecker.findLinkageErrors();

    System.out.println(report);
  }

  /**
   * Finds jar file paths for Maven artifacts and their dependencies.
   *
   * @param artifacts Maven artifacts to check
   * @return list of absolute paths to jar files
   * @throws RepositoryException when there is a problem in retrieving jar files
   */
  public static ImmutableList<Path> artifactsToClasspath(List<Artifact> artifacts)
      throws RepositoryException {
    
    LinkedListMultimap<Path, DependencyPath> multimap = artifactsToPaths(artifacts);
    return ImmutableList.copyOf(multimap.keySet());
  }
  
  
  // Multimap is a pain, maybe just use LinkedHashMap<Path, List<DependencyPath>>
  /**
   * Finds jar file paths for Maven artifacts and their dependencies.
   *
   * @param artifacts Maven artifacts to check
   * @return map absolute paths of jar files to Maven dependency paths
   * @throws RepositoryException when there is a problem in retrieving jar files
   */
  public static LinkedListMultimap<Path, DependencyPath> artifactsToPaths(List<Artifact> artifacts)
      throws RepositoryException {
    
    LinkedListMultimap<Path, DependencyPath> multimap = LinkedListMultimap.create();
    if (artifacts.isEmpty()) {
      return multimap;
    }
    // dependencyGraph holds multiple versions for one artifact key (groupId:artifactId)
    DependencyGraph dependencyGraph =
        DependencyGraphBuilder.getStaticLinkageCheckDependencies(artifacts);
    List<DependencyPath> dependencyPaths = dependencyGraph.list();

    for (DependencyPath dependencyPath : dependencyPaths) {
      Artifact artifact = dependencyPath.getLeaf();
      Path jarAbsolutePath = artifact.getFile().toPath().toAbsolutePath();
      if (!jarAbsolutePath.toString().endsWith(".jar")) {
        continue;
      }
      multimap.put(jarAbsolutePath, dependencyPath);
    }
    return multimap;
  }

  /**
   * Finds linkage errors in the input classpath and generates a static linkage check report.
   */
  public StaticLinkageCheckReport findLinkageErrors() throws IOException {
    ImmutableList<Path> jarPaths = classDumper.getInputClasspath();

    ImmutableMap.Builder<Path, SymbolReferenceSet> jarToSymbols = ImmutableMap.builder();
    for (Path jarPath : jarPaths) {
      jarToSymbols.put(jarPath, ClassDumper.scanSymbolReferencesInJar(jarPath));
    }

    // Validate linkage error of each reference
    ImmutableList.Builder<JarLinkageReport> jarLinkageReports = ImmutableList.builder();
    for (Map.Entry<Path, SymbolReferenceSet> entry : jarToSymbols.build().entrySet()) {
      Path jarPath = entry.getKey();
      Iterable<DependencyPath> dependencyPaths = this.paths.get(jarPath);
      jarLinkageReports.add(generateLinkageReport(jarPath, entry.getValue(), dependencyPaths));
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
  JarLinkageReport generateLinkageReport(Path jarPath, SymbolReferenceSet symbolReferenceSet,
      Iterable<DependencyPath> dependencyPaths) {
    
    JarLinkageReport.Builder reportBuilder = JarLinkageReport.builder()
        .setJarPath(jarPath)
        .setDependencyPaths(dependencyPaths);

    // Because the Java compiler ensures that there are no static linkage errors between classes
    // defined in the same jar file, this validation excludes reference within the same jar file.
    ImmutableSet<String> classesDefinedInJar = classDumper.classesDefinedInJar(jarPath);

    reportBuilder.setMissingClassErrors(
        errorsFromSymbolReferences(
            symbolReferenceSet.getClassReferences(),
            classesDefinedInJar,
            this::checkLinkageErrorMissingClassAt));

    reportBuilder.setMissingMethodErrors(
        errorsFromSymbolReferences(
            symbolReferenceSet.getMethodReferences(),
            classesDefinedInJar,
            this::checkLinkageErrorMissingMethodAt));

    reportBuilder.setMissingFieldErrors(
        errorsFromSymbolReferences(
            symbolReferenceSet.getFieldReferences(),
            classesDefinedInJar,
            this::checkLinkageErrorMissingFieldAt));

    return reportBuilder.build();
  }

  private static <R extends SymbolReference, C> ImmutableList<C> errorsFromSymbolReferences(
      Set<R> symbolReferences,
      Set<String> classesDefinedInJar,
      Function<R, Optional<C>> checkFunction) {
    ImmutableList<C> linkageErrors =
        symbolReferences
            .stream()
            .filter(reference -> !classesDefinedInJar.contains(reference.getTargetClassName()))
            .map(checkFunction)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(toImmutableList());
    return linkageErrors;
  }

  /**
   * Returns an {@code Optional} describing the linkage error for the method reference if the
   * reference does not have a valid referent in the input class path; otherwise an empty {@code
   * Optional}.
   */
  @VisibleForTesting
  Optional<StaticLinkageError<MethodSymbolReference>> checkLinkageErrorMissingMethodAt(
      MethodSymbolReference reference) {
    String targetClassName = reference.getTargetClassName();
    String methodName = reference.getMethodName();

    // Skip references to Java runtime class. For example, java.lang.String.
    if (classDumper.isSystemClass(targetClassName)) {
      return Optional.empty();
    }

    try {
      JavaClass targetJavaClass = classDumper.loadJavaClass(targetClassName);
      Path classFileLocation = classDumper.findClassLocation(targetClassName);
      if (!isClassAccessibleFrom(targetJavaClass, reference.getSourceClassName())) {
        return Optional.of(StaticLinkageError.errorInaccessibleClass(reference, classFileLocation));
      }

      // Checks the target class, its parent classes, and its interfaces.
      // Interface check is needed to avoid false positive for a method reference to an abstract
      // class that implements an interface. For example, Guava's ImmutableList is an abstract class
      // that implements the List interface, but the class does not have a get() method. A method
      // reference to ImmutableList.get() should not be reported as a linkage error.
      Iterable<JavaClass> typesToCheck =
          Iterables.concat(
              getClassHierarchy(targetJavaClass),
              Arrays.asList(targetJavaClass.getAllInterfaces()));
      for (JavaClass javaClass : typesToCheck) {
        for (Method method : javaClass.getMethods()) {
          if (method.getName().equals(methodName)
              && method.getSignature().equals(reference.getDescriptor())) {
            if (!isMemberAccessibleFrom(javaClass, method, reference.getSourceClassName())) {
              return Optional.of(
                  StaticLinkageError.errorInaccessibleMember(reference, classFileLocation));
            }
            // The method is found and accessible. Returning no error.
            return Optional.empty();
          }
        }
      }

      // The class is in class path but the symbol is not found
      return Optional.of(StaticLinkageError.errorMissingMember(reference, classFileLocation));
    } catch (ClassNotFoundException ex) {
      return Optional.of(StaticLinkageError.errorMissingTargetClass(reference));
    }
  }

  /**
   * Returns an {@code Optional} describing the linkage error for the field reference if the
   * reference does not have a valid referent in the input class path; otherwise an empty {@code
   * Optional}.
   */
  @VisibleForTesting
  Optional<StaticLinkageError<FieldSymbolReference>> checkLinkageErrorMissingFieldAt(
      FieldSymbolReference reference) {
    String targetClassName = reference.getTargetClassName();
    String fieldName = reference.getFieldName();
    try {
      JavaClass targetJavaClass = classDumper.loadJavaClass(targetClassName);
      Path classFileLocation = classDumper.findClassLocation(targetClassName);
      if (!isClassAccessibleFrom(targetJavaClass, reference.getSourceClassName())) {
        return Optional.of(StaticLinkageError.errorInaccessibleClass(reference, classFileLocation));
      }

      for (JavaClass javaClass : getClassHierarchy(targetJavaClass)) {
        for (Field field : javaClass.getFields()) {
          if (field.getName().equals(fieldName)) {
            if (!isMemberAccessibleFrom(javaClass, field, reference.getSourceClassName())) {
              return Optional.of(
                  StaticLinkageError.errorInaccessibleMember(reference, classFileLocation));
            }
            // The field is found and accessible. Returning no error.
            return Optional.empty();
          }
        }
      }
      // The field was not found in the class from the classpath
      return Optional.of(StaticLinkageError.errorMissingMember(reference, classFileLocation));
    } catch (ClassNotFoundException ex) {
      return Optional.of(StaticLinkageError.errorMissingTargetClass(reference));
    }
  }

  /**
   * Returns true if the field or method of a class is accessible from {@code sourceClassName}.
   *
   * @see <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-6.html#jls-6.6.1">JLS 6.6.1
   *     Determining Accessibility</a>
   */
  private boolean isMemberAccessibleFrom(
      JavaClass targetClass, FieldOrMethod member, String sourceClassName) {
    // The order of these if statements for public, protected, and private are in the same order
    // they
    // appear in JLS 6.6.1
    if (member.isPublic()) {
      return true;
    }
    if (member.isProtected()) {
      if (ClassDumper.classesInSamePackage(targetClass.getClassName(), sourceClassName)) {
        return true;
      }
      try {
        JavaClass sourceClass = classDumper.loadJavaClass(sourceClassName);
        if (ClassDumper.isClassSubClassOf(sourceClass, targetClass)) {
          return true;
        }
      } catch (ClassNotFoundException ex) {
        logger.warning(
            "The source class "
                + sourceClassName
                + " of a reference was not found in the class path when checking accessibility");
        return false;
      }
    }
    if (member.isPrivate()) {
      // Access from within same top-level class is allowed to read private class. However, such
      // cases are already filtered at errorsFromSymbolReferences.
      return false;
    }
    // Default: package private
    if (ClassDumper.classesInSamePackage(targetClass.getClassName(), sourceClassName)) {
      return true;
    }
    return false;
  }

  /**
   * Returns an {@code Optional} describing the linkage error for the class reference if the
   * reference does not have a valid referent in the input class path; otherwise an empty {@code
   * Optional}.
   */
  @VisibleForTesting
  Optional<StaticLinkageError<ClassSymbolReference>> checkLinkageErrorMissingClassAt(
      ClassSymbolReference reference) {
    String targetClassName = reference.getTargetClassName();
    try {
      JavaClass targetClass = classDumper.loadJavaClass(targetClassName);
      if (!isClassAccessibleFrom(targetClass, reference.getSourceClassName())) {
        return Optional.of(
            StaticLinkageError.errorInaccessibleClass(
                reference, classDumper.findClassLocation(targetClassName)));
      }
      return Optional.empty();
    } catch (ClassNotFoundException ex) {
      return Optional.of(StaticLinkageError.errorMissingTargetClass(reference));
    }
  }

  /**
   * Returns true if the {@code javaClass} is accessible {@code from sourceClassName} in terms of
   * the access modifiers in the {@code javaClass}.
   *
   * @see <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-8.html#jls-ClassModifier">
   *     JLS 8.1.1. Class Modifiers</a>
   */
  private boolean isClassAccessibleFrom(JavaClass javaClass, String sourceClassName)
      throws ClassNotFoundException {
    if (javaClass.isPrivate()) {
      // Nested class can be declared as private. Class reference within same file is allowed to
      // access private class. However, such cases are already filtered at
      // errorsFromSymbolReferences.
      return false;
    }

    String targetClassName = javaClass.getClassName();
    if (javaClass.isPublic()
        || ClassDumper.classesInSamePackage(targetClassName, sourceClassName)) {
      String enclosingClassName = ClassDumper.enclosingClassName(targetClassName);
      if (enclosingClassName != null) {
        // Nested class can be declared as private or protected, in addition to
        // public and package private. Protected is treated same as package private.
        // https://docs.oracle.com/javase/specs/jls/se8/html/jls-8.html#jls-ClassModifier
        JavaClass enclosingJavaClass = classDumper.loadJavaClass(enclosingClassName);
        return isClassAccessibleFrom(enclosingJavaClass, sourceClassName);
      } else {
        // Top-level class can be declared as public or package private.
        return true;
      }
    } else {
      // The class is not public and not in the same package as the source class.
      return false;
    }
  }
}
