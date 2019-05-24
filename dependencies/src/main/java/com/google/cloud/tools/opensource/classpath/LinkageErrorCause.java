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

import com.google.auto.value.AutoValue;

/** Key to group linkage errors by their causes. */
@AutoValue
// TODO "cause" is unclear. Is it the target or the source? I don't know.
// Rename things to remove the word "cause" and use more specific terminology.
// why do we need this class at all? It's just a wrapper around a SymbolNotResolvable.
// It has no extra information.
abstract class LinkageErrorCause {

  /** Returns the reason for the error */
  abstract ErrorType getReason();

  /** 
   * Returns the fully qualified name of the missing or inaccessible symbol.
   * This is a class name, field name, or method name. 
   */
  abstract String getSymbol();
  
  static <T extends SymbolReference> LinkageErrorCause from(
      SymbolNotResolvable<T> staticLinkageError) {
    String symbolName = symbolNameFrom(staticLinkageError);
    return new AutoValue_LinkageErrorCause(staticLinkageError.getReason(), symbolName);
  }

  private static <T extends SymbolReference> String symbolNameFrom(
      SymbolNotResolvable<T> staticLinkageError) {
    T reference = staticLinkageError.getReference();
    switch (staticLinkageError.getReason()) {
      case INACCESSIBLE_MEMBER:
      case SYMBOL_NOT_FOUND:
        String classNamePrefix = reference.getTargetClassName() + ".";
        if (reference instanceof MethodSymbolReference) {
          return classNamePrefix + ((MethodSymbolReference) reference).getMethodName();
        } else {
          // FieldSymbolReference
          return classNamePrefix + ((FieldSymbolReference) reference).getFieldName();
        }
      case CLASS_NOT_FOUND:
      case INACCESSIBLE_CLASS:
      case INCOMPATIBLE_CLASS_CHANGE:
      default:
        return reference.getTargetClassName();
    }
  }

  @Override
  public final String toString() {
    StringBuilder builder = new StringBuilder(getSymbol());
    switch (getReason()) {
      case CLASS_NOT_FOUND:
        builder.append(" is not found");
        break;
      case INACCESSIBLE_CLASS:
        builder.append(" is not accessible");
        break;
      case INCOMPATIBLE_CLASS_CHANGE:
        builder.append(" has changed incompatibly");
        break;
      case SYMBOL_NOT_FOUND:
        builder.append(" is not found");
        break;
      case INACCESSIBLE_MEMBER:
        builder.append(" is not accessible");
        break;
    }
    return builder.toString();
  }
}
