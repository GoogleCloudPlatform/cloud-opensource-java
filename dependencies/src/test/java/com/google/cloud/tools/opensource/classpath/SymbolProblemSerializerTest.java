package com.google.cloud.tools.opensource.classpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.cloud.tools.opensource.serializable.LinkageCheckResult;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.LinkedListMultimap;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Test;

public class SymbolProblemSerializerTest {

  SymbolProblemSerializer serializer = new SymbolProblemSerializer();


  @Test
  public void testDeserialize() throws Exception{
    String name = "com.google.cloud_google-cloud-bigquery_1.101.0___io.grpc_grpc-alts_1.26.0.json";

    Path input = Paths.get("../linkage-check-cache/com.google.cloud_libraries-bom_3.3.0/"+name);
    LinkageCheckResult result = serializer.deserialize(input);

    assertNotNull(result);
  }
}
