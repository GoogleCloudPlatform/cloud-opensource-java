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
import java.util.HashMap;
import java.util.Map;

public class DashboardMain {
  
  public static void main(String[] args) 
      throws IOException, TemplateException {

    generate();
  }

  public static Path generate() throws IOException, TemplateException {
    Configuration configuration = new Configuration(new Version("2.3.28"));
    configuration.setDefaultEncoding("UTF-8");
    configuration.setClassForTemplateLoading(DashboardMain.class, "/");
    Template template = configuration.getTemplate("/templates/dashboard.ftl");
    Map<String, Object> templateData = new HashMap<>();
    
    Path output = Files.createTempDirectory("dashboard");
    
    try (Writer out = new OutputStreamWriter(
        new FileOutputStream(output.resolve("dashboard.html").toFile()), StandardCharsets.UTF_8)) {
      template.process(templateData, out);
      out.flush();
    }
    
    return output;
  }
  
}
