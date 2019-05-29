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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.FieldOrMethod;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

/** A tool to find linkage errors in a class path. */
public class LinkageChecker {

  private static final Logger logger = Logger.getLogger(LinkageChecker.class.getName());

  private final ClassDumper classDumper;
  private final ImmutableList<Path> jars;
  private final SymbolReferenceMaps classToSymbols;
  private final ClassReferenceGraph classReferenceGraph;

  @VisibleForTesting
  SymbolReferenceMaps getClassToSymbols() {
    return classToSymbols;
  }

  public ClassReferenceGraph getClassReferenceGraph() {
    return classReferenceGraph;
  }

  public static LinkageChecker create(List<Path> jars, Iterable<Path> entryPoints)
      throws IOException {
    Preconditions.checkArgument(
        !jars.isEmpty(),
        "The linkage classpath is empty. Specify input to supply one or more jar files");
    ClassDumper dumper = ClassDumper.create(jars);
    SymbolReferenceMaps symbolReferenceMaps = dumper.findSymbolReferences();

    ClassReferenceGraph classReferenceGraph =
        ClassReferenceGraph.create(symbolReferenceMaps, ImmutableSet.copyOf(entryPoints));

    return new LinkageChecker(dumper, jars, symbolReferenceMaps, classReferenceGraph);
  }

  @VisibleForTesting
  LinkageChecker cloneWith(SymbolReferenceMaps newSymbolMaps) {
    return new LinkageChecker(classDumper, jars, newSymbolMaps, classReferenceGraph);
  }

  private LinkageChecker(
      ClassDumper classDumper,
      List<Path> jars,
      SymbolReferenceMaps symbolReferenceMaps,
      ClassReferenceGraph classReferenceGraph) {
    this.classDumper = Preconditions.checkNotNull(classDumper);
    this.jars = ImmutableList.copyOf(jars);
    this.classReferenceGraph = Preconditions.checkNotNull(classReferenceGraph);
    this.classToSymbols = Preconditions.checkNotNull(symbolReferenceMaps);
  }

  /**
   * Returns {@link SymbolProblem}s found in the class path and referencing classes for each
   * problem.
   */
  public ImmutableSetMultimap<SymbolProblem, ClassFile> findSymbolProblems() {
    // Having Problem in key will dedup SymbolProblems
    ImmutableSetMultimap.Builder<SymbolProblem, ClassFile> problemToClass =
        ImmutableSetMultimap.builder();

    ImmutableSetMultimap<ClassFile, ClassSymbol> classToClassSymbols =
        classToSymbols.getClassToClassSymbols();
    classToClassSymbols.forEach(
        (classFile, classSymbol) -> {
          if (!classDumper
              .classesDefinedInJar(classFile.getJar())
              .contains(classSymbol.getClassName())) {
            findSymbolProblem(classFile, classSymbol)
                .ifPresent(problem -> problemToClass.put(problem, classFile));
          }
        });

    ImmutableSetMultimap<ClassFile, MethodSymbol> classToMethodSymbols =
        classToSymbols.getClassToMethodSymbols();
    classToMethodSymbols.forEach(
        (classFile, methodSymbol) -> {
          if (!classDumper
              .classesDefinedInJar(classFile.getJar())
              .contains(methodSymbol.getClassName())) {
            findSymbolProblem(classFile, methodSymbol)
                .ifPresent(problem -> problemToClass.put(problem, classFile));
          }
        });

    ImmutableSetMultimap<ClassFile, FieldSymbol> classToFieldSymbols =
        classToSymbols.getClassToFieldSymbols();
    classToFieldSymbols.forEach(
        (classFile, fieldSymbol) -> {
          if (!classDumper
              .classesDefinedInJar(classFile.getJar())
              .contains(fieldSymbol.getClassName())) {
            findSymbolProblem(classFile, fieldSymbol)
                .ifPresent(problem -> problemToClass.put(problem, classFile));
          }
        });

    return problemToClass.build();
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
  Optional<SymbolProblem> findSymbolProblem(ClassFile classFile, MethodSymbol symbol) {
    String sourceClassName = classFile.getClassName();
    String targetClassName = symbol.getClassName();
    String methodName = symbol.getName();

    // Skip references to Java runtime class. For example, java.lang.String.
    if (classDumper.isSystemClass(targetClassName)) {
      return Optional.empty();
    }

    try {
      JavaClass targetJavaClass = classDumper.loadJavaClass(targetClassName);
      Path classFileLocation = classDumper.findClassLocation(targetClassName);
      ClassFile containingClassFile =
          classFileLocation == null ? null : new ClassFile(classFileLocation, targetClassName);

      if (!isClassAccessibleFrom(targetJavaClass, sourceClassName)) {
        return Optional.of(
            new SymbolProblem(symbol, ErrorType.INACCESSIBLE_CLASS, containingClassFile));
      }

      if (targetJavaClass.isInterface() != symbol.isInterfaceMethod()) {
        return Optional.of(
            new SymbolProblem(symbol, ErrorType.INCOMPATIBLE_CLASS_CHANGE, containingClassFile));
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
              && method.getSignature().equals(symbol.getDescriptor())) {
            if (!isMemberAccessibleFrom(javaClass, method, sourceClassName)) {
              return Optional.of(
                  new SymbolProblem(symbol, ErrorType.INACCESSIBLE_MEMBER, containingClassFile));
            }
            // The method is found and accessible. Returning no error.
            return Optional.empty();
          }
        }
      }

      // The class is in class path but the symbol is not found
      return Optional.of(
          new SymbolProblem(symbol, ErrorType.SYMBOL_NOT_FOUND, containingClassFile));
    } catch (ClassNotFoundException ex) {
      if (classDumper.catchesNoClassDefFoundError(sourceClassName)) {
        return Optional.empty();
      }
      ClassSymbol classSymbol = new ClassSymbol(symbol.getClassName());
      return Optional.of(new SymbolProblem(classSymbol, ErrorType.CLASS_NOT_FOUND, null));
    }
  }

  /**
   * Returns an {@code Optional} describing the linkage error for the field reference if the
   * reference does not have a valid referent in the input class path; otherwise an empty {@code
   * Optional}.
   */
  @VisibleForTesting
  Optional<SymbolProblem> findSymbolProblem(ClassFile classFile, FieldSymbol symbol) {
    String sourceClassName = classFile.getClassName();
    String targetClassName = symbol.getClassName();

    String fieldName = symbol.getName();
    try {
      JavaClass targetJavaClass = classDumper.loadJavaClass(targetClassName);
      Path classFileLocation = classDumper.findClassLocation(targetClassName);
      ClassFile containingClassFile =
          classFileLocation == null ? null : new ClassFile(classFileLocation, targetClassName);

      if (!isClassAccessibleFrom(targetJavaClass, sourceClassName)) {
        return Optional.of(
            new SymbolProblem(symbol, ErrorType.INACCESSIBLE_CLASS, containingClassFile));
      }

      for (JavaClass javaClass : getClassHierarchy(targetJavaClass)) {
        for (Field field : javaClass.getFields()) {
          if (field.getName().equals(fieldName)) {
            if (!isMemberAccessibleFrom(javaClass, field, sourceClassName)) {
              return Optional.of(
                  new SymbolProblem(symbol, ErrorType.INACCESSIBLE_MEMBER, containingClassFile));
            }
            // The field is found and accessible. Returning no error.
            return Optional.empty();
          }
        }
      }
      // The field was not found in the class from the classpath
      return Optional.of(
          new SymbolProblem(symbol, ErrorType.SYMBOL_NOT_FOUND, containingClassFile));
    } catch (ClassNotFoundException ex) {
      if (classDumper.catchesNoClassDefFoundError(sourceClassName)) {
        return Optional.empty();
      }
      ClassSymbol classSymbol = new ClassSymbol(symbol.getClassName());
      return Optional.of(new SymbolProblem(classSymbol, ErrorType.CLASS_NOT_FOUND, null));
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
  Optional<SymbolProblem> findSymbolProblem(ClassFile classFile, ClassSymbol symbol) {
    String sourceClassName = classFile.getClassName();
    String targetClassName = symbol.getClassName();

    try {
      JavaClass targetClass = classDumper.loadJavaClass(targetClassName);
      Path classFileLocation = classDumper.findClassLocation(targetClassName);
      ClassFile containingClassFile =
          classFileLocation == null ? null : new ClassFile(classFileLocation, targetClassName);

      boolean isSubclassReference = symbol instanceof SuperClassSymbol;
      if (isSubclassReference
          && !classDumper.hasValidSuperclass(
              classDumper.loadJavaClass(sourceClassName), targetClass)) {
        return Optional.of(
            new SymbolProblem(symbol, ErrorType.INCOMPATIBLE_CLASS_CHANGE, containingClassFile));
      }

      if (!isClassAccessibleFrom(targetClass, sourceClassName)) {
        return Optional.of(
            new SymbolProblem(symbol, ErrorType.INACCESSIBLE_CLASS, containingClassFile));
      }
      return Optional.empty();
    } catch (ClassNotFoundException ex) {
      if (classDumper.isUnusedClassSymbolReference(sourceClassName, symbol)
          || classDumper.catchesNoClassDefFoundError(sourceClassName)) {
        // The class reference is unused in the source
        return Optional.empty();
      }
      return Optional.of(new SymbolProblem(symbol, ErrorType.CLASS_NOT_FOUND, null));
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
