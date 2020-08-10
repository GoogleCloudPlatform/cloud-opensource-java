/*
 * Copyright 2020 Google LLC.
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

package com.google.example;

class Dummy {
  private int privateField = 0;

  private int privateMethod() {
    return 1;
  }

  public static void main(String[] arguments) {
    Dummy dummy = new Dummy();
    int sum = dummy.privateField + dummy.privateMethod();
    System.out.println(sum);
  }
}
