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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Nodes;
import nu.xom.ParsingException;

import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.io.MoreFiles;

public class DashboardTest {
  
  private Path outputDirectory;
  
  @Before
  public void setUp() throws ArtifactDescriptorException, IOException, TemplateException {
    outputDirectory = DashboardMain.generate();
  }
  
  @After
  public void cleanUp() throws IOException {
    MoreFiles.deleteRecursively(outputDirectory);
  }

  @Test
  public void testMain() throws IOException, TemplateException, ArtifactDescriptorException {
    DashboardMain.main(null);
  }
  
  @Test
  public void testGenerateDashboard()
      throws IOException, TemplateException, ParsingException, ArtifactDescriptorException {
    
    Assert.assertTrue(Files.exists(outputDirectory));
    Assert.assertTrue(Files.isDirectory(outputDirectory));
    
    Path dashboardHtml = outputDirectory.resolve("dashboard.html");
    Assert.assertTrue(Files.isRegularFile(dashboardHtml));
    
    List<String> artifacts = DashboardMain.readBom();
    Assert.assertFalse(artifacts.isEmpty());
    
    Builder builder = new Builder();
    try (InputStream source = Files.newInputStream(dashboardHtml)) {
      Document document = builder.build(dashboardHtml.toFile());
      Nodes li = document.query("//li");
      Assert.assertEquals(artifacts.size(), li.size());
      for (int i = 0; i < li.size(); i++) {
        Assert.assertEquals(artifacts.get(i), li.get(i).getValue());
      }
      Nodes href = document.query("//li/a/@href");
      for (int i = 0; i < href.size(); i++) {
        String fileName = href.get(i).getValue();
        Assert.assertEquals(artifacts.get(i).replace(':', '_') + ".html", 
            URLDecoder.decode(fileName, "UTF-8"));
        Path componentReport = outputDirectory.resolve(fileName);
        Assert.assertTrue(fileName + " is missing", Files.isRegularFile(componentReport));
        try {
          Document report = builder.build(componentReport.toFile());
          Nodes updates = report.query("//li");
        } catch (ParsingException ex) {
          byte[] data = Files.readAllBytes(componentReport);
          String message = "Could not parse " + componentReport + " at line " +
            ex.getLineNumber() +", column " + ex.getColumnNumber() + "\r\n";
          message += ex.getMessage() + "\r\n";
          message += new String(data, StandardCharsets.UTF_8);
          Assert.fail(message);
        }
      }
      
      Nodes updated = document.query("//p[@id='updated']");
      Assert.assertEquals("didn't find updated" + document.toXML(), 1, updated.size());
    }
  }

}
