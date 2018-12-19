package com.google.cloud.tools.opensource.classpath;

import java.net.URL;
import javax.annotation.Nullable;

/**
 * Interface to provide common fields for the different types of static linkage errors.
 */
interface LinkageErrorWithReason {

  /**
   * Returns the location of the target class in the field reference; null if the target class is
   * not found in the class path.
   */
  @Nullable
  URL getTargetClassLocation();

  /**
   * Returns the reason why a symbol reference is detected as a linkage error.
   */
  Reason getReason();

  /**
   * Reason to distinguish the cause of a static linkage error
   */
  enum Reason {
    TARGET_CLASS_NOT_FOUND, INVALID_ACCESS_MODIFIER, MISSING_MEMBER
  }
}