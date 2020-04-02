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

import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.FieldOrMethod;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.eclipse.aether.artifact.Artifact;

/** A tool to find linkage errors in a class path. */
public class LinkageChecker {

  private static final Logger logger = Logger.getLogger(LinkageChecker.class.getName());
  
  private final ClassDumper classDumper;
  private final ImmutableList<ClassPathEntry> classPath;
  private final SymbolReferenceMaps classToSymbols;
  private final ClassReferenceGraph classReferenceGraph;
  private final ExcludedErrors excludedErrors;

  @VisibleForTesting
  SymbolReferenceMaps getClassToSymbols() {
    return classToSymbols;
  }

  public ClassReferenceGraph getClassReferenceGraph() {
    return classReferenceGraph;
  }

  public static LinkageChecker create(List<ClassPathEntry> classPath) throws IOException {
    return create(classPath, ImmutableSet.copyOf(classPath), null);
  }

  /**
   * Returns Linkage Checker for {@code classPath}.
   *
   * @param classPath JAR files to find linkage errors in
   * @param entryPoints JAR files to specify entry point classes in reachability
   * @param exclusionFile exclusion file to suppress linkage errors
   */
  public static LinkageChecker create(
      List<ClassPathEntry> classPath,
      Iterable<ClassPathEntry> entryPoints,
      @Nullable Path exclusionFile)
      throws IOException {
    Preconditions.checkArgument(!classPath.isEmpty(), "The linkage classpath is empty.");
    ClassDumper dumper = ClassDumper.create(classPath);
    SymbolReferenceMaps symbolReferenceMaps = dumper.findSymbolReferences();

    ClassReferenceGraph classReferenceGraph =
        ClassReferenceGraph.create(symbolReferenceMaps, ImmutableSet.copyOf(entryPoints));

    return new LinkageChecker(
        dumper,
        classPath,
        symbolReferenceMaps,
        classReferenceGraph,
        ExcludedErrors.create(exclusionFile));
  }

  public static LinkageChecker create(Bom bom) throws IOException {
    return create(bom, null);
  }

  public static LinkageChecker create(Bom bom, Path exclusionFile) throws IOException {
    // duplicate code from DashboardMain follows. We need to refactor to extract this.
    ImmutableList<Artifact> managedDependencies = bom.getManagedDependencies();

    ClassPathBuilder classPathBuilder = new ClassPathBuilder();
    ClassPathResult classPathResult = classPathBuilder.resolve(managedDependencies);
    ImmutableList<ClassPathEntry> classpath = classPathResult.getClassPath();

    // When checking a BOM, entry point classes are the ones in the artifacts listed in the BOM
    List<ClassPathEntry> artifactsInBom = classpath.subList(0, managedDependencies.size());
    ImmutableSet<ClassPathEntry> entryPoints = ImmutableSet.copyOf(artifactsInBom);

    return LinkageChecker.create(classpath, entryPoints, exclusionFile);
  }

  @VisibleForTesting
  LinkageChecker cloneWith(SymbolReferenceMaps newSymbolMaps) {
    return new LinkageChecker(
        classDumper, classPath, newSymbolMaps, classReferenceGraph, excludedErrors);
  }

  private LinkageChecker(
      ClassDumper classDumper,
      List<ClassPathEntry> classPath,
      SymbolReferenceMaps symbolReferenceMaps,
      ClassReferenceGraph classReferenceGraph,
      ExcludedErrors excludedErrors) {
    this.classDumper = Preconditions.checkNotNull(classDumper);
    this.classPath = ImmutableList.copyOf(classPath);
    this.classReferenceGraph = Preconditions.checkNotNull(classReferenceGraph);
    this.classToSymbols = Preconditions.checkNotNull(symbolReferenceMaps);
    this.excludedErrors = Preconditions.checkNotNull(excludedErrors);
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
          if (classSymbol instanceof SuperClassSymbol) {
            ImmutableList<SymbolProblem> problems =
                findAbstractParentProblems(classFile, (SuperClassSymbol) classSymbol);
            if (!problems.isEmpty()) {
              String superClassName = classSymbol.getClassBinaryName();
              ClassPathEntry superClassLocation = classDumper.findClassLocation(superClassName);
              ClassFile superClassFile = new ClassFile(superClassLocation, superClassName);
              for (SymbolProblem problem : problems) {
                problemToClass.put(problem, superClassFile);
              }
            }
          }
          if (!classFile.getClassPathEntry().getClassNames()
              .contains(classSymbol.getClassBinaryName())) {

            if (classSymbol instanceof InterfaceSymbol) {
              ImmutableList<SymbolProblem> problems =
                  findInterfaceProblems(classFile, (InterfaceSymbol) classSymbol);
              if (!problems.isEmpty()) {
                String interfaceName = classSymbol.getClassBinaryName();
                ClassPathEntry interfaceLocation = classDumper.findClassLocation(interfaceName);
                ClassFile interfaceClassFile = new ClassFile(interfaceLocation, interfaceName);
                for (SymbolProblem problem : problems) {
                  problemToClass.put(problem, interfaceClassFile);
                }
              }
            } else {
              findSymbolProblem(classFile, classSymbol)
                  .ifPresent(problem -> problemToClass.put(problem, classFile.topLevelClassFile()));
            }
          }
        });

    ImmutableSetMultimap<ClassFile, MethodSymbol> classToMethodSymbols =
        classToSymbols.getClassToMethodSymbols();
    classToMethodSymbols.forEach(
        (classFile, methodSymbol) -> {
          if (!classFile.getClassPathEntry().getClassNames()
              .contains(methodSymbol.getClassBinaryName())) {
            findSymbolProblem(classFile, methodSymbol)
                .ifPresent(problem -> problemToClass.put(problem, classFile.topLevelClassFile()));
          }
        });

    ImmutableSetMultimap<ClassFile, FieldSymbol> classToFieldSymbols =
        classToSymbols.getClassToFieldSymbols();
    classToFieldSymbols.forEach(
        (classFile, fieldSymbol) -> {
          if (!classFile.getClassPathEntry().getClassNames()
              .contains(fieldSymbol.getClassBinaryName())) {
            findSymbolProblem(classFile, fieldSymbol)
                .ifPresent(problem -> problemToClass.put(problem, classFile.topLevelClassFile()));
          }
        });

    // Filter classes in whitelist
    SetMultimap<SymbolProblem, ClassFile> filteredMap =
        Multimaps.filterEntries(problemToClass.build(), this::problemFilter);
    return ImmutableSetMultimap.copyOf(filteredMap);
  }

  /**
   * Returns true if the linkage error {@code entry} should be reported. False if it should be
   * suppressed.
   */
  private boolean problemFilter(Map.Entry<SymbolProblem, ClassFile> entry) {
    SymbolProblem symbolProblem = entry.getKey();
    ClassFile sourceClass = entry.getValue();
    return !excludedErrors.contains(symbolProblem, sourceClass);
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
    String sourceClassName = classFile.getBinaryName();
    String targetClassName = symbol.getClassBinaryName();
    String methodName = symbol.getName();

    // Skip references to Java runtime class. For example, java.lang.String.
    if (classDumper.isSystemClass(targetClassName)) {
      return Optional.empty();
    }

    try {
      JavaClass targetJavaClass = classDumper.loadJavaClass(targetClassName);
      ClassPathEntry classFileLocation = classDumper.findClassLocation(targetClassName);
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

      // Check the existence of the parent class or interface for the class
      Optional<SymbolProblem> parentSymbolProblem = findParentSymbolProblem(targetClassName);
      if (parentSymbolProblem.isPresent()) {
        return parentSymbolProblem;
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

      // Slf4J catches LinkageError to check the existence of other classes
      if (classDumper.catchesLinkageError(sourceClassName)) {
        return Optional.empty();
      }

      // The class is in class path but the symbol is not found
      return Optional.of(
          new SymbolProblem(symbol, ErrorType.SYMBOL_NOT_FOUND, containingClassFile));
    } catch (ClassNotFoundException ex) {
      if (classDumper.catchesLinkageError(sourceClassName)) {
        return Optional.empty();
      }
      ClassSymbol classSymbol = new ClassSymbol(symbol.getClassBinaryName());
      return Optional.of(new SymbolProblem(classSymbol, ErrorType.CLASS_NOT_FOUND, null));
    }
  }

  /**
   * Returns the linkage errors for unimplemented methods in {@code classFile}. Such unimplemented
   * methods manifest as {@link AbstractMethodError} in runtime.
   */
  private ImmutableList<SymbolProblem> findInterfaceProblems(
      ClassFile classFile, InterfaceSymbol interfaceSymbol) {
    String interfaceName = interfaceSymbol.getClassBinaryName();
    if (classDumper.isSystemClass(interfaceName)) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<SymbolProblem> builder = ImmutableList.builder();
    try {
      JavaClass implementingClass = classDumper.loadJavaClass(classFile.getBinaryName());
      if (implementingClass.isAbstract()) {
        // Abstract class does not need to implement methods in an interface.
        return ImmutableList.of();
      }
      JavaClass interfaceDefinition = classDumper.loadJavaClass(interfaceName);
      for (Method interfaceMethod : interfaceDefinition.getMethods()) {
        if (interfaceMethod.getCode() != null) {
          // This interface method has default implementation. Subclass does not have to implement
          // it.
          continue;
        }
        String interfaceMethodName = interfaceMethod.getName();
        String interfaceMethodDescriptor = interfaceMethod.getSignature();
        boolean methodFound = false;

        Iterable<JavaClass> typesToCheck = Iterables.concat(getClassHierarchy(implementingClass));
        for (JavaClass javaClass : typesToCheck) {
          for (Method method : javaClass.getMethods()) {
            if (method.getName().equals(interfaceMethodName)
                && method.getSignature().equals(interfaceMethodDescriptor)) {
              methodFound = true;
              break;
            }
          }
        }
        if (!methodFound) {
          MethodSymbol missingMethodOnClass =
              new MethodSymbol(
                  classFile.getBinaryName(), interfaceMethodName, interfaceMethodDescriptor, false);
          builder.add(
              new SymbolProblem(missingMethodOnClass, ErrorType.ABSTRACT_METHOD, classFile));
        }
      }
    } catch (ClassNotFoundException ex) {
      // Missing classes are reported by findSymbolProblem method.
    }
    return builder.build();
  }

  /**
   * Returns an {@code Optional} describing the linkage error for the field reference if the
   * reference does not have a valid referent in the input class path; otherwise an empty {@code
   * Optional}.
   */
  @VisibleForTesting
  Optional<SymbolProblem> findSymbolProblem(ClassFile classFile, FieldSymbol symbol) {
    String sourceClassName = classFile.getBinaryName();
    String targetClassName = symbol.getClassBinaryName();

    String fieldName = symbol.getName();
    try {
      JavaClass targetJavaClass = classDumper.loadJavaClass(targetClassName);
      ClassPathEntry classFileLocation = classDumper.findClassLocation(targetClassName);
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
      if (classDumper.catchesLinkageError(sourceClassName)) {
        return Optional.empty();
      }
      ClassSymbol classSymbol = new ClassSymbol(symbol.getClassBinaryName());
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
    String sourceClassName = classFile.getBinaryName();
    String targetClassName = symbol.getClassBinaryName();

    try {
      JavaClass targetClass = classDumper.loadJavaClass(targetClassName);
      ClassPathEntry classFileLocation = classDumper.findClassLocation(targetClassName);
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
          || classDumper.catchesLinkageError(sourceClassName)) {
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

  /**
   * Returns an {@code Optional} describing the symbol problem in the parent classes or interfaces
   * of {@code baseClassName}, if any of them are missing; otherwise an empty {@code Optional}.
   */
  private Optional<SymbolProblem> findParentSymbolProblem(String baseClassName) {
    Queue<String> queue = new ArrayDeque<>();
    queue.add(baseClassName);
    while (!queue.isEmpty()) {
      String className = queue.remove();
      if (Object.class.getName().equals(className)) {
        continue; // java.lang.Object is the root of the inheritance tree
      }
      String potentiallyMissingClassName = className;
      try {
        JavaClass baseClass = classDumper.loadJavaClass(className);
        queue.add(baseClass.getSuperclassName());

        for (String interfaceName : baseClass.getInterfaceNames()) {
          potentiallyMissingClassName = interfaceName;
          JavaClass interfaceClass = classDumper.loadJavaClass(interfaceName);
          // An interface may implement other interfaces
          queue.addAll(Arrays.asList(interfaceClass.getInterfaceNames()));
        }
      } catch (ClassNotFoundException ex) {
        // potentiallyMissingClassName (either className or interfaceName) is missing
        SymbolProblem problem =
            new SymbolProblem(
                new ClassSymbol(potentiallyMissingClassName), ErrorType.SYMBOL_NOT_FOUND, null);
        return Optional.of(problem);
      }
    }
    return Optional.empty();
  }

  private ImmutableList<SymbolProblem> findAbstractParentProblems(
      ClassFile classFile, SuperClassSymbol superClassSymbol) {
    ImmutableList.Builder<SymbolProblem> builder = ImmutableList.builder();
    String superClassName = superClassSymbol.getClassBinaryName();
    if (classDumper.isSystemClass(superClassName)) {
      return ImmutableList.of();
    }

    try {
      String className = classFile.getBinaryName();
      JavaClass implementingClass = classDumper.loadJavaClass(className);
      if (implementingClass.isAbstract()) {
        return ImmutableList.of();
      }

      JavaClass superClass = classDumper.loadJavaClass(superClassName);
      if (!superClass.isAbstract()) {
        return ImmutableList.of();
      }

      JavaClass abstractClass = superClass;

      // Equality of BCEL's Method class is on its name and descriptor field
      Set<Method> implementedMethods = new HashSet<>();
      implementedMethods.addAll(ImmutableList.copyOf(implementingClass.getMethods()));

      while (abstractClass.isAbstract()) {
        for (Method abstractMethod : abstractClass.getMethods()) {
          if (!abstractMethod.isAbstract()) {
            // This abstract method has implementation. Subclass does not have to implement it.
            implementedMethods.add(abstractMethod);
            continue;
          }
          if (implementedMethods.contains(abstractMethod)) {
            continue;
          }
          String unimplementedMethodName = abstractMethod.getName();
          String unimplementedMethodDescriptor = abstractMethod.getSignature();

          MethodSymbol missingMethodOnClass =
              new MethodSymbol(
                  className, unimplementedMethodName, unimplementedMethodDescriptor, false);
          builder.add(
              new SymbolProblem(missingMethodOnClass, ErrorType.ABSTRACT_METHOD, classFile));
        }
        abstractClass = abstractClass.getSuperClass();
      }
    } catch (ClassNotFoundException ex) {
      // Missing classes are reported by findSymbolProblem method.
    }
    return builder.build();
  }
}
