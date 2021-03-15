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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.common.io.MoreFiles;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.yaml.snakeyaml.Yaml;

/**
 * Runs test for each repository of the libraries in the LTS BOM
 *
 * src/resources/repositories.yaml
 */
public class LtsRuntimeCompatibilityTest {

  public static void main(String[] arguments)
      throws IOException, ArtifactDescriptorException, InterruptedException {
    String inputFileName = arguments[0];

    Yaml yaml = new Yaml();



    Path inputFile = Paths.get(inputFileName);
    Map<String, Object> input = yaml.load(new FileInputStream(inputFile.toFile()));

    String bomCoordinates = (String) input.get("bom");
    Bom bom = Bom.readBom(bomCoordinates);

    List<Map<String, Object>> repositories = (List<Map<String, Object>>) input.get("repositories");

    Path testRoot = Files.createTempDirectory("lts-test");
    System.out.println("Root directory:" + testRoot);
    // testRoot.toFile().deleteOnExit();

    for (Map<String, Object> repository: repositories) {
      String name = checkNotNull((String) repository.get("name"));
      String url = checkNotNull((String) repository.get("url"));
      String gitTag = checkNotNull((String) repository.get("tag"));

      System.out.println(name + ": " + url + " at " + gitTag);

      Process gitClone = Runtime.getRuntime()
          .exec(String.format("git clone -b %s --depth=1 %s", gitTag, url),
              null, testRoot.toFile());

      int statusCode = gitClone.waitFor();
      BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(gitClone.getErrorStream()));
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        System.out.println(line);
      }
      if (statusCode != 0) {
        System.out.println("Failed to checkout " + url +". Exiting");
        break;
      }
    }
  }


}

