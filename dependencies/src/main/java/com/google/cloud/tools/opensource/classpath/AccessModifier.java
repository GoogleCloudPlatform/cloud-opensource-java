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

package com.google.cloud.tools.opensource.classpath;

import org.apache.bcel.Const;

/** A modifier that controls access to classes, fields, or methods. */
enum AccessModifier {
  PUBLIC(" is public"),
  PRIVATE(" is private"),
  PROTECTED(" is private"),
  DEFAULT(" has default access");

  private String description;

  AccessModifier(String description) {
    this.description = description;
  }

  static AccessModifier fromFlag(int modifierFlag) {
    if ((modifierFlag & Const.ACC_PUBLIC) != 0) {
      return PUBLIC;
    } else if ((modifierFlag & Const.ACC_PRIVATE) != 0) {
      return PRIVATE;
    } else if ((modifierFlag & Const.ACC_PROTECTED) != 0) {
      return PROTECTED;
    } else {
      return DEFAULT;
    }
  }

  String describe(String item) {
    return item + description;
  }
}
