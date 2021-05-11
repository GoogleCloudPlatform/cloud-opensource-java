/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.opensource.lts;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.cloud.tools.opensource.classpath.ClassPathBuilder;
import com.google.cloud.tools.opensource.classpath.ClassPathEntry;
import com.google.cloud.tools.opensource.classpath.ClassPathResult;
import com.google.cloud.tools.opensource.classpath.GradleDependencyMediation;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.MoreFiles;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.Node;
import nu.xom.Nodes;
import nu.xom.ParsingException;
import nu.xom.Text;
import nu.xom.XPathContext;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.version.InvalidVersionSpecificationException;

/**
 * Modifies pom.xml files to use the libraries in the BOM when running the unit tests with the Maven
 * surefire plugin.
 */
class MavenProjectModifier implements BuildFileModifier {
  private static final Logger logger = Logger.getLogger(MavenProjectModifier.class.getName());

  // For Beam's snapshot version
  private static final String APACHE_SNAPSHOT_REPOSITORY_URL =
      "https://repository.apache.org/content/repositories/snapshots/";

  @VisibleForTesting
  static final String MAVEN_POM_NAMESPACE_URL = "http://maven.apache.org/POM/4.0.0";

  @Override
  public void modifyFiles(String name, Path projectDirectory, Bom bom)
      throws TestSetupFailureException {
    try {
      modifyPomFiles(projectDirectory, bom);
    } catch (ArtifactResolutionException
        | InvalidVersionSpecificationException
        | ParsingException
        | IOException ex) {
      throw new TestSetupFailureException("Couldn't modify pom.xml files", ex);
    }
  }

  private static void modifyPomFiles(Path projectRoot, Bom bom)
      throws IOException, ParsingException, InvalidVersionSpecificationException,
          ArtifactResolutionException {
    Iterable<Path> paths = MoreFiles.fileTraverser().breadthFirst(projectRoot);

    ImmutableList<Artifact> bomManagedDependencies = bom.getManagedDependencies();
    DependencyGraphBuilder dependencyGraphBuilder =
        new DependencyGraphBuilder(
            ImmutableList.of(RepositoryUtility.CENTRAL.getUrl(), APACHE_SNAPSHOT_REPOSITORY_URL));
    ClassPathBuilder classPathBuilder = new ClassPathBuilder(dependencyGraphBuilder);
    ClassPathResult resolvedDependencies =
        classPathBuilder.resolve(
            bomManagedDependencies, false, GradleDependencyMediation.withEnforcedPlatform(bom));

    // Build the class path with the following points:
    // 1. Include the BOM members' dependencies as well; otherwise we may get NoClassDefFoundEerror
    // for artifacts that declare new dependencies in newer versions.
    // https://github.com/GoogleCloudPlatform/cloud-opensource-java/pull/1982#issuecomment-812201558
    //
    // 2. Exclude "com.google.android:android" artifact because grpc-api's ServiceProvider
    // behaves incorrectly for Android-mode in the presence of this artifact. Our audience does
    // not include Android.
    // https://github.com/GoogleCloudPlatform/cloud-opensource-java/pull/1982#issuecomment-831441247
    ImmutableList<ClassPathEntry> resolvedManagedDependencies =
        resolvedDependencies.getClassPath().stream()
            .filter(
                classPathEntry ->
                    !"com.google.android:android"
                        .equals(Artifacts.makeKey(classPathEntry.getArtifact())))
            .collect(toImmutableList());

    StringBuilder bomDependencyMessage = new StringBuilder("Dependencies from BOM:\n");
    for (ClassPathEntry resolvedManagedDependency : resolvedManagedDependencies) {
      bomDependencyMessage.append("  ");
      bomDependencyMessage.append(resolvedManagedDependency.getArtifact());
      bomDependencyMessage.append("\n");
    }
    logger.info(bomDependencyMessage.toString());

    for (Path path : paths) {
      if (!path.getFileName().endsWith("pom.xml")) {
        continue;
      }

      modifyPomFile(path, resolvedManagedDependencies);
    }
  }

  static Element getOrCreateNode(Element parent, String name) {
    Elements targetNodes = parent.getChildElements(name, MAVEN_POM_NAMESPACE_URL);
    if (targetNodes.size() >= 1) {
      return targetNodes.get(0);
    }
    Element newNode = new Element(name, MAVEN_POM_NAMESPACE_URL);
    parent.appendChild(newNode);

    return newNode;
  }

  private static Element getOrCreateSurefirePlugin(Element pluginsNode) {
    Elements pluginElements = pluginsNode.getChildElements();
    for (Element pluginElement : pluginElements) {
      Elements artifactIdElements =
          pluginElement.getChildElements("artifactId", MAVEN_POM_NAMESPACE_URL);
      if (artifactIdElements.size() == 1
          && "maven-surefire-plugin".equals(artifactIdElements.get(0).getValue())) {
        return pluginElement;
      }
    }

    Element newPluginNode = new Element("plugin", MAVEN_POM_NAMESPACE_URL);
    Element artifactIdElement = new Element("artifactId", MAVEN_POM_NAMESPACE_URL);
    artifactIdElement.appendChild("maven-surefire-plugin");
    Element groupIdElement = new Element("groupId", MAVEN_POM_NAMESPACE_URL);
    groupIdElement.appendChild("org.apache.maven.plugins");
    newPluginNode.appendChild(groupIdElement);
    newPluginNode.appendChild(artifactIdElement);

    pluginsNode.appendChild(newPluginNode);
    return newPluginNode;
  }

  /**
   * Modifies {@code pomFile} so that Maven uses {@code managedDependencies} when running the tests.
   *
   * <p>It inserts new line characters so that the items in the list is easily visible in a vertical
   * scroll. This does not try to
   */
  private static void modifyPomFile(Path pomFile, ImmutableList<ClassPathEntry> managedDependencies)
      throws IOException, ParsingException, ArtifactResolutionException {
    Builder builder = new Builder();
    XPathContext context = new XPathContext("ns", MAVEN_POM_NAMESPACE_URL);
    Document document = builder.build(pomFile.toFile());
    Nodes project = document.query("//ns:project", context);
    if (project.size() != 1) {
      // When sample's XML declaration is missing namespace, this logic fails.
      // Example: https://github.com/googleapis/java-bigquery/pull/1199
      logger.warning("Invalid pom.xml " + pomFile + "; project element size: " + project.size());
      return;
    }

    // Look at project/build/plugins/plugin for surefire plugin
    Nodes classifierNodes =
        document.query("//ns:project/ns:dependencies/ns:dependency/ns:classifier", context);

    // Surefire configuration cannot distinguish artifacts' classifiers. Therefore we do not exclude
    // artifacts with classifiers
    Set<String> dependenciesWithClassifiers = new HashSet<>();
    for (Node classifierNode : classifierNodes) {
      String classifierValue = classifierNode.getValue();
      if (classifierValue.isEmpty()) {
        continue;
      }
      Element dependency = (Element) classifierNode.getParent();
      // A dependency element always has an artifactId and a groupId element.
      String artifactId =
          dependency.getChildElements("artifactId", MAVEN_POM_NAMESPACE_URL).get(0).getValue();
      String groupId =
          dependency.getChildElements("groupId", MAVEN_POM_NAMESPACE_URL).get(0).getValue();
      dependenciesWithClassifiers.add(groupId + ":" + artifactId);
    }

    Element projectNode = (Element) document.query("//ns:project", context).get(0);
    Element buildNode = getOrCreateNode(projectNode, "build");
    Element pluginsNode = getOrCreateNode(buildNode, "plugins");
    Element surefirePluginElement = getOrCreateSurefirePlugin(pluginsNode);

    // https://maven.apache.org/surefire/maven-surefire-plugin/examples/configuring-classpath.html

    Element surefireConfigurationElement = getOrCreateNode(surefirePluginElement, "configuration");
    Element additionalClasspathElements =
        getOrCreateNode(surefireConfigurationElement, "additionalClasspathElements");
    additionalClasspathElements.appendChild(new Text("\n"));
    for (ClassPathEntry bomManagedDependency : managedDependencies) {
      File file = bomManagedDependency.getArtifact().getFile();
      Element additionalClasspathElement =
          new Element("additionalClasspathElement", MAVEN_POM_NAMESPACE_URL);
      additionalClasspathElement.appendChild(file.getAbsolutePath());
      additionalClasspathElements.appendChild(additionalClasspathElement);
      additionalClasspathElements.appendChild(new Text("\n"));
    }

    ImmutableMap<String, String> versionlessCoordinatesToVersion =
        managedDependencies.stream()
            .map(ClassPathEntry::getArtifact)
            .collect(ImmutableMap.toImmutableMap(Artifacts::makeKey, Artifact::getVersion));

    // Google-cloud-storage depends on com.google.cloud:google-cloud-core:jar:tests:1.94.3.
    // Because com.google.cloud:google-cloud-core is excluded at runtime, we have to add it back.
    Nodes testJarTypeNodes =
        document.query("//ns:project/ns:dependencies/ns:dependency/ns:type", context);
    Set<String> dependenciesWithTarJarType = new HashSet<>();
    for (Node dependencyNodeWithType : testJarTypeNodes) {
      String typeValue = dependencyNodeWithType.getValue();
      if (!"test-jar".equals(typeValue)) {
        continue;
      }
      Element dependency = (Element) dependencyNodeWithType.getParent();
      // A dependency element always has an artifactId and a groupId element.
      String artifactId =
          dependency.getChildElements("artifactId", MAVEN_POM_NAMESPACE_URL).get(0).getValue();
      String groupId =
          dependency.getChildElements("groupId", MAVEN_POM_NAMESPACE_URL).get(0).getValue();
      dependenciesWithTarJarType.add(groupId + ":" + artifactId);
    }

    if (dependenciesWithTarJarType.contains("com.google.cloud:google-cloud-core")) {
      String googleCloudCoreVersion =
          versionlessCoordinatesToVersion.get("com.google.cloud:google-cloud-core");

      // Where is documentation that says type:test-jar becomes classifier:tests?
      Artifact artifact =
          resolveArtifact("com.google.cloud:google-cloud-core:jar:tests:" + googleCloudCoreVersion);
      Element additionalClasspathElement =
          new Element("additionalClasspathElement", MAVEN_POM_NAMESPACE_URL);
      File file = artifact.getFile();
      additionalClasspathElement.appendChild(file.getAbsolutePath());
      additionalClasspathElements.appendChild(additionalClasspathElement);
      additionalClasspathElements.appendChild(new Text("\n"));
    }

    // This unexpectedly removes dependencies even if they use classifiers. For example,
    // com.google.api.gax.grpc.testing.MockServiceHelper is in gax-grpc with testlib classifier
    // the BOM does not supply the testlib-classifier artifacts.
    Element classpathDependencyExcludes =
        getOrCreateNode(surefireConfigurationElement, "classpathDependencyExcludes");
    classpathDependencyExcludes.appendChild(new Text("\n"));
    for (ClassPathEntry bomManagedDependency : managedDependencies) {
      Artifact artifact = bomManagedDependency.getArtifact();
      String versionlessCoordinates = Artifacts.makeKey(artifact);
      if (dependenciesWithClassifiers.contains(versionlessCoordinates)) {
        // Because surefire configuration cannot handle artifacts with classifiers (such as
        // com.google.gax:gax-grpc:testlib), we cannot exclude them.
        continue;
      }
      Element classpathDependencyExclude =
          new Element("classpathDependencyExclude", MAVEN_POM_NAMESPACE_URL);
      classpathDependencyExclude.appendChild(versionlessCoordinates);
      classpathDependencyExcludes.appendChild(classpathDependencyExclude);
      classpathDependencyExcludes.appendChild(new Text("\n"));
    }

    com.google.common.io.Files.asCharSink(pomFile.toFile(), Charsets.UTF_8).write(document.toXML());
  }

  private static Artifact resolveArtifact(String coordinates) throws ArtifactResolutionException {
    RepositorySystem system = RepositoryUtility.newRepositorySystem();
    RepositorySystemSession session = RepositoryUtility.newSession(system);

    Artifact artifact = new DefaultArtifact(coordinates);
    ArtifactRequest artifactRequest = new ArtifactRequest();
    artifactRequest.addRepository(RepositoryUtility.CENTRAL);
    artifactRequest.setArtifact(artifact);
    ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);

    return artifactResult.getArtifact();
  }
}
