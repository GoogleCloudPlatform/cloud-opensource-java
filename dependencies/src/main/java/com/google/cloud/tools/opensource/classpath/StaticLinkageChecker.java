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

import com.google.cloud.tools.opensource.dependencies.DependencyGraph;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.reflect.ClassPath.ClassInfo;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.apache.bcel.Const;
import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.InnerClass;
import org.apache.bcel.classfile.InnerClasses;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.Type;
import org.apache.bcel.util.ClassPath;
import org.apache.bcel.util.SyntheticRepository;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * This class reads jar files and runs static linkage check on them.
 * Static linkage check finds discrepancies between methods referenced by classes in the jar files,
 * and methods defined in them. This happens when the signature of a non-private method or class in
 * a dependency has changed in an incompatible way between the version supplied at compile time
 * and the version invoked at runtime.
 * TODO: enhance scope to include fields and classes
 */
class StaticLinkageChecker {
  static boolean debug = false;

  /**
   * Map to record class references in reverse order to show the referencing classes for a classes
   * When 'Class A' uses 'Class B' and 'Class C', then we call
   *
   * <pre>
   *   classReferenceGraph.put('Class B', 'Class A');
   *   classReferenceGraph.put('Class C', 'Class A');
   * </pre>
   */
  static SetMultimap<String, String> classReferenceGraph = HashMultimap.create();
  static String classToTraceUsageGraph = null;
  static String traceClassName = null;
  static String traceMethodName = null;

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
    List<Path> jarFilePaths = parseArguments(arguments);

    System.out.println("Starting to read " + jarFilePaths.size() + " files: \n" + jarFilePaths);
    StringBuilder stringBuilder = new StringBuilder();
    List<FullyQualifiedMethodSignature> unresolvedMethodReferences =
        findUnresolvedMethodReferences(jarFilePaths);
    SortedSet<FullyQualifiedMethodSignature> sortedUnresolvedMethodReferences =
        new TreeSet<>(Comparator.comparing(FullyQualifiedMethodSignature::toString));
    sortedUnresolvedMethodReferences.addAll(unresolvedMethodReferences);
    if (sortedUnresolvedMethodReferences.isEmpty()) {
      stringBuilder.append("There were no unresolved method references from the first jar file :");
      stringBuilder.append(jarFilePaths.get(0).getFileName());
    } else {
      int count = sortedUnresolvedMethodReferences.size();
      stringBuilder.append(
          "There were " + count + " unresolved method references from the jar file(s):\n");
      for (FullyQualifiedMethodSignature methodReference : sortedUnresolvedMethodReferences) {
        stringBuilder.append("Class: '");
        stringBuilder.append(methodReference.getClassName());
        stringBuilder.append("', method: '");
        stringBuilder.append(methodReference.getMethodSignature().getMethodName());
        stringBuilder.append("' with descriptor ");
        stringBuilder.append(methodReference.getMethodSignature().getDescriptor());
        stringBuilder.append("\n");
      }
    }
    System.out.println(stringBuilder.toString());
  }

  private static void printUsageGraphTrace(
      SetMultimap<String, String> classReferenceGraph,
      String className,
      Set<String> parentNodes,
      int depth,
      ClassLoader classLoader) {
    // TODO: Use breadth-first search to find shortest path from start nodes to the the traced node
    Set<String> callersOfClass = classReferenceGraph.get(className);
    String indent = Strings.repeat("    ", depth);
    if (parentNodes.contains(className)) {
      System.out.println(indent + "(Cyclic dependency of " + className + ")");
      return;
    }
    if (callersOfClass.isEmpty()) {
      System.out.println(indent + className + " is at root; no class referenced it)");
    } else {
      int count = 0;
      for (String callerClassName : callersOfClass) {
        count++;
        try {
          Class clazz = classLoader.loadClass(callerClassName);
          URL codeLocation = clazz.getProtectionDomain().getCodeSource().getLocation();
          Path sourceFileName = Paths.get(codeLocation.toURI()).getFileName();
          System.out.println(
              indent + className + " <- " + callerClassName + " in " + sourceFileName);
          if (count > 10) {
            System.out.println(
                indent + "...(too many usage for " + className + ". Truncated output)...");
            break;
          }
        } catch (ClassNotFoundException | URISyntaxException ex) {
          // source file not found
        }
        Set<String> nextParentNodes = new HashSet<>(parentNodes);
        nextParentNodes.add(className);
        printUsageGraphTrace(
            classReferenceGraph, callerClassName, nextParentNodes, depth + 1, classLoader);
      }
    }
  }

  /**
   * Parses arguments to get list of jar file paths.
   *
   * @param arguments command-line arguments
   * @return list of the absolute paths to jar files
   * @throws RepositoryException when there is a problem in resolving the Maven coordinate to jar
   */
  static List<Path> parseArguments(String[] arguments) throws RepositoryException {
    Options options = new Options();
    options.addOption("c", "coordinate", true, "Maven coordinates (separated by ',')");
    options.addOption("j", "jars", true, "Jar files (separated by ',')");
    options.addOption("t", "--trace", true, "class to trace class usage graph");
    options.addOption(
        "m",
        "--trace-method",
        true,
        "class and method to identify the callers. Format: '<fully-qualified class>:<method>'");

    HelpFormatter formatter = new HelpFormatter();
    CommandLineParser parser = new DefaultParser();
    List<Path> jarFilePaths = new ArrayList<>();
    try {
      CommandLine cmd = parser.parse(options, arguments);
      if (cmd.hasOption("c")) {
        String mavenCoordinates = cmd.getOptionValue("c");
        for (String coordinate : mavenCoordinates.split(",")) {
          jarFilePaths.addAll(coordinateToJarPaths(coordinate));
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
      if (cmd.hasOption("t")) {
        classToTraceUsageGraph = cmd.getOptionValue("t");
      }
      if (cmd.hasOption("m")) {
        String traceMethodValue = cmd.getOptionValue("m");
        String[] classAndMethod = traceMethodValue.split(":");
        if (classAndMethod.length != 2) {
          throw new IllegalArgumentException(
              "Invalid method name specified: "
                  + traceMethodValue
                  + "\n"
                  + "format: '<fully-qualified class>:<method>'");
        }
        traceClassName = classAndMethod[0];
        traceMethodName = classAndMethod[1];
      }
    } catch (ParseException ex) {
      System.err.println("Failed to parse command line arguments: " + ex.getMessage());
    }
    if (jarFilePaths.isEmpty()) {
      System.err.println("No jar files to scan");
      formatter.printHelp("StaticLinkageChecker", options);
      throw new IllegalArgumentException("Could not list jar files for given argument.");
    }
    return jarFilePaths;
  }

  /**
   * Finds jar file paths for the dependencies of the Maven coordinate.
   *
   * @param coordinate Maven coordinate of an artifact to check its dependencies
   * @return list of absolute paths to jar files
   * @throws RepositoryException when there is a problem in retrieving jar files
   */
  static List<Path> coordinateToJarPaths(String coordinate) throws RepositoryException {
    DefaultArtifact artifact = new DefaultArtifact(coordinate);
    DependencyGraph transitiveDependencies =
        DependencyGraphBuilder.getTransitiveDependencies(artifact);
    List<DependencyPath> dependencyPaths = transitiveDependencies.list();
    List<Path> jarPaths = dependencyPaths.stream().map(dependencyPath -> {
      File artifactFile = dependencyPath.getLeaf().getFile();
      Path artifactFilePath = artifactFile.toPath();
      if (artifactFilePath.toString().endsWith(".jar")) {
        return artifactFilePath.toAbsolutePath();
      } else {
        return null;
      }
    }).filter(Objects::nonNull).collect(Collectors.toList());
    return jarPaths;
  }

  /**
   * Given the jar file paths, runs the static linkage check and returns unresolved methods.
   *
   * @param jarFilePaths absolute paths to jar files to scan for static linkage check
   * @return list of methods that are not found in the jar files
   * @throws IOException when there is a problem in reading a jar file
   * @throws ClassNotFoundException when there is a problem in reading a class from a jar file
   */
  static List<FullyQualifiedMethodSignature> findUnresolvedMethodReferences(
      List<Path> jarFilePaths)
      throws IOException, ClassNotFoundException {
    if (jarFilePaths.size() < 1) {
      throw new IllegalArgumentException("The size of jar file paths is zero");
    }
    Path absolutePathToFirstJar = jarFilePaths.get(0);
    if (!Files.isReadable(absolutePathToFirstJar)) {
      throw new IOException("The file is not readable: " + absolutePathToFirstJar);
    }
    Set<String> classesCheckedMethodReference = new HashSet<>();

    // To avoid false positives from unused classes in 3rd-party library (e.g., grpc-netty-shaded),
    // it traverses class usage graph starting with the method references from the input class path.
    List<FullyQualifiedMethodSignature> methodReferencesFromInputClassPath =
        listExternalMethodReferences(absolutePathToFirstJar, classesCheckedMethodReference);

    List<FullyQualifiedMethodSignature> unresolvedMethodReferences =
        findUnresolvedReferences(
            jarFilePaths, methodReferencesFromInputClassPath, classesCheckedMethodReference);
    return unresolvedMethodReferences;
  }

  /**
   * Checks the availability of the methods through the jar files and lists the unavailable methods.
   * Starting with the initialMethodReferences, this method recursively search for the method
   * references in class usage graph.
   *
   * @param jarFilePaths absolute paths to the jar files to search for the methods
   * @param initialMethodReferences methods to search for within the jar files
   * @param classesAlreadyInQueue class names already checked for their method references
   * @return list of methods that are not found in the jar files
   */
  @VisibleForTesting
  static List<FullyQualifiedMethodSignature> findUnresolvedReferences(
      List<Path> jarFilePaths, List<FullyQualifiedMethodSignature> initialMethodReferences,
      Set<String> classesAlreadyInQueue) {
    List<FullyQualifiedMethodSignature> unresolvedMethods = new ArrayList<>();

    // Creates classpath in the same order as jarFilePaths for BCEL API
    String pathAsString =
        jarFilePaths.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
    ClassPath classPath = new ClassPath(pathAsString);
    SyntheticRepository repository = SyntheticRepository.getInstance(classPath);

    // This map helps to distinguish whether a method reference from a class is external or not
    Map<Path, Set<String>> jarFileToClasses = jarFilesToDefinedClasses(jarFilePaths);

    Set<String> classesNotFound = new HashSet<>();
    Set<FullyQualifiedMethodSignature> availableMethodsInJars = new HashSet<>();

    URL[] jarFileUrls = jarFilePaths.stream().map(jarPath -> {
      try {
        return jarPath.toUri().toURL();
      } catch (MalformedURLException ex) {
        System.err.println("Jar file " + jarPath + " was not converted to URL: " + ex.getMessage());
        return null;
      }
    }).filter(Objects::nonNull).toArray(URL[]::new);
    URLClassLoader classLoaderFromJars =
        new URLClassLoader(jarFileUrls, ClassLoader.getSystemClassLoader());

    Queue<FullyQualifiedMethodSignature> queue = new ArrayDeque<>(initialMethodReferences);
    while (!queue.isEmpty()) {
      FullyQualifiedMethodSignature methodReference = queue.poll();
      String className = methodReference.getClassName();
      if (isBuiltinClassName(className)) {
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

          // If the class is available, then check the method references from the class recursively
          if (classesAlreadyInQueue.contains(className)) {
            continue;
          }
          // This list does not include internal method references: classes defined within the
          // same jar file as the one with `className`.
          List<FullyQualifiedMethodSignature> nextExternalMethodReferences =
              ClassDumper.listExternalMethodReferences(
                  className, jarFileToClasses, classLoaderFromJars, repository);

          queue.addAll(nextExternalMethodReferences);
          classesAlreadyInQueue.add(className);
        } else {
          unresolvedMethods.add(methodReference);
        }
      } catch (ClassNotFoundException | NoClassDefFoundError ex) {
        unresolvedMethods.add(methodReference);
        classesNotFound.add(className);
      }
    }
    System.out.println(
        "The number of resolved method references during linkage check: "
            + availableMethodsInJars.size());

    if (classToTraceUsageGraph != null) {
      printUsageGraphTrace(
          classReferenceGraph, classToTraceUsageGraph, new HashSet<>(), 0, classLoaderFromJars);
    }

    return unresolvedMethods;
  }

  /**
   * @param jarFilePaths absolute paths to jar files
   * @return map of jar file paths to classes defined in them
   */
  private static Map<Path, Set<String>> jarFilesToDefinedClasses(List<Path> jarFilePaths) {
    Map<Path, Set<String>> pathToClasses = new HashMap<>();
    for (Path jarFilePath : jarFilePaths) {
      Set<String> internalClassNames = new HashSet<>();

      String pathToJar = jarFilePath.toString();
      SyntheticRepository repository = SyntheticRepository.getInstance(new ClassPath(pathToJar));
      try {
        URL jarFileUrl = jarFilePath.toUri().toURL();
        Set<ClassInfo> classes = listTopLevelClassesFromJar(jarFileUrl);
        for (ClassInfo classInfo : classes) {
          String className = classInfo.getName();
          JavaClass javaClass = repository.loadClass(className);
          String topLevelClassName = javaClass.getClassName();
          internalClassNames.add(topLevelClassName);
          // This does not take double-nested classes. As long as such classes are accessed
          // only from the outer class, static linkage checker does not report false positives
          // TODO: enhance this so that it can work with double-nested classes
          internalClassNames.addAll(listInnerClassNames(javaClass));
        }
        pathToClasses.put(jarFilePath, internalClassNames);
      } catch (IOException | ClassNotFoundException ex) {
        throw new RuntimeException("There was problem in loading classes in jar file", ex);
      }
    }
    return pathToClasses;
  }

  private static boolean isBuiltinClassName(String className) {
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
    Class[] parameterTypes = methodDescriptorToClass(methodSignature.getDescriptor(),
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
    } catch (NoSuchMethodException | ClassNotFoundException ex) {
      // Attempt 2: Find the class and method in BCEL API
      // BCEL helps to search availability of (package) private class, constructors and methods
      // that are inaccessible to Java's reflection API or the class loader.
      JavaClass javaClass = repository.loadClass(className);
      boolean methodFoundInBcel = false;
      while (javaClass != null) {
        // Inherited methods need checking with the parent class name
        FullyQualifiedMethodSignature methodReferenceForClass = new FullyQualifiedMethodSignature(
            javaClass.getClassName(),
            methodName,
            methodSignature.getDescriptor()
        );
        List<FullyQualifiedMethodSignature> availableMethodsOnClass =
            listMethodsOnClass(javaClass);
        if (availableMethodsOnClass.contains(methodReferenceForClass)) {
          methodFoundInBcel = true;
          break;
        }
        // null if java.lang.Object.
        javaClass = javaClass.getSuperClass();
      }
      return methodFoundInBcel;
    }
    return true;
  }

  private static Class bcelTypeToJavaClass(Type type, ClassLoader classLoader) {
    switch (type.getType()) {
      case Const.T_BOOLEAN:
        return boolean.class;
      case Const.T_INT:
        return int.class;
      case Const.T_SHORT:
        return short.class;
      case Const.T_BYTE:
        return byte.class;
      case Const.T_LONG:
        return long.class;
      case Const.T_DOUBLE:
        return double.class;
      case Const.T_FLOAT:
        return float.class;
      case Const.T_CHAR:
        return char.class;
      case Const.T_ARRAY:
        return Object[].class;
      default:
        String typeName = type.toString();
        try {
          return classLoader.loadClass(typeName);
        } catch (ClassNotFoundException ex) {
          return null;
        }
    }
  }

  @VisibleForTesting
  static Class[] methodDescriptorToClass(String methodDescriptor, ClassLoader classLoader) {
    Type[] argumentTypes = Type.getArgumentTypes(methodDescriptor);
    Class[] parameterTypes =
        Arrays.stream(argumentTypes)
            .map(type -> bcelTypeToJavaClass(type, classLoader))
            .toArray(Class[]::new);
    return parameterTypes;
  }

  private static List<FullyQualifiedMethodSignature> listMethodsOnClass(JavaClass javaClass) {
    List<MethodSignature> methods = ClassDumper.listDeclaredMethods(javaClass);
    List<FullyQualifiedMethodSignature> fullyQualifiedMethodSignatures = methods.stream()
        .map(method -> new FullyQualifiedMethodSignature(
            javaClass.getClassName(), method.getMethodName(), method.getDescriptor()))
        .collect(Collectors.toList());
    return fullyQualifiedMethodSignatures;
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

    URL jarFileUrl = jarFilePath.toUri().toURL();
    Set<ClassInfo> classes = listTopLevelClassesFromJar(jarFileUrl);
    for (ClassInfo classInfo : classes) {
      String className = classInfo.getName();
      JavaClass javaClass = repository.loadClass(className);
      String topLevelClassName = javaClass.getClassName();
      classesChecked.add(topLevelClassName);
      internalClassNames.add(topLevelClassName);
      internalClassNames.addAll(listInnerClassNames(javaClass));
      List<FullyQualifiedMethodSignature> refs = ClassDumper.listMethodReferences(javaClass);
      methodReferences.addAll(refs);
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
      // This innerClasses don't include double-nested inner classes
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
