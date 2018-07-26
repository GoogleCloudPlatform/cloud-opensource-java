package com.google.cloud.tools.opensource.dependencies;

import java.util.List;

import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.DependencyResolutionException;

public class DependencyLister {

  public static void main(String[] args)
      throws DependencyCollectionException, DependencyResolutionException {
    
    if (args.length != 1 || !args[0].contains(":")) {
      System.err.println("Usage: java " + DependencyLister.class.getCanonicalName()
          + " groupdId:artifactId:version");
      return;
    }
    
    String[] coordinates = args[0].split(":");
    String groupId = coordinates[0];
    String artifactId = coordinates[1];
    String version = coordinates[2];
    
    DependencyGraph graph = DependencyGraphBuilder.getCompleteDependencies(groupId, artifactId, version);
    
    List<DependencyPath> paths = graph.list();
    for (DependencyPath path : paths) { 
      System.out.println(path);
    }
  }

}
