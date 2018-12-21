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

package com.google.cloud.tools.opensource.classpath;

import java.net.MalformedURLException;
import java.net.URL;
import org.junit.Assert;
import org.junit.Test;

public class LinkageErrorMissingFieldTest{
  @Test
  public void testCreation() throws MalformedURLException {
    FieldSymbolReference fieldSymbolReference =
        FieldSymbolReference.builder()
            .setTargetClassName("ClassC")
            .setFieldName("fieldX")
            .setSourceClassName("ClassD")
            .build();
    URL targetClassLocation = new URL("file://foo/bar");
    LinkageErrorMissingField linkageErrorMissingField =
        LinkageErrorMissingField.errorSymbolNotFound(fieldSymbolReference, targetClassLocation);
    Assert.assertEquals(fieldSymbolReference, linkageErrorMissingField.getReference());
    Assert.assertEquals(targetClassLocation, linkageErrorMissingField.getTargetClassLocation());
  }
}
