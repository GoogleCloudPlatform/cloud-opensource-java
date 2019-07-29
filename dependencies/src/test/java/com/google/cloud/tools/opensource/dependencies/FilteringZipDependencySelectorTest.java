package com.google.cloud.tools.opensource.dependencies;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.junit.Test;

public class FilteringZipDependencySelectorTest {

  private FilteringZipDependencySelector selector = new FilteringZipDependencySelector();

  @Test
  public void testDeriveChildSelector() {
    DependencySelector derivedSelector = selector.deriveChildSelector(null);
    assertSame(selector, derivedSelector);
  }

  @Test
  public void testFilteringZip() {
    Artifact artifact =
        new DefaultArtifact(
            "org.apache.logging.log4j:log4j-api-java9:zip:2.11.1", ImmutableMap.of("type", "zip"));
    Dependency dependency = new Dependency(artifact, "compile");
    assertFalse(selector.selectDependency(dependency));
  }

  @Test
  public void testFilteringNonZip() {
    Artifact artifact = new DefaultArtifact("com.google.guava", "guava", "jar", "28.0-android");
    Dependency dependency = new Dependency(artifact, "compile");
    assertTrue(selector.selectDependency(dependency));
  }
}
