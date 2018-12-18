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
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.graph.Traverser;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.apache.bcel.classfile.Field;
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
  // TODO(suztomo): enhance scope to include fields and classes. Issue #207

  private static final Logger logger = Logger.getLogger(StaticLinkageChecker.class.getName());

  public static StaticLinkageChecker create(
      boolean onlyReachable, List<Path> jarFilePaths, Iterable<Path> entryPoints)
      throws IOException {
    Preconditions.checkArgument(
        !jarFilePaths.isEmpty(),
        "The linkage classpath is empty. Specify input to supply one or more jar files");
    ClassDumper dumper = ClassDumper.create(jarFilePaths);
    return new StaticLinkageChecker(onlyReachable, dumper, entryPoints);
  }
  
  public static StaticLinkageChecker create(boolean onlyReachable,
      LinkedListMultimap<Path, DependencyPath> paths, ImmutableSet<Path> entryPoints)
      throws IOException {
    List<Path> jarFilePaths = new ArrayList<>(paths.keySet());
    ClassDumper dumper = ClassDumper.create(jarFilePaths);
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

  StaticLinkageChecker(
      boolean reportOnlyReachable, ClassDumper classDumper, Iterable<Path> entryPoints) {
    this(reportOnlyReachable, classDumper, entryPoints, ArrayListMultimap.create());
  }
  
  StaticLinkageChecker(boolean reportOnlyReachable, ClassDumper classDumper,
      Iterable<Path> entryPoints, ListMultimap<Path, DependencyPath> paths) {
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
    ImmutableList<Path> inputClasspath = StaticLinkageCheckOption.generateInputClasspath(commandLine);
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
    ImmutableList<Path> jarFilePaths = classDumper.getInputClasspath();

    ImmutableMap.Builder<Path, SymbolReferenceSet> jarToSymbols = ImmutableMap.builder();
    for (Path jarPath : jarFilePaths) {
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

    reportBuilder.setMissingMethodErrors(
        symbolReferenceSet
            .getMethodReferences()
            .stream()
            .filter(reference -> !classesDefinedInJar.contains(reference.getTargetClassName()))
            .map(this::checkLinkageErrorAt)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(toImmutableList()));

    reportBuilder.setMissingFieldErrors(
        symbolReferenceSet.getFieldReferences()
        .stream()
        .filter(reference-> !classesDefinedInJar.contains(reference.getTargetClassName()))
        .map(this::checkLinkageErrorAt)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(toImmutableList()));

    // TODO(#243): implement validation for class
    reportBuilder.setMissingClassErrors(ImmutableList.of());

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
   * Returns an {@code Optional} describing the linkage error for the field reference if the
   * reference does not have a valid referent in the input class path; otherwise an empty {@code
   * Optional}.
   */
  private Optional<LinkageErrorMissingField> checkLinkageErrorAt(FieldSymbolReference reference) {
    String targetClassName = reference.getTargetClassName();
    String fieldName = reference.getFieldName();
    for (JavaClass javaClass : getClassAndSuperClasses(targetClassName)) {
      for (Field field : javaClass.getFields()) {
        if (field.getName().equals(fieldName)) {
          return Optional.empty();
        }
      }
    }
    try {
      // The field was not found in the class from the classpath. The location of the target class
      // will be the first thing to check for investigating the reason.
      URL classFileUrl = classDumper.findClassLocation(targetClassName);
      return Optional.of(LinkageErrorMissingField.errorAt(reference, classFileUrl));
    } catch (ClassNotFoundException ex) {
      return Optional.of(LinkageErrorMissingField.errorAt(reference, null));
    }
  }

  /**
   * Returns true if the method reference has a valid referent in the classpath via Java class
   * loader.
   */
  private boolean validateMethodReferenceByClassLoader(MethodSymbolReference methodReference) {
    String className = methodReference.getTargetClassName();
    String methodName = methodReference.getMethodName();
    try {
      Class<?>[] parameterTypes = classDumper.methodDescriptorToClass(methodReference.getDescriptor());
      Class<?> clazz = className.startsWith("[") ? Array.class : classDumper.loadClass(className);
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
    for (JavaClass javaClass : getClassAndSuperClasses(className)) {
      for (Method method : javaClass.getMethods()) {
        if (method.getName().equals(methodName)
            && method.getSignature().equals(methodReference.getDescriptor())) {
          return true;
        }
      }
    }
    return false;
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

  /**
   * Returns the target class and its superclasses in order (with {@link Object} last). If any can't
   * be found, the list stops with the previous one.
   */
  private Iterable<JavaClass> getClassAndSuperClasses(String targetClassName) {
    try {
      return SUPERCLASSES.breadthFirst(classDumper.loadJavaClass(targetClassName));
    } catch (ClassNotFoundException ex) {
      return ImmutableList.of();
    }
  }

  private static final Traverser<JavaClass> SUPERCLASSES =
      Traverser.forTree(
          javaClass -> {
            try {
              return ImmutableSet.of(javaClass.getSuperClass());
            } catch (ClassNotFoundException e) {
              return ImmutableSet.of();
            }
          });
}
