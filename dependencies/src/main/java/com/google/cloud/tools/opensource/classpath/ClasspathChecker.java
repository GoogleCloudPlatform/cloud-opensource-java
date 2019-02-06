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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.nio.file.Path;
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

/**
 * A tool to find static linkage errors for a class path.
 */
public class ClasspathChecker {

  private static final Logger logger = Logger.getLogger(ClasspathChecker.class.getName());

  private final ClassDumper classDumper;
  private final ImmutableMap<Path, SymbolReferenceSet> jarToSymbols;
  private final ClassReferenceGraph classReferenceGraph;

  public static ClasspathChecker create(List<Path> jarPaths, Iterable<Path> entryPoints)
      throws IOException {
    Preconditions.checkArgument(
        !jarPaths.isEmpty(),
        "The linkage classpath is empty. Specify input to supply one or more jar files");
    ClassDumper dumper = ClassDumper.create(jarPaths);

    ImmutableMap.Builder<Path, SymbolReferenceSet> jarToSymbolBuilder = ImmutableMap.builder();
    for (Path jarPath : jarPaths) {
      jarToSymbolBuilder.put(jarPath, ClassDumper.scanSymbolReferencesInJar(jarPath));
    }
    ImmutableMap<Path, SymbolReferenceSet> jarToSymbols = jarToSymbolBuilder.build();

    ClassReferenceGraph classReferenceGraph =
        ClassReferenceGraph.create(jarToSymbols.values(), ImmutableSet.copyOf(entryPoints));

    return new ClasspathChecker(dumper, jarToSymbols, classReferenceGraph);
  }

  private ClasspathChecker(
      ClassDumper classDumper,
      Map<Path, SymbolReferenceSet> jarToSymbols,
      ClassReferenceGraph classReferenceGraph) {
    this.classDumper = Preconditions.checkNotNull(classDumper);
    this.jarToSymbols = ImmutableMap.copyOf(jarToSymbols);
    this.classReferenceGraph = Preconditions.checkNotNull(classReferenceGraph);
  }

  /**
   * Given Maven coordinates or list of the jar files as file names in filesystem, outputs the
   * report of classpath check.
   *
   * @throws IOException when there is a problem in reading a jar file
   * @throws RepositoryException when there is a problem in resolving the Maven coordinates to jar
   *     files
   * @throws ParseException when the arguments are invalid for the tool
   */
  public static void main(String[] arguments)
      throws IOException, RepositoryException, ParseException {

    CommandLine commandLine = ClasspathCheckOption.readCommandLine(arguments);
    ImmutableList<Path> inputClasspath = ClasspathCheckOption.parseInputClasspath(commandLine);

    ImmutableSet<Path> entryPointJars =
        ClasspathCheckOption.parseEntryPointJars(commandLine, inputClasspath);

    ClasspathChecker classpathChecker = create(inputClasspath, entryPointJars);
    ClasspathCheckReport report = classpathChecker.findLinkageErrors();

    System.out.println(report);
  }

  /** Finds linkage errors in the input classpath and generates a classpath check report. */
  public ClasspathCheckReport findLinkageErrors() {
    // Validate linkage error of each reference
    ImmutableList.Builder<JarLinkageReport> jarLinkageReports = ImmutableList.builder();

    jarToSymbols.forEach(
        (jar, symbolReferenceSet) ->
            jarLinkageReports.add(generateLinkageReport(jar, symbolReferenceSet)));

    return ClasspathCheckReport.create(jarLinkageReports.build());
  }

  /**
   * Generates a linkage report for a jar file, by checking linkage errors in the symbol references
   * against the input class path.
   *
   * @param jarPath absolute path to the jar file
   * @param symbolReferenceSet symbol references from {@code jarPath} to check its linkage errors
   * @return linkage report for the jar file, which includes linkage errors if any
   */
  @VisibleForTesting
  JarLinkageReport generateLinkageReport(
      Path jarPath, SymbolReferenceSet symbolReferenceSet) {

    JarLinkageReport.Builder reportBuilder = JarLinkageReport.builder().setJarPath(jarPath);

    // Because the Java compiler ensures that there are no linkage errors between classes
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

  private static <R extends SymbolReference>
      ImmutableList<StaticLinkageError<R>> errorsFromSymbolReferences(
          Set<R> symbolReferences,
          Set<String> classesDefinedInJar,
          Function<R, Optional<StaticLinkageError<R>>> checkFunction) {
    ImmutableList<StaticLinkageError<R>> linkageErrors =
        symbolReferences.stream()
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
   *
   * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.3">Java
   *     Virtual Machine Specification: 5.4.3.3. Method Resolution</a>
   * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.4">Java
   *     Virtual Machine Specification: 5.4.3.4. Interface Method Resolution</a>
   */
  @VisibleForTesting
  Optional<StaticLinkageError<MethodSymbolReference>> checkLinkageErrorMissingMethodAt(
      MethodSymbolReference reference) {
    String targetClassName = reference.getTargetClassName();
    String sourceClassName = reference.getSourceClassName();
    boolean isSourceClassReachable = classReferenceGraph.isReachable(sourceClassName);
    String methodName = reference.getMethodName();

    // Skip references to Java runtime class. For example, java.lang.String.
    if (classDumper.isSystemClass(targetClassName)) {
      return Optional.empty();
    }

    try {
      JavaClass targetJavaClass = classDumper.loadJavaClass(targetClassName);
      Path classFileLocation = classDumper.findClassLocation(targetClassName);
      if (!isClassAccessibleFrom(targetJavaClass, sourceClassName)) {
        return Optional.of(
            StaticLinkageError.errorInaccessibleClass(
                reference, classFileLocation, isSourceClassReachable));
      }

      if (targetJavaClass.isInterface() != reference.isInterfaceMethod()) {
        return Optional.of(
            StaticLinkageError.errorIncompatibleClassChange(
                reference, classFileLocation, isSourceClassReachable));
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
            if (!isMemberAccessibleFrom(javaClass, method, sourceClassName)) {
              return Optional.of(
                  StaticLinkageError.errorInaccessibleMember(
                      reference, classFileLocation, isSourceClassReachable));
            }
            // The method is found and accessible. Returning no error.
            return Optional.empty();
          }
        }
      }

      // The class is in class path but the symbol is not found
      return Optional.of(
          StaticLinkageError.errorMissingMember(
              reference, classFileLocation, isSourceClassReachable));
    } catch (ClassNotFoundException ex) {
      if (classDumper.catchesNoClassDefFoundError(reference)) {
        return Optional.empty();
      }
      return Optional.of(
          StaticLinkageError.errorMissingTargetClass(reference, isSourceClassReachable));
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
    String sourceClassName = reference.getSourceClassName();
    boolean isSourceClassReachable = classReferenceGraph.isReachable(sourceClassName);
    String fieldName = reference.getFieldName();
    try {
      JavaClass targetJavaClass = classDumper.loadJavaClass(targetClassName);
      Path classFileLocation = classDumper.findClassLocation(targetClassName);
      if (!isClassAccessibleFrom(targetJavaClass, sourceClassName)) {
        return Optional.of(
            StaticLinkageError.errorInaccessibleClass(
                reference, classFileLocation, isSourceClassReachable));
      }

      for (JavaClass javaClass : getClassHierarchy(targetJavaClass)) {
        for (Field field : javaClass.getFields()) {
          if (field.getName().equals(fieldName)) {
            if (!isMemberAccessibleFrom(javaClass, field, sourceClassName)) {
              return Optional.of(
                  StaticLinkageError.errorInaccessibleMember(
                      reference, classFileLocation, isSourceClassReachable));
            }
            // The field is found and accessible. Returning no error.
            return Optional.empty();
          }
        }
      }
      // The field was not found in the class from the classpath
      return Optional.of(
          StaticLinkageError.errorMissingMember(
              reference, classFileLocation, isSourceClassReachable));
    } catch (ClassNotFoundException ex) {
      if (classDumper.catchesNoClassDefFoundError(reference)) {
        return Optional.empty();
      }
      return Optional.of(
          StaticLinkageError.errorMissingTargetClass(reference, isSourceClassReachable));
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
    String sourceClassName = reference.getSourceClassName();
    String targetClassName = reference.getTargetClassName();
    boolean isSourceClassReachable = classReferenceGraph.isReachable(sourceClassName);
    try {
      JavaClass targetClass = classDumper.loadJavaClass(targetClassName);
      Path classFileLocation = classDumper.findClassLocation(targetClassName);

      if (reference.isSubclass()
          && !classDumper.hasValidSuperclass(
              classDumper.loadJavaClass(sourceClassName), targetClass)) {
        return Optional.of(
            StaticLinkageError.errorIncompatibleClassChange(
                reference, classFileLocation, isSourceClassReachable));
      }

      if (!isClassAccessibleFrom(targetClass, sourceClassName)) {
        return Optional.of(
            StaticLinkageError.errorInaccessibleClass(
                reference, classFileLocation, isSourceClassReachable));
      }
      return Optional.empty();
    } catch (ClassNotFoundException ex) {
      if (classDumper.isUnusedClassSymbolReference(reference)
          || classDumper.catchesNoClassDefFoundError(reference)) {
        // The class reference is unused in the source
        return Optional.empty();
      }
      return Optional.of(
          StaticLinkageError.errorMissingTargetClass(reference, isSourceClassReachable));
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
