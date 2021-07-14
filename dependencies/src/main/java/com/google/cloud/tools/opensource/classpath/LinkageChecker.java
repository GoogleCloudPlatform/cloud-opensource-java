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
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.UnresolvableArtifactProblem;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.FieldOrMethod;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.Utility;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.version.InvalidVersionSpecificationException;

/** A tool to find linkage errors in a class path. */
public class LinkageChecker {

  private static final Logger logger = Logger.getLogger(LinkageChecker.class.getName());
  
  private final ClassDumper classDumper;
  private final ImmutableList<ClassPathEntry> classPath;
  private final SymbolReferences symbolReferences;
  private final ClassReferenceGraph classReferenceGraph;
  private final ExcludedErrors excludedErrors;

  @VisibleForTesting
  SymbolReferences getSymbolReferences() {
    return symbolReferences;
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
    SymbolReferences symbolReferenceMaps = dumper.findSymbolReferences();

    ClassReferenceGraph classReferenceGraph =
        ClassReferenceGraph.create(symbolReferenceMaps, ImmutableSet.copyOf(entryPoints));

    return new LinkageChecker(
        dumper,
        classPath,
        symbolReferenceMaps,
        classReferenceGraph,
        ExcludedErrors.create(exclusionFile));
  }

  public static LinkageChecker create(Bom bom)
      throws IOException, InvalidVersionSpecificationException {
    return create(bom, null);
  }

  public static LinkageChecker create(Bom bom, Path exclusionFile)
      throws IOException, InvalidVersionSpecificationException {
    // duplicate code from DashboardMain follows. We need to refactor to extract this.
    ImmutableList<Artifact> managedDependencies = bom.getManagedDependencies();

    ClassPathBuilder classPathBuilder = new ClassPathBuilder();
    ClassPathResult classPathResult =
        classPathBuilder.resolve(managedDependencies, true, DependencyMediation.MAVEN);
    ImmutableList<ClassPathEntry> classpath = classPathResult.getClassPath();

    ImmutableList<UnresolvableArtifactProblem> artifactProblems =
        classPathResult.getArtifactProblems();
    if (!artifactProblems.isEmpty()) {
      for (UnresolvableArtifactProblem artifactProblem : artifactProblems) {
        logger.severe(artifactProblem.toString());
      }
      throw new IOException(
          "Could not resolve "
              + (artifactProblems.size() == 1
                  ? "1 dependency"
                  : (artifactProblems.size() + " dependencies"))
              + ". See the message above for details.");
    }

    // When checking a BOM, entry point classes are the ones in the artifacts listed in the BOM
    List<ClassPathEntry> artifactsInBom = classpath.subList(0, managedDependencies.size());
    ImmutableSet<ClassPathEntry> entryPoints = ImmutableSet.copyOf(artifactsInBom);

    return LinkageChecker.create(classpath, entryPoints, exclusionFile);
  }

  @VisibleForTesting
  LinkageChecker cloneWith(SymbolReferences newSymbolMaps) {
    return new LinkageChecker(
        classDumper, classPath, newSymbolMaps, classReferenceGraph, excludedErrors);
  }

  private LinkageChecker(
      ClassDumper classDumper,
      List<ClassPathEntry> classPath,
      SymbolReferences symbolReferenceMaps,
      ClassReferenceGraph classReferenceGraph,
      ExcludedErrors excludedErrors) {
    this.classDumper = Preconditions.checkNotNull(classDumper);
    this.classPath = ImmutableList.copyOf(classPath);
    this.classReferenceGraph = Preconditions.checkNotNull(classReferenceGraph);
    this.symbolReferences = Preconditions.checkNotNull(symbolReferenceMaps);
    this.excludedErrors = Preconditions.checkNotNull(excludedErrors);
  }

  /**
   * Searches the classpath for linkage errors.
   *
   * @return {@link LinkageProblem}s found in the class path and referencing classes
   * @throws IOException I/O error reading files in the classpath
   */
  public ImmutableSet<LinkageProblem> findLinkageProblems() throws IOException {
    ImmutableSet.Builder<LinkageProblem> problemToClass = ImmutableSet.builder();

    // This sourceClassFile is a source of references to other symbols.
    for (ClassFile classFile : symbolReferences.getClassFiles()) {
      ImmutableSet<ClassSymbol> classSymbols = symbolReferences.getClassSymbols(classFile);
      for (ClassSymbol classSymbol : classSymbols) {
        if (classSymbol instanceof SuperClassSymbol) {
          String superClassName = classSymbol.getClassBinaryName();
          ClassPathEntry superClassLocation = classDumper.findClassLocation(superClassName);
          if (superClassLocation != null) {
            ClassFile superClassFile = new ClassFile(superClassLocation, superClassName);
            ImmutableList<LinkageProblem> problems =
                findAbstractParentProblems(
                    classFile, (SuperClassSymbol) classSymbol, superClassFile);
            for (LinkageProblem problem : problems) {
              problemToClass.add(problem);
            }
          }
        }

        ImmutableSet<String> classFileNames = classFile.getClassPathEntry().getFileNames();
        String classBinaryName = classSymbol.getClassBinaryName();
        String classFileName = classDumper.getFileName(classBinaryName);
        if (!classFileNames.contains(classFileName)) {
          if (classSymbol instanceof InterfaceSymbol) {
            String interfaceName = classSymbol.getClassBinaryName();
            ClassPathEntry interfaceLocation = classDumper.findClassLocation(interfaceName);
            if (interfaceLocation != null) {
              ClassFile interfaceClassFile = new ClassFile(interfaceLocation, interfaceName);
              ImmutableList<LinkageProblem> problems =
                  findInterfaceProblems(
                      interfaceClassFile, (InterfaceSymbol) classSymbol, classFile);
              for (LinkageProblem problem : problems) {
                problemToClass.add(problem);
              }
            }
          } else {
            findLinkageProblem(classFile, classSymbol, classFile.topLevelClassFile())
                .ifPresent(problemToClass::add);
          }
        }
      }    
    }
    
    for (ClassFile classFile : symbolReferences.getClassFiles()) {
      ImmutableSet<MethodSymbol> methodSymbols = symbolReferences.getMethodSymbols(classFile);
      ImmutableSet<String> classFileNames = classFile.getClassPathEntry().getFileNames();
      for (MethodSymbol methodSymbol : methodSymbols) {
        String classBinaryName = methodSymbol.getClassBinaryName();
        String classFileName = classDumper.getFileName(classBinaryName);
        if (!classFileNames.contains(classFileName)) {
          findLinkageProblem(classFile, methodSymbol, classFile.topLevelClassFile())
              .ifPresent(problemToClass::add);
        }
      }
    }

    for (ClassFile classFile : symbolReferences.getClassFiles()) {
      ImmutableSet<FieldSymbol> fieldSymbols = symbolReferences.getFieldSymbols(classFile);
      ImmutableSet<String> classFileNames = classFile.getClassPathEntry().getFileNames();
      for (FieldSymbol fieldSymbol : fieldSymbols) {
        String classBinaryName = fieldSymbol.getClassBinaryName();
        String classFileName = classDumper.getFileName(classBinaryName);
        if (!classFileNames.contains(classFileName)) {
          findLinkageProblem(classFile, fieldSymbol, classFile.topLevelClassFile())
              .ifPresent(problemToClass::add);
        }
      }
    }

    // Filter classes in exclusion file
    ImmutableSet<LinkageProblem> filteredMap =
        problemToClass.build().stream().filter(this::problemFilter).collect(toImmutableSet());
    return filteredMap;
  }

  /**
   * Returns true if the linkage error {@code entry} should be reported. False if it should be
   * suppressed.
   */
  private boolean problemFilter(LinkageProblem linkageProblem) {
    return !excludedErrors.contains(linkageProblem);
  }

  /**
   * Returns an {@code Optional} describing the linkage error for the method reference if the
   * reference does not have a valid referent in the input class path; otherwise an empty {@code
   * Optional}.
   *
   * <p>Because the Java Virtual Machine has special handling for {@link
   * java.lang.invoke.MethodHandle#invoke(Object...)} and {@link
   * java.lang.invoke.MethodHandle#invokeExact(Object...)}, this method does not report the
   * references to them as linkage errors.
   *
   * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.3">Java
   *     Virtual Machine Specification: 5.4.3.3. Method Resolution</a>
   * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.4">Java
   *     Virtual Machine Specification: 5.4.3.4. Interface Method Resolution</a>
   * @see <a
   *     href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-6.html#jvms-6.5.invokevirtual>Java
   *     Virtual Machine Specification: invokevirtual</a>
   */
  @VisibleForTesting
  Optional<LinkageProblem> findLinkageProblem(
      ClassFile classFile, MethodSymbol symbol, ClassFile sourceClassFile) {
    String sourceClassName = classFile.getBinaryName();
    String targetClassName = symbol.getClassBinaryName();
    String methodName = symbol.getName();

    if (ClassDumper.isArrayClass(targetClassName)) {
      return Optional.empty();
    }

    if (targetClassName.equals("java.lang.invoke.MethodHandle")
        && (methodName.equals("invoke") || methodName.equals("invokeExact"))) {
      return Optional.empty();
    }

    try {
      JavaClass targetJavaClass = classDumper.loadJavaClass(targetClassName);
      ClassPathEntry classPathEntry = classDumper.findClassLocation(targetClassName);
      ClassFile targetClassFile =
          classPathEntry == null ? null : new ClassFile(classPathEntry, targetClassName);

      if (!isClassAccessibleFrom(targetJavaClass, sourceClassName)) {
        AccessModifier modifier = AccessModifier.fromFlag(targetJavaClass.getModifiers());
        return Optional.of(
            new InaccessibleClassProblem(
                sourceClassFile,
                targetClassFile,
                new ClassSymbol(symbol.getClassBinaryName()),
                modifier));
      }

      if (targetJavaClass.isInterface() != symbol.isInterfaceMethod()) {
        return Optional.of(
            new IncompatibleClassChangeProblem(sourceClassFile, targetClassFile, symbol));
      }

      // Check the existence of the parent class or interface for the class
      Optional<LinkageProblem> parentLinkageProblem =
          findParentClassLinkageProblem(targetClassName, sourceClassFile);
      if (parentLinkageProblem.isPresent()) {
        return parentLinkageProblem;
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

      String changedReturnType = null;
      for (JavaClass javaClass : typesToCheck) {
        for (Method method : javaClass.getMethods()) {
          if (method.getName().equals(methodName)) {
            String expectedMethodDescriptor = symbol.getDescriptor();
            String actualMethodDescriptor = method.getSignature();
            if (actualMethodDescriptor.equals(expectedMethodDescriptor)) {
              if (!isMemberAccessibleFrom(javaClass, method, sourceClassName)) {
                AccessModifier modifier = AccessModifier.fromFlag(method.getModifiers());
                return Optional.of(
                    new InaccessibleMemberProblem(
                        sourceClassFile, targetClassFile, symbol, modifier));
              }
              // The method is found and accessible. Returning no error.
              return Optional.empty();
            } else {
              String expectedParameterDescriptors =
                  parseParameterDescriptors(expectedMethodDescriptor);
              String actualParameterDescriptors = parseParameterDescriptors(actualMethodDescriptor);
              if (actualParameterDescriptors.equals(expectedParameterDescriptors)) {
                // Not returning result yet, because there can be another supertype that has the
                // exact method that matches the name, argument types, and return type.
                changedReturnType = Utility.methodSignatureReturnType(actualMethodDescriptor);
              }
            }
          }
        }
      }

      if (changedReturnType != null) {
        // When only the return types are different, we can report this specific problem
        // rather than more generic SymbolNotFoundProblem.
        return Optional.of(
            new ReturnTypeChangedProblem(
                sourceClassFile, targetClassFile, symbol, changedReturnType));
      }

      // Slf4J catches LinkageError to check the existence of other classes
      if (classDumper.catchesLinkageErrorOnMethod(sourceClassName)) {
        return Optional.empty();
      }

      // The class is in class path but the symbol is not found
      return Optional.of(new SymbolNotFoundProblem(sourceClassFile, targetClassFile, symbol));
    } catch (ClassNotFoundException ex) {
      if (classDumper.catchesLinkageErrorOnClass(sourceClassName)) {
        return Optional.empty();
      }
      ClassSymbol classSymbol = new ClassSymbol(symbol.getClassBinaryName());
      return Optional.of(new ClassNotFoundProblem(sourceClassFile, classSymbol));
    }
  }

  /**
   * Returns the parameter descriptors from {@code methodDescriptor}.
   *
   * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.3" >Java
   *     Virtual Machine Specification: 4.3.3. Method Descriptors</a>
   */
  private static String parseParameterDescriptors(String methodDescriptor) {
    // E.g., '(Ljava/lang/String;)Ljava/lang/Integer;' => '(Ljava/lang/String;)'
    return methodDescriptor.substring(0, methodDescriptor.indexOf(')') + 1);
  }

  /**
   * Returns the linkage errors for unimplemented methods in {@code classFile}. Such unimplemented
   * methods manifest as {@link AbstractMethodError}s at runtime.
   */
  private ImmutableList<LinkageProblem> findInterfaceProblems(
      ClassFile interfaceClassFile,
      InterfaceSymbol interfaceSymbol,
      ClassFile implementationClassFile) {
    String interfaceName = interfaceSymbol.getClassBinaryName();
    if (classDumper.isSystemClass(interfaceName)) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<LinkageProblem> builder = ImmutableList.builder();
    try {
      JavaClass implementingClass =
          classDumper.loadJavaClass(implementationClassFile.getBinaryName());
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
                  interfaceClassFile.getBinaryName(),
                  interfaceMethodName,
                  interfaceMethodDescriptor,
                  false);
          builder.add(
              new AbstractMethodProblem(
                  implementationClassFile, missingMethodOnClass, interfaceClassFile));
        }
      }
    } catch (ClassNotFoundException ex) {
      // Missing classes are reported by findLinkageProblem method.
    }
    return builder.build();
  }

  /**
   * Returns an {@code Optional} describing the linkage error for the field reference if the
   * reference does not have a valid referent in the input class path; otherwise an empty {@code
   * Optional}.
   */
  @VisibleForTesting
  Optional<LinkageProblem> findLinkageProblem(
      ClassFile classFile, FieldSymbol symbol, ClassFile sourceClassFile) {
    String sourceClassName = classFile.getBinaryName();
    String targetClassName = symbol.getClassBinaryName();

    String fieldName = symbol.getName();
    try {
      JavaClass targetJavaClass = classDumper.loadJavaClass(targetClassName);
      ClassPathEntry classFileLocation = classDumper.findClassLocation(targetClassName);
      ClassFile targetClassFile =
          classFileLocation == null ? null : new ClassFile(classFileLocation, targetClassName);

      if (!isClassAccessibleFrom(targetJavaClass, sourceClassName)) {
        AccessModifier modifier = AccessModifier.fromFlag(targetJavaClass.getModifiers());
        return Optional.of(
            new InaccessibleClassProblem(
                sourceClassFile,
                targetClassFile,
                new ClassSymbol(symbol.getClassBinaryName()),
                modifier));
      }

      for (JavaClass javaClass : getClassHierarchy(targetJavaClass)) {
        for (Field field : javaClass.getFields()) {
          if (field.getName().equals(fieldName)) {
            if (!isMemberAccessibleFrom(javaClass, field, sourceClassName)) {
              AccessModifier modifier = AccessModifier.fromFlag(field.getModifiers());
              return Optional.of(
                  new InaccessibleMemberProblem(
                      sourceClassFile, targetClassFile, symbol, modifier));
            }
            // The field is found and accessible. Returning no error.
            return Optional.empty();
          }
        }
      }
      // The field was not found in the class from the classpath
      return Optional.of(new SymbolNotFoundProblem(sourceClassFile, targetClassFile, symbol));
    } catch (ClassNotFoundException ex) {
      if (classDumper.catchesLinkageErrorOnClass(sourceClassName)) {
        return Optional.empty();
      }
      ClassSymbol classSymbol = new ClassSymbol(symbol.getClassBinaryName());
      return Optional.of(new ClassNotFoundProblem(sourceClassFile, classSymbol));
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
  Optional<LinkageProblem> findLinkageProblem(
      ClassFile classFile, ClassSymbol symbol, ClassFile sourceClassFile) {
    String sourceClassName = classFile.getBinaryName();
    String targetClassName = symbol.getClassBinaryName();

    try {
      JavaClass targetClass = classDumper.loadJavaClass(targetClassName);
      ClassPathEntry classFileLocation = classDumper.findClassLocation(targetClassName);
      ClassFile targetClassFile =
          classFileLocation == null ? null : new ClassFile(classFileLocation, targetClassName);

      boolean isSubclassReference = symbol instanceof SuperClassSymbol;
      if (isSubclassReference
          && !ClassDumper.hasValidSuperclass(
              classDumper.loadJavaClass(sourceClassName), targetClass)) {
        return Optional.of(
            new IncompatibleClassChangeProblem(sourceClassFile, targetClassFile, symbol));
      }

      if (!isClassAccessibleFrom(targetClass, sourceClassName)
          && classDumper.isClassSymbolReferenceUsed(sourceClassName, symbol)) {
        AccessModifier modifier = AccessModifier.fromFlag(targetClass.getModifiers());
        return Optional.of(
            new InaccessibleClassProblem(sourceClassFile, targetClassFile, symbol, modifier));
      }
      return Optional.empty();
    } catch (ClassNotFoundException ex) {
      if (!classDumper.isClassSymbolReferenceUsed(sourceClassName, symbol)
          || classDumper.catchesLinkageErrorOnClass(sourceClassName)) {
        // The class reference is unused in the source, or catches NoClassDefFoundError
        return Optional.empty();
      }
      return Optional.of(new ClassNotFoundProblem(sourceClassFile, symbol));
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
  private Optional<LinkageProblem> findParentClassLinkageProblem(
      String baseClassName, ClassFile sourceClassFile) {
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
        LinkageProblem problem =
            new ClassNotFoundProblem(sourceClassFile, new ClassSymbol(potentiallyMissingClassName));
        return Optional.of(problem);
      }
    }
    return Optional.empty();
  }

  private ImmutableList<LinkageProblem> findAbstractParentProblems(
      ClassFile classFile, SuperClassSymbol superClassSymbol, ClassFile superClassFile) {
    ImmutableList.Builder<LinkageProblem> builder = ImmutableList.builder();
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
          } else if (!implementedMethods.contains(abstractMethod)) {
            String unimplementedMethodName = abstractMethod.getName();
            String unimplementedMethodDescriptor = abstractMethod.getSignature();

            String abstractClassName = abstractClass.getClassName();
            MethodSymbol missingMethodOnClass =
                new MethodSymbol(
                    abstractClassName,
                    unimplementedMethodName,
                    unimplementedMethodDescriptor,
                    false);
            builder.add(new AbstractMethodProblem(classFile, missingMethodOnClass, superClassFile));
          }
        }
        abstractClass = abstractClass.getSuperClass();
      }
    } catch (ClassNotFoundException ex) {
      // Missing classes are reported by findLinkageProblem method.
    }
    return builder.build();
  }
}
