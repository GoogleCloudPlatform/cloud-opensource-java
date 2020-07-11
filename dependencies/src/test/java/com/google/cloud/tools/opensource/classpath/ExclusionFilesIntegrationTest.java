/*
 * Copyright 2020 Google LLC.
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

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Before;
import org.junit.Test;

public class ExclusionFilesIntegrationTest {

  Path exclusionFile;

  @Before
  public void setup() throws IOException {
    exclusionFile = Files.createTempFile("exclusion-file", ".xml");
    exclusionFile.toFile().deleteOnExit();
  }

  @Test
  public void testExclusion()
      throws IOException, RepositoryException, TransformerException, XMLStreamException,
          LinkageCheckResultException {

    Artifact artifact =
        new DefaultArtifact("org.apache.beam:beam-runners-google-cloud-dataflow-java:2.19.0");

    LinkageCheckerMain.main(
        new String[] {"-a", Artifacts.toCoordinates(artifact), "-o", exclusionFile.toString()});

    ClassPathBuilder classPathBuilder = new ClassPathBuilder();
    ClassPathResult classPathResult = classPathBuilder.resolve(ImmutableList.of(artifact), true);

    LinkageChecker linkagechecker =
        LinkageChecker.create(classPathResult.getClassPath(), ImmutableList.of(), exclusionFile);

    ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems =
        linkagechecker.findSymbolProblems();
    Truth.assertThat(symbolProblems).isEmpty();
  }
}
