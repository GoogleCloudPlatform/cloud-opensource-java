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

import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Nodes;
import nu.xom.ParsingException;
import nu.xom.ValidityException;
import org.junit.Assert;
import org.junit.Test;

/**
 * M2_HOME must be set to point to a local Maven installation for this to work.
 */
public class DashboardTest {

  @Test
  public void testMain() throws IOException, TemplateException {
    DashboardMain.main(null);
  }
  
  @Test
  public void testGenerateDashboard()
      throws IOException, TemplateException, ValidityException, ParsingException {
    Path outputDirectory = DashboardMain.generate();
    Assert.assertTrue(Files.exists(outputDirectory));
    Assert.assertTrue(Files.isDirectory(outputDirectory));
    
    Path dashboardHtml = outputDirectory.resolve("dashboard.html");
    Assert.assertTrue(Files.isRegularFile(dashboardHtml));
    
    Builder builder = new Builder();
    try (InputStream source = Files.newInputStream(dashboardHtml)) {
      Document document = builder.build(dashboardHtml.toFile());
      Nodes li = document.query("//li");
      Assert.assertEquals(1, li.size());
      for (int i = 0; i < li.size(); i++) {
        Assert.assertEquals(DashboardMain.ARTIFACTS[i], li.get(i).getValue());
      }
      Nodes href = document.query("//li/a/@href");
      Assert.assertEquals(1, href.size());
      for (int i = 0; i < href.size(); i++) {
        String fileName = href.get(i).getValue();
        Assert.assertEquals(DashboardMain.ARTIFACTS[i].replace(':', '_') + ".html", 
            URLDecoder.decode(fileName, "UTF-8"));
        Path componentReport = outputDirectory.resolve(fileName);
        Assert.assertTrue(fileName + " is missing", Files.isRegularFile(componentReport));
        Document report = builder.build(componentReport.toFile());
       
      }

    }  
    
  }
}
