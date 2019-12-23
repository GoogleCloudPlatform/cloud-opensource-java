package com.google.cloud.tools.opensource.classpath;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.cloud.tools.opensource.serializable.LinkageCheckResult;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.eclipse.aether.artifact.Artifact;

public class SymbolProblemSerializer {

  private final ObjectMapper mapper;

  SymbolProblemSerializer() {
    mapper = new ObjectMapper();
    mapper.registerModule(new GuavaModule());
    mapper.registerModule(new Jdk8Module());

  }

  public void serialize(ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems,
      Multimap<Path, DependencyPath> jarToDependencyPaths, Path output) throws IOException {

    ImmutableListMultimap.Builder<com.google.cloud.tools.opensource.serializable.SymbolProblem, com.google.cloud.tools.opensource.serializable.ClassFile> builder = ImmutableListMultimap.builder();

    symbolProblems.forEach((symbolProblem, classFile) -> {

      com.google.cloud.tools.opensource.serializable.ClassFile value = convert(classFile,
          jarToDependencyPaths);

      com.google.cloud.tools.opensource.serializable.ClassFile containingClass = convert(
          symbolProblem.getContainingClass(), jarToDependencyPaths);

      builder.put(new com.google.cloud.tools.opensource.serializable.SymbolProblem(symbolProblem.getSymbol(),
          symbolProblem.getErrorType(), containingClass),
          value);
    });

    ImmutableListMultimap<com.google.cloud.tools.opensource.serializable.SymbolProblem, com.google.cloud.tools.opensource.serializable.ClassFile> serializableProblems = builder
        .build();


    ImmutableList<Path> classpath = ImmutableList.copyOf(jarToDependencyPaths.keySet());

    ImmutableList<Artifact> classPathArtifacts = classpath.stream()
        .map(path -> jarToDependencyPaths.get(path))
        .map(dependencyPaths -> Iterables.getFirst(dependencyPaths, null))
        .map(DependencyPath::getLeaf)
        .collect(toImmutableList());

    LinkageCheckResult result = new LinkageCheckResult(classPathArtifacts, serializableProblems);
    mapper.writeValue(output.toFile(), result);
  }

  private static com.google.cloud.tools.opensource.serializable.ClassFile convert(ClassFile classFile, Multimap<Path, DependencyPath> jarToDependencyPaths) {
    if (classFile == null) {
      return null;
    }
    Path jar = classFile.getJar();
    DependencyPath dependencyPath = Iterables
        .getFirst(jarToDependencyPaths.get(jar), null);
    if (dependencyPath == null) {
      throw new RuntimeException("JAR file was not found in jarToDependencyPaths" + jar);
    }
    Artifact artifact = dependencyPath.getLeaf();
    String coordinates = Artifacts.toCoordinates(artifact);
    return new com.google.cloud.tools.opensource.serializable.ClassFile(
        coordinates, classFile.getClassName());
  }

}
