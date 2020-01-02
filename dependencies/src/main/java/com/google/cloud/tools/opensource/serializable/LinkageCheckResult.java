package com.google.cloud.tools.opensource.serializable;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import java.time.ZonedDateTime;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;

final public class LinkageCheckResult {

  final ImmutableList<String> classPathArtifacts;

  final ImmutableMap<String, SymbolProblem> symbolProblems;

  final ImmutableListMultimap<String, ClassFile> references;

  @JsonIgnore
  final ZonedDateTime timestamp;

  @JsonCreator
  public LinkageCheckResult(@JsonProperty("classPathArtifacts") ImmutableList<String> classPathArtifacts,
      @JsonProperty("symbolProblems")  ImmutableMap<String, SymbolProblem> symbolProblems,
      @JsonProperty("references") ImmutableListMultimap<String, ClassFile> references) {
    this.classPathArtifacts = classPathArtifacts;
    this.symbolProblems = symbolProblems;
    this.references = references;
    this.timestamp = null;
  }

  public LinkageCheckResult(List<Artifact> classPathArtifacts,
      Multimap<SymbolProblem, ClassFile> symbolProblems) {
    ImmutableList<SymbolProblem> keys = ImmutableList.copyOf(symbolProblems.keySet());
    this.symbolProblems = Maps.uniqueIndex(keys, SymbolProblem::toString);

    ImmutableListMultimap.Builder<String, ClassFile> builder = ImmutableListMultimap.builder();

    symbolProblems.forEach((key, value) ->
        builder.put(key.toString(), value));

    this.references = builder.build();

    this.classPathArtifacts = classPathArtifacts.stream().map(Artifacts::toCoordinates)
        .collect(toImmutableList());

    timestamp = ZonedDateTime.now();
  }

  public ImmutableMap<String, SymbolProblem> getSymbolProblems() {
    return symbolProblems;
  }

  public ImmutableListMultimap<String, ClassFile> getReferences() {
    return references;
  }

  public ImmutableList<String> getClassPathArtifacts() {
    return classPathArtifacts;
  }

  public String getTimestamp() {
    return timestamp.toString();
  }
}
