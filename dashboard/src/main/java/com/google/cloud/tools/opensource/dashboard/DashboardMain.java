/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.opensource.dashboard;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.Version;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.common.base.Splitter;

public class DashboardMain {
  
  static String[] ARTIFACTS = {
    "com.google.cloud:google-cloud-datastore:1.33.0"
  };
  
  public static void main(String[] args) 
      throws IOException, TemplateException {

    generate();
  }

  public static Path generate() throws IOException, TemplateException {
    Configuration configuration = new Configuration(new Version("2.3.28"));
    configuration.setDefaultEncoding("UTF-8");
    configuration.setClassForTemplateLoading(DashboardMain.class, "/");
    
    Path relativePath = Paths.get("target", "dashboard");
    Path output = Files.createDirectories(relativePath);

    try (Writer out = new OutputStreamWriter(
        new FileOutputStream(output.resolve("dashboard.html").toFile()), StandardCharsets.UTF_8)) {
      Template dashboard = configuration.getTemplate("/templates/dashboard.ftl");
      Map<String, Object> templateData = new HashMap<>();
      templateData.put("artifacts", ARTIFACTS);

      dashboard.process(templateData, out);
      out.flush();
    }
    
    for (String coordinates : ARTIFACTS) {
      try (Writer out = new OutputStreamWriter(
          new FileOutputStream(output.resolve(coordinates + ".html").toFile()), StandardCharsets.UTF_8)) {
        Template report = configuration.getTemplate("/templates/component.ftl");
        Map<String, Object> templateData = new HashMap<>();
        
        List<String> coords = Splitter.on(":").splitToList(coordinates);
        
        templateData.put("groupId", coords.get(0));
        templateData.put("artifactId", coords.get(1));
        templateData.put("version", coords.get(2));
        report.process(templateData, out);

        out.flush();
      }
    }
    
    return output;
  }
  
}
