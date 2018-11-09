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

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.DependencyGraph;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.util.ClassPath;
import org.apache.bcel.util.SyntheticRepository;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * This class reads jar files and runs static linkage check on them.
 * Static linkage check finds discrepancies between methods referenced by classes in the jar files,
 * and methods defined in them. This happens when the signature of a non-private method or class in
 * a dependency has changed in an incompatible way between the version supplied at compile time
 * and the version invoked at runtime.
 */
class StaticLinkageChecker {
  // TODO(suztomo): enhance scope to include fields and classes. Issue #207

  private static final Logger logger = Logger.getLogger(StaticLinkageChecker.class.getName());

  /**
   * Flag on the reachability. This flag controls whether the report excludes the linkage errors
   * on classes that are not reachable from the entry points of the class usage graph.
   */
  private final boolean reportOnlyReachable;

  private final ClassDumper classDumper;

  private final ImmutableSet<Path> entryPoints;

  StaticLinkageChecker(
      boolean reportOnlyReachable, List<Path> jarFilePaths, Set<Path> entryPoints) {
    Preconditions.checkArgument(
        !jarFilePaths.isEmpty(),
        "The linkage classpath is empty. Specify input to supply one or more jar files");
    this.reportOnlyReachable = reportOnlyReachable;
    this.classDumper = ClassDumper.create(jarFilePaths);
    this.entryPoints = ImmutableSet.copyOf(entryPoints);
  }

  /**
   * Given Maven coordinates or list of the jar files as file names in filesystem, outputs the
   * report of static linkage check.
   *
   * @throws IOException when there is a problem in reading a jar file
   * @throws ClassNotFoundException when there is a problem in reading a class from a jar file
   * @throws RepositoryException when there is a problem in resolving the Maven coordinate to jar
   *     files
   */
  public static void main(String[] arguments)
      throws IOException, ClassNotFoundException, RepositoryException {
    StaticLinkageCheckOption commandLineOption = StaticLinkageCheckOption.parseArgument(arguments);
    ImmutableList<Path> inputClasspath =
        generateInputClasspathFromLinkageCheckOption(commandLineOption);
    // TODO(suztomo): to take command-line option to choose entry point classes for reachability
    ImmutableSet<Path> entryPoints = ImmutableSet.of(inputClasspath.get(0));
    StaticLinkageChecker staticLinkageChecker =
        new StaticLinkageChecker(commandLineOption.isReportOnlyReachable(), inputClasspath,
            entryPoints);

    List<FullyQualifiedMethodSignature> unresolvedMethodReferences =
        staticLinkageChecker.findUnresolvedMethodReferences();
    printStaticLinkageErrors(unresolvedMethodReferences);
  }

  private static void printStaticLinkageErrors(
      List<FullyQualifiedMethodSignature> unresolvedMethodReferences) {
    if (unresolvedMethodReferences.isEmpty()) {
      System.out.println("There were no unresolved method references");
      return;
    }
    ImmutableSortedSet<FullyQualifiedMethodSignature> sortedUnresolvedMethodReferences =
        ImmutableSortedSet.copyOf(unresolvedMethodReferences);
    int count = sortedUnresolvedMethodReferences.size();
    Formatter formatter = new Formatter();
    formatter.format(
        "There were %,d unresolved method references from the jar file(s):\n", count);
    for (FullyQualifiedMethodSignature methodReference : sortedUnresolvedMethodReferences) {
      formatter.format(
          "Class: '%s', method: '%s' with descriptor %s\n",
          methodReference.getClassName(),
          methodReference.getMethodSignature().getMethodName(),
          methodReference.getMethodSignature().getDescriptor());
    }
    System.out.println(formatter);
  }

  /**
   * Resolves command line option to list of jar files as input class path for static linkage
   * checker.
   *
   * @param linkageCheckOption option through command-line arguments
   * @return input class path resolved as a list of absolute paths to jar files
   * @throws RepositoryException when there is a problem in resolving the Maven coordinate to jar
   */
  @VisibleForTesting
  static ImmutableList<Path> generateInputClasspathFromLinkageCheckOption(
      StaticLinkageCheckOption linkageCheckOption) throws RepositoryException {
    ImmutableList.Builder<Path> jarFileBuilder = ImmutableList.builder();

    // TODO(suztomo): add logic to convert Maven BOM to list of Maven coordinates as per README.md
    if (!linkageCheckOption.getJarFileList().isEmpty()) {
      jarFileBuilder.addAll(linkageCheckOption.getJarFileList());
    } else if (!linkageCheckOption.getMavenCoordinates().isEmpty()) {
      for (String mavenCoordinates : linkageCheckOption.getMavenCoordinates()) {
        jarFileBuilder.addAll(coordinateToClasspath(mavenCoordinates));
      }
    }
    return jarFileBuilder.build();
  }

  /**
   * Finds jar file paths for the dependencies of the Maven coordinate.
   *
   * @param coordinate Maven coordinate of an artifact to check its dependencies
   * @return list of absolute paths to jar files
   * @throws RepositoryException when there is a problem in retrieving jar files
   */
  @VisibleForTesting
  static List<Path> coordinateToClasspath(String coordinate) throws RepositoryException {
    DefaultArtifact rootArtifact = new DefaultArtifact(coordinate);
    // dependencyGraph holds multiple versions for one artifact key (groupId:artifactId)
    DependencyGraph dependencyGraph =
        DependencyGraphBuilder.getStaticLinkageCheckDependencies(rootArtifact);
    List<DependencyPath> dependencyPaths = dependencyGraph.list();

    // When building a class path, we only need the first version found in breadth-first search
    // for each artifact key. This set is to filter such duplicates.
    Set<String> artifactKeySet = new HashSet<>();

    List<Path> jarPaths = new ArrayList<>();
    for (DependencyPath dependencyPath : dependencyPaths) {
      Artifact artifact = dependencyPath.getLeaf();
      String artifactKey = Artifacts.makeKey(artifact);
      if (artifactKeySet.contains(artifactKey)) {
        // When "groupId:artifactId" is already found in iteration, then not picking up this jar
        continue;
      }
      artifactKeySet.add(artifactKey);

      File artifactFile = artifact.getFile();
      Path artifactFilePath = artifactFile.toPath();
      if (artifactFilePath.toString().endsWith(".jar")) {
        jarPaths.add(artifactFilePath.toAbsolutePath());
      }
    }
    return jarPaths;
  }

  /**
   * Runs the static linkage check and returns unresolved methods for the jar file paths
   *
   * @return list of methods that are not found in the jar files
   * @throws IOException when there is a problem in reading a jar file
   * @throws ClassNotFoundException when there is a problem in reading a class from a jar file
   */
  @VisibleForTesting
  List<FullyQualifiedMethodSignature> findUnresolvedMethodReferences()
      throws IOException, ClassNotFoundException {
    // TODO(suztomo): Separate logic between data retrieval and usage graph traversal. Issue #203
    ImmutableList<Path> jarFilePaths = classDumper.getInputClasspath();
    logger.fine("Starting to read " + jarFilePaths.size() + " files: \n" + jarFilePaths);

    Set<String> visitedClasses = new HashSet<>();
    List<FullyQualifiedMethodSignature> methodReferencesFromInputClassPath = new ArrayList<>();

    // When reportOnlyReachable is true, to avoid false positives from unused classes in 3rd-party
    // libraries (e.g., grpc-netty-shaded), we traverse the class usage graph starting with the
    // method references from the input class path and report only errors reachable from there.
    // If the flag is false, it checks all references in the classpath.
    ImmutableList<Path> jarPathsInInputClasspath =
        reportOnlyReachable ? ImmutableList.copyOf(entryPoints) : jarFilePaths;
    for (Path absolutePathToJar : jarPathsInInputClasspath) {
      if (!Files.isReadable(absolutePathToJar)) {
        throw new IOException("The file is not readable: " + absolutePathToJar);
      }
      methodReferencesFromInputClassPath.addAll(
          listExternalMethodReferences(absolutePathToJar, visitedClasses));
    }

    List<FullyQualifiedMethodSignature> unresolvedMethodReferences =
        findUnresolvedReferences(methodReferencesFromInputClassPath, visitedClasses);
    return unresolvedMethodReferences;
  }

  /**
   * Checks the availability of the methods through the jar files and lists the unavailable methods.
   * Starting with the initialMethodReferences, this method recursively searches (breadth-first
   * search) for the references in the class usage graph.
   *
   * @param initialMethodReferences methods to search for within the jar files
   * @param classesVisited class names already checked for their method references
   * @return list of methods that are not found in the jar files
   */
  @VisibleForTesting
  List<FullyQualifiedMethodSignature> findUnresolvedReferences(
      List<FullyQualifiedMethodSignature> initialMethodReferences,
      Set<String> classesVisited) {
    List<FullyQualifiedMethodSignature> unresolvedMethods = new ArrayList<>();

    Set<String> classesNotFound = new HashSet<>();
    Set<FullyQualifiedMethodSignature> availableMethodsInJars = new HashSet<>();

    // Breadth-first search
    Queue<FullyQualifiedMethodSignature> queue = new ArrayDeque<>(initialMethodReferences);
    while (!queue.isEmpty()) {
      FullyQualifiedMethodSignature methodReference = queue.remove();
      String className = methodReference.getClassName();
      if (isBuiltInClassName(className)) {
        // Ignore references to JDK package
        continue;
      }

      // Case 1: we know that the class doesn't exist in the jar files
      if (classesNotFound.contains(className)) {
        unresolvedMethods.add(methodReference);
        continue;
      }
      // Case 2: we know that the class and method exist in the jar files
      if (availableMethodsInJars.contains(methodReference)) {
        continue;
      }

      // Case 3: we need to check the availability of the method through the class loader
      try {
        if (classDumper.methodDefinitionExists(methodReference)) {
          availableMethodsInJars.add(methodReference);

          // Enqueue references from the class unless it is already visited in class usage graph
          if (classesVisited.add(className)) {
            ImmutableSet<FullyQualifiedMethodSignature> nextExternalMethodReferences =
                classDumper.listExternalMethodReferences(className);
            queue.addAll(nextExternalMethodReferences);
          }
        } else {
          unresolvedMethods.add(methodReference);
        }
      } catch (ClassNotFoundException | NoClassDefFoundError ex) {
        unresolvedMethods.add(methodReference);
        classesNotFound.add(className);
      }
    }

    logger.fine("The number of resolved method references during linkage check: "
        + availableMethodsInJars.size());
    return unresolvedMethods;
  }

  private static boolean isBuiltInClassName(String className) {
    return className.startsWith("java.")
        || className.startsWith("sun.")
        || className.startsWith("[");
  }

  /**
   * Lists all external methods called from the classes in the jar file. The output list does not
   * include the methods defined in the file.
   *
   * @param jarFilePath the absolute path to jar file to analyze
   * @param classesChecked to populate classes that are checked for method references
   * @return list of the method signatures with their fully-qualified classes
   * @throws IOException when there is a problem in reading the jar file
   * @throws ClassNotFoundException when a class visible by Guava's reflect was unexpectedly not
   *     found by BCEL API
   */
  List<FullyQualifiedMethodSignature> listExternalMethodReferences(
      Path jarFilePath, Set<String> classesChecked) throws IOException, ClassNotFoundException {
    List<FullyQualifiedMethodSignature> methodReferences = new ArrayList<>();
    Set<String> internalClassNames = new HashSet<>();

    String pathToJar = jarFilePath.toString();
    SyntheticRepository repository = SyntheticRepository.getInstance(new ClassPath(pathToJar));

    for (JavaClass javaClass: ClassDumper.topLevelJavaClassesInJar(jarFilePath, repository)) {
      String className = javaClass.getClassName();
      classesChecked.add(className);
      internalClassNames.add(className);
      internalClassNames.addAll(ClassDumper.listInnerClassNames(javaClass));
      List<FullyQualifiedMethodSignature> references = ClassDumper.listMethodReferences(javaClass);
      methodReferences.addAll(references);
    }
    List<FullyQualifiedMethodSignature> externalMethodReferences = methodReferences.stream()
        .filter(reference -> !internalClassNames.contains(reference.getClassName()))
        .collect(Collectors.toList());
    return externalMethodReferences;
  }
}
