package com.google.cloud.tools.opensource.classpath;

import java.util.Objects;

public class MethodAndSignature {
  private String methodName;
  private String signature;

  public MethodAndSignature(String methodName, String signature) {
    this.methodName = methodName;
    this.signature = signature;
  }

  public String getMethodName() {
    return methodName;
  }

  public String getSignature() {
    return signature;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MethodAndSignature)) {
      return false;
    }
    MethodAndSignature that = (MethodAndSignature) o;
    return Objects.equals(methodName, that.methodName) &&
        Objects.equals(signature, that.signature);
  }

  @Override
  public int hashCode() {
    return Objects.hash(methodName, signature);
  }
}
