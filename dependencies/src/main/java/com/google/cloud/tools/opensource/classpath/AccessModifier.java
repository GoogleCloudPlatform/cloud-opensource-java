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
  publicAccess,
  privateAccess,
  protectedAccess,
  defaultAccess;

  static AccessModifier fromFlag(int modifierFlag) {
    if ((modifierFlag & Const.ACC_PUBLIC) != 0) {
      return publicAccess;
    } else if ((modifierFlag & Const.ACC_PRIVATE) != 0) {
      return privateAccess;
    } else if ((modifierFlag & Const.ACC_PROTECTED) != 0) {
      return protectedAccess;
    } else {
      return defaultAccess;
    }
  }

  String describe(String item) {
    switch (this) {
      case publicAccess:
        return item + " is public";
      case privateAccess:
        return item + " is private";
      case protectedAccess:
        return item + " is protected";
      case defaultAccess:
        return item + " has default access";
    }
    return item + " has unknown access";
  }
}
