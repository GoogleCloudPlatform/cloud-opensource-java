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

import static com.google.cloud.tools.opensource.classpath.TestHelper.classPathEntryOfResource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.stream.XMLStreamException;
import org.iso_relax.verifier.VerifierConfigurationException;
import org.junit.Test;
import org.xml.sax.SAXException;

public class ExclusionFileWriterTest {
  @Test
  public void testExclusionFileCreation()
      throws IOException, URISyntaxException, XMLStreamException, VerifierConfigurationException, SAXException {

    Path output = Files.createTempFile("output", ".xml");

    ClassPathEntry grpcFirestore = classPathEntryOfResource("testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar");
    LinkageChecker linkageChecker = LinkageChecker.create(ImmutableList.of(grpcFirestore));
    ImmutableSetMultimap<SymbolProblem, ClassFile> linkageErrors = linkageChecker
        .findSymbolProblems();
    ExclusionFileWriter.write(output, linkageErrors);

    System.out.println("Output" + output);

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFileParser.parse(output);
    Truth.assertThat(matchers).hasSize(10);

  }

}
