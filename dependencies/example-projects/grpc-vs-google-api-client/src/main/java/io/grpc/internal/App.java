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

package io.grpc.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Random;

public class App {

  public static void main(String[] args) {
    Map<String, Object> choice = ImmutableMap.of("clientLanguage", ImmutableList.of("en"));

    // Throws java.lang.NoSuchMethodError when Guava 20.0 is in the class path:
    //   com.google.common.base.Verify.verify(ZLjava/lang/String;Ljava/lang/Object;)V
    DnsNameResolver.maybeChooseServiceConfig(choice, new Random(), "localhost");
  }
}
