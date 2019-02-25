/*
 * Copyright 2019 Google LLC.
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

import com.google.cloud.tools.opensource.classpath.SymbolNotResolvable.Reason;
import com.google.common.truth.Truth;
import org.junit.Test;

public class LinkageErrorCauseTest {

  @Test
  public void testCreation() {
    FieldSymbolReference fieldSymbolReference =
        FieldSymbolReference.builder()
            .setTargetClassName("ClassC")
            .setFieldName("fieldX")
            .setSourceClassName("ClassD")
            .build();

    SymbolNotResolvable<FieldSymbolReference> fieldError =
        SymbolNotResolvable.errorMissingTargetClass(fieldSymbolReference, true);

    LinkageErrorCause groupKey = LinkageErrorCause.from(fieldError);
    Truth.assertThat(groupKey.getReason()).isEqualTo(Reason.CLASS_NOT_FOUND);
    Truth.assertThat(groupKey.getSymbol()).isEqualTo("ClassC");
  }
}
