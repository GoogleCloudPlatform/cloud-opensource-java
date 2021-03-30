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

import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.MavenRepositoryException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import nu.xom.ParsingException;
import org.junit.Assume;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public class LtsBomCompatibilityTest {

  static final String INPUT_RESOURCE_NAME = "repositories.yaml";

  @Test
  public void testLibraryCompatibility()
      throws IOException, InterruptedException, ParsingException, MavenRepositoryException {
    String targetRepositoryName = System.getProperty("lts.test.repository");
    Assume.assumeNotNull(targetRepositoryName);

    Path bomFile = Paths.get("..", "boms", "cloud-lts-bom", "pom.xml");
    Bom bom = Bom.readBom(bomFile);

    // SafeConstructor parses YAML only with simple values.
    Yaml yaml = new Yaml(new SafeConstructor());

    URL resource =
        LtsCompatibilityTestRunner.class.getClassLoader().getResource(INPUT_RESOURCE_NAME);
    try (InputStream yamlInputStream = resource.openStream()) {
      Map<String, Object> input = yaml.load(yamlInputStream);
      List<Map<String, Object>> repositories =
          (List<Map<String, Object>>) input.get("repositories");

      Path testRoot = Files.createTempDirectory("lts-test");
      System.out.println("Root directory: " + testRoot);
      Path runnerLog = testRoot.resolve("runner.log");

      int i = 0;
      for (Map<String, Object> repository : repositories) {
        RepositoryTestCase testCase = RepositoryTestCase.fromMap(repository);
        if (!targetRepositoryName.equals(testCase.getName())) {
          continue;
        }

        LtsCompatibilityTestRunner runner = new LtsCompatibilityTestRunner(testCase);
        runner.run(bom, testRoot, runnerLog);
      }
    }
  }
}
