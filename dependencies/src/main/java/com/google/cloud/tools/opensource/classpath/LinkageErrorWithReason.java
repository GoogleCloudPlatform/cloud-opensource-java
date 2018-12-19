package com.google.cloud.tools.opensource.classpath;

import java.net.URL;
import javax.annotation.Nullable;

/**
 * Interface to provide common fields for different types of static linkage errors.
 */
interface LinkageErrorWithReason {

  /**
   * Returns the location of the target class in a symbol reference; null if the target class is
   * not found in the class path or the source location is unavailable.
   */
  @Nullable
  URL getTargetClassLocation();

  /**
   * Returns the reason why a symbol reference is marked as a linkage error.
   */
  Reason getReason();

  /**
   * Reason to distinguish the cause of a static linkage error against a symbol reference.
   */
  enum Reason {
    /**
     * The target class of the symbol reference is not found in the class path.
     */
    TARGET_CLASS_NOT_FOUND,

    /**
     * The access modifier (e.g., public or protected) does not allow the source of the symbol
     * reference to use the target symbol.
     */
    INVALID_ACCESS_MODIFIER,

    /**
     * For a method or field reference, the member is not found in the target class in the class
     * path.
     */
    MISSING_MEMBER
  }
}