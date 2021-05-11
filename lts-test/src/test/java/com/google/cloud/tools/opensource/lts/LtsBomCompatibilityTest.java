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
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.junit.Assume;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * The entry point of the LTS test with modify-build-file approach. To run this test, set the system
 * property 'lts.test.repository' to one of the repository names in repositories.yaml.
 */
public class LtsBomCompatibilityTest {
  private static final Logger logger = Logger.getLogger(LtsBomCompatibilityTest.class.getName());

  static final String INPUT_RESOURCE_NAME = "repositories.yaml";

  @Test
  public void testBomTesttLibraryCompatibility() throws Exception {
    // This test case does not run in normal build
    String targetRepositoryName = System.getProperty("lts.test.repository");
    Assume.assumeNotNull(targetRepositoryName);

    Path bomFile = Paths.get("..", "boms", "cloud-lts-bom", "pom.xml");
    Bom bom = Bom.readBom(bomFile);

    // SafeConstructor parses YAML only with simple values.
    Yaml yaml = new Yaml(new SafeConstructor());

    URL resource = LtsCompatibilityChecker.class.getClassLoader().getResource(INPUT_RESOURCE_NAME);
    try (InputStream yamlInputStream = resource.openStream()) {
      Map<String, Object> input = yaml.load(yamlInputStream);
      List<Map<String, Object>> repositories =
          (List<Map<String, Object>>) input.get("repositories");

      Path testRoot = Files.createTempDirectory("lts-test");
      logger.info("Running tests under temporary directory: " + testRoot);

      for (Map<String, Object> repository : repositories) {
        RepositoryTestCase testCase = RepositoryTestCase.fromMap(repository);
        if (!targetRepositoryName.equals(testCase.getName())) {
          continue;
        }

        LtsCompatibilityChecker runner = new LtsCompatibilityChecker(testCase);
        runner.run(bom, testRoot);
      }
    }
  }
}
