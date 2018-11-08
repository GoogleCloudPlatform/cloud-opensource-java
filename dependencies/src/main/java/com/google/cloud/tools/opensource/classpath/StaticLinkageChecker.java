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
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.SetMultimap;
import com.google.common.reflect.ClassPath.ClassInfo;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.bcel.Const;
import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.InnerClass;
import org.apache.bcel.classfile.InnerClasses;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.util.ClassPath;
import org.apache.bcel.util.SyntheticRepository;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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
   * Flag on whether the report excludes the linkage errors on classes that are not reachable from
   * the entry point of the class usage graph.
   */
  private final boolean reportOnlyReachable;

  private final ImmutableList<Path> jarFilePaths;

  StaticLinkageChecker(boolean reportOnlyReachable, List<Path> jarFilePaths) {
    // TODO(suztomo): Create immutable instance variable for class repository. Issue #208
    this.reportOnlyReachable = reportOnlyReachable;
    this.jarFilePaths = ImmutableList.copyOf(jarFilePaths);
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
    StaticLinkageChecker staticLinkageChecker = getInstanceFromArguments(arguments);

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
   * Parses arguments to instantiate the class with configuration specified in arguments.
   *
   * @param arguments command-line arguments
   * @return static linkage checker instance with its variables populated from the arguments
   * @throws RepositoryException when there is a problem in resolving the Maven coordinate to jar
   */
  static StaticLinkageChecker getInstanceFromArguments(String[] arguments) throws RepositoryException {
    // TODO(suztomo): create a class to represent command line option. Issue #209
    Options options = new Options();
    options.addOption(
        "c", "coordinate", true, "Maven coordinates (separated by ',') to generate a classpath");
    options.addOption("j", "jars", true, "Jar files (separated by ',') to generate a classpath");
    options.addOption(
        "r",
        "report-only-reachable",
        false,
        "To report only linkage errors reachable from entry point");

    HelpFormatter formatter = new HelpFormatter();
    CommandLineParser parser = new DefaultParser();
    List<Path> jarFilePaths = new ArrayList<>();
    try {
      CommandLine cmd = parser.parse(options, arguments);
      if (cmd.hasOption("c")) {
        String mavenCoordinates = cmd.getOptionValue("c");
        for (String coordinate : mavenCoordinates.split(",")) {
          jarFilePaths.addAll(coordinateToClasspath(coordinate));
        }
      }
      if (cmd.hasOption("j")) {
        String jarFiles = cmd.getOptionValue("j");
        List<Path> jarFilesInArguments =
            Arrays.stream(jarFiles.split(","))
                .map(name -> (Paths.get(name)).toAbsolutePath())
                .collect(Collectors.toList());
        jarFilePaths.addAll(jarFilesInArguments);
      }
      boolean reportOnlyReachable = cmd.hasOption("r");

      if (jarFilePaths.isEmpty()) {
        throw new IllegalArgumentException("Could not get list of jar files for given argument.");
      }
      return new StaticLinkageChecker(reportOnlyReachable, jarFilePaths);
    } catch (ParseException ex) {
      throw new IllegalArgumentException("Failed to parse command line arguments", ex);
    }
  }

  /**
   * Finds jar file paths for the dependencies of the Maven coordinate.
   *
   * @param coordinate Maven coordinate of an artifact to check its dependencies
   * @return list of absolute paths to jar files
   * @throws RepositoryException when there is a problem in retrieving jar files
   */
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
   * Given the jar file paths, runs the static linkage check and returns unresolved methods.
   *
   * @return list of methods that are not found in the jar files
   * @throws IOException when there is a problem in reading a jar file
   * @throws ClassNotFoundException when there is a problem in reading a class from a jar file
   */
  List<FullyQualifiedMethodSignature> findUnresolvedMethodReferences()
      throws IOException, ClassNotFoundException {
    // TODO(suztomo): Separate logic between data retrieval and usage graph traversal. Issue #203
    Preconditions.checkArgument(!jarFilePaths.isEmpty(), "no jar files specified");

    logger.info("Starting to read " + jarFilePaths.size() + " files: \n" + jarFilePaths);

    Set<String> visitedClasses = new HashSet<>();
    List<FullyQualifiedMethodSignature> methodReferencesFromInputClassPath = new ArrayList<>();

    // When reportOnlyReachable is true, to avoid false positives from unused classes in 3rd-party
    // libraries (e.g., grpc-netty-shaded), we traverse the class usage graph starting with the
    // method references from the input class path and report only errors reachable from there.
    // If the flag is false, it checks all references in the classpath.
    List<Path> jarPathsInInputClasspath =
        reportOnlyReachable ? Collections.singletonList(jarFilePaths.get(0)) : jarFilePaths;
    for (Path absolutePathToJar : jarPathsInInputClasspath) {
      if (!Files.isReadable(absolutePathToJar)) {
        throw new IOException("The file is not readable: " + absolutePathToJar);
      }
      methodReferencesFromInputClassPath.addAll(
          listExternalMethodReferences(absolutePathToJar, visitedClasses));
    }

    List<FullyQualifiedMethodSignature> unresolvedMethodReferences =
        findUnresolvedReferences(
            jarFilePaths, methodReferencesFromInputClassPath, visitedClasses);
    return unresolvedMethodReferences;
  }

  /**
   * Checks the availability of the methods through the jar files and lists the unavailable methods.
   * Starting with the initialMethodReferences, this method recursively searches (breadth-first
   * search) for the references in the class usage graph.
   *
   * @param jarFilePaths absolute paths to the jar files to search for the methods
   * @param initialMethodReferences methods to search for within the jar files
   * @param classesVisited class names already checked for their method references
   * @return list of methods that are not found in the jar files
   */
  @VisibleForTesting
  static List<FullyQualifiedMethodSignature> findUnresolvedReferences(
      List<Path> jarFilePaths,
      List<FullyQualifiedMethodSignature> initialMethodReferences,
      Set<String> classesVisited) {
    List<FullyQualifiedMethodSignature> unresolvedMethods = new ArrayList<>();

    // Creates classpath in the same order as jarFilePaths for BCEL API
    String pathAsString =
        jarFilePaths.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
    ClassPath classPath = new ClassPath(pathAsString);
    SyntheticRepository repository = SyntheticRepository.getInstance(classPath);

    // This map helps to distinguish whether a method reference from a class is external or not
    SetMultimap<Path, String> jarFileToClasses = jarFilesToDefinedClasses(jarFilePaths);

    Set<String> classesNotFound = new HashSet<>();
    Set<FullyQualifiedMethodSignature> availableMethodsInJars = new HashSet<>();

    URL[] jarFileUrls = jarFilePaths.stream().map(jarPath -> {
      try {
        return jarPath.toUri().toURL();
      } catch (MalformedURLException ex) {
        logger.warning("Jar file " + jarPath + " was not converted to URL: " + ex.getMessage());
        return null;
      }
    }).filter(Objects::nonNull).toArray(URL[]::new);
    URLClassLoader classLoaderFromJars =
        new URLClassLoader(jarFileUrls, ClassLoader.getSystemClassLoader());

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
        if (methodDefinitionExists(methodReference, classLoaderFromJars, repository)) {
          availableMethodsInJars.add(methodReference);

          // Enqueue references from the class unless it is already visited in class usage graph
          if (classesVisited.add(className)) {
            ImmutableSet<FullyQualifiedMethodSignature> nextExternalMethodReferences =
                ClassDumper.listExternalMethodReferences(
                    className, jarFileToClasses, classLoaderFromJars, repository);
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

  /**
   * @param jarFilePaths absolute paths to jar files
   * @return map of jar file paths to classes defined in them
   */
  private static SetMultimap<Path, String> jarFilesToDefinedClasses(List<Path> jarFilePaths) {
    SetMultimap<Path, String>  pathToClasses = HashMultimap.create();

    for (Path jarFilePath : jarFilePaths) {
      String pathToJar = jarFilePath.toString();
      SyntheticRepository repository = SyntheticRepository.getInstance(new ClassPath(pathToJar));
      try {
        for (JavaClass javaClass: topLevelJavaClassesInJar(jarFilePath, repository)) {
          pathToClasses.put(jarFilePath, javaClass.getClassName());
          // This does not take double-nested classes. As long as such classes are accessed
          // only from the outer class, static linkage checker does not report false positives
          // TODO: enhance this so that it can work with double-nested classes
          pathToClasses.putAll(jarFilePath, listInnerClassNames(javaClass));
        }
      } catch (IOException | ClassNotFoundException ex) {
        throw new RuntimeException("There was problem in loading classes in jar file", ex);
      }
    }
    return pathToClasses;
  }

  private static ImmutableSet<JavaClass> topLevelJavaClassesInJar(Path jarFilePath,
      SyntheticRepository repository) throws IOException, ClassNotFoundException {
    ImmutableSet.Builder<JavaClass> javaClasses = ImmutableSet.builder();
    URL jarFileUrl = jarFilePath.toUri().toURL();
    Set<ClassInfo> classes = listTopLevelClassesFromJar(jarFileUrl);
    for (ClassInfo classInfo : classes) {
      String className = classInfo.getName();
      JavaClass javaClass = repository.loadClass(className);
      javaClasses.add(javaClass);
    }
    return javaClasses.build();
  }

  private static boolean isBuiltInClassName(String className) {
    return className.startsWith("java.")
        || className.startsWith("sun.")
        || className.startsWith("[");
  }

  @VisibleForTesting
  @SuppressWarnings("unchecked")
  static boolean methodDefinitionExists(FullyQualifiedMethodSignature methodReference,
      ClassLoader classLoader, SyntheticRepository repository) throws ClassNotFoundException {
    String className = methodReference.getClassName();
    MethodSignature methodSignature = methodReference.getMethodSignature();
    String methodName = methodSignature.getMethodName();
    Class[] parameterTypes = ClassDumper.methodDescriptorToClass(methodSignature.getDescriptor(),
        classLoader);
    try {
      // Attempt 1: Find the class and method in the class loader
      // Class loader helps to resolve class hierarchy, such as methods defined in parent class
      Class clazz =
          className.startsWith("[") ? Array.class : classLoader.loadClass(className);
      if ("<init>".equals(methodName)) {
        clazz.getConstructor(parameterTypes);
      } else if ("clone".equals(methodName) && clazz == Array.class) {
        // Array's clone method is not returned by getMethod
        // https://docs.oracle.com/javase/8/docs/api/java/lang/Class.html#getMethod-java.lang.String-java.lang.Class...-
        return true;
      } else {
        clazz.getMethod(methodSignature.getMethodName(),
            parameterTypes);
      }
      return true;
    } catch (NoSuchMethodException | ClassNotFoundException ex) {
      // Attempt 2: Find the class and method in BCEL API
      // BCEL helps to search availability of (package) private class, constructors and methods
      // that are inaccessible to Java's reflection API or the class loader.
      JavaClass javaClass = repository.loadClass(className);
      while (javaClass != null) {
        // Inherited methods need checking with the parent class name
        FullyQualifiedMethodSignature methodReferenceForClass = new FullyQualifiedMethodSignature(
            javaClass.getClassName(),
            methodName,
            methodSignature.getDescriptor()
        );
        List<FullyQualifiedMethodSignature> availableMethodsOnClass =
            ClassDumper.listMethodsOnClass(javaClass);
        if (availableMethodsOnClass.contains(methodReferenceForClass)) {
          return true;
        }
        // null if java.lang.Object
        javaClass = javaClass.getSuperClass();
      }
      return false;
    }
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
  static List<FullyQualifiedMethodSignature> listExternalMethodReferences(
      Path jarFilePath, Set<String> classesChecked) throws IOException, ClassNotFoundException {
    List<FullyQualifiedMethodSignature> methodReferences = new ArrayList<>();
    Set<String> internalClassNames = new HashSet<>();

    String pathToJar = jarFilePath.toString();
    SyntheticRepository repository = SyntheticRepository.getInstance(new ClassPath(pathToJar));

    for (JavaClass javaClass: topLevelJavaClassesInJar(jarFilePath, repository)) {
      String className = javaClass.getClassName();
      classesChecked.add(className);
      internalClassNames.add(className);
      internalClassNames.addAll(listInnerClassNames(javaClass));
      List<FullyQualifiedMethodSignature> references = ClassDumper.listMethodReferences(javaClass);
      methodReferences.addAll(references);
    }
    List<FullyQualifiedMethodSignature> externalMethodReferences = methodReferences.stream()
        .filter(reference -> !internalClassNames.contains(reference.getClassName()))
        .collect(Collectors.toList());
    return externalMethodReferences;
  }

  @VisibleForTesting
  static Set<String> listInnerClassNames(JavaClass javaClass) {
    Set<String> innerClassNames = new HashSet<>();
    String topLevelClassName = javaClass.getClassName();
    Attribute[] attributes = javaClass.getAttributes();
    ConstantPool constantPool = javaClass.getConstantPool();
    for (Attribute attribute : attributes) {
      if (attribute.getTag() != Const.ATTR_INNER_CLASSES) {
        continue;
      }
      // This innerClasses variable does not include double-nested inner classes
      InnerClasses innerClasses = (InnerClasses) attribute;
      for (InnerClass innerClass : innerClasses.getInnerClasses()) {
        int classIndex = innerClass.getInnerClassIndex();
        String innerClassName = constantPool.getConstantString(classIndex, Const.CONSTANT_Class);
        int outerClassIndex = innerClass.getOuterClassIndex();
        if (outerClassIndex > 0) {
          String outerClassName = constantPool.getConstantString(outerClassIndex,
              Const.CONSTANT_Class);
          String normalOuterClassName = outerClassName.replace('/', '.');
          if (!normalOuterClassName.equals(topLevelClassName)) {
            continue;
          }
        }

        // Class names stored in constant pool have '/' as separator. We want '.' (as binary name)
        String normalInnerClassName = innerClassName.replace('/', '.');
        innerClassNames.add(normalInnerClassName);
      }
    }
    return innerClassNames;
  }

  private static ImmutableSet<ClassInfo> listTopLevelClassesFromJar(URL jarFileUrl)
      throws IOException {
    URL[] jarFileUrls = new URL[] {jarFileUrl};

    // Setting parent as null because we don't want other classes than this jar file
    URLClassLoader classLoaderFromJar = new URLClassLoader(jarFileUrls, null);

    // Leveraging Google Guava reflection as BCEL doesn't list classes in a jar file
    com.google.common.reflect.ClassPath classPath =
        com.google.common.reflect.ClassPath.from(classLoaderFromJar);

    // Nested (inner) classes reside in one of top-level class files.
    ImmutableSet<ClassInfo> allClassesInJar = classPath.getTopLevelClasses();
    return allClassesInJar;
  }
}
