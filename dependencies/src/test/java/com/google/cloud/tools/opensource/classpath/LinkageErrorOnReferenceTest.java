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

import com.google.cloud.tools.opensource.classpath.LinkageErrorOnReference.Reason;
import com.google.common.truth.Truth;
import java.net.MalformedURLException;
import java.net.URL;
import org.junit.Test;

public class LinkageErrorOnReferenceTest {

  @Test
  public void testLinkageErrorCreation() throws MalformedURLException {
    FieldSymbolReference fieldSymbolReference =
        FieldSymbolReference.builder()
            .setTargetClassName("ClassC")
            .setFieldName("fieldX")
            .setSourceClassName("ClassD")
            .build();

    LinkageErrorOnReference<FieldSymbolReference> fieldError = LinkageErrorOnReference
        .errorMissingTargetClass(fieldSymbolReference);
    Truth.assertThat(fieldError.getReference()).isEqualTo(fieldSymbolReference);

    MethodSymbolReference methodSymbolReference =
        MethodSymbolReference.builder()
            .setTargetClassName("ClassA")
            .setMethodName("methodX")
            .setDescriptor("java.lang.String")
            .setSourceClassName("ClassB")
            .build();

    URL targetClassLocation = new URL("file://foo/bar");
    LinkageErrorOnReference<MethodSymbolReference> methodError = LinkageErrorOnReference
        .errorMissingMember(methodSymbolReference, targetClassLocation);
    Truth.assertThat(methodError.getTargetClassLocation()).isEqualTo(targetClassLocation);

    ClassSymbolReference classSymbolReference =
        ClassSymbolReference.builder()
            .setTargetClassName("ClassA")
            .setSourceClassName("ClassB")
            .build();
    LinkageErrorOnReference<ClassSymbolReference> classError =
        LinkageErrorOnReference.errorInvalidModifier(classSymbolReference, targetClassLocation);
    Truth.assertThat(classError.getReason()).isEqualTo(Reason.INACCESSIBLE);
  }
}
