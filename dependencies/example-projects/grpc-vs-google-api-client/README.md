# grpc 1.17.1 v.s. google-api-client 1.27.0

This project is to demonstrate `NoSuchMethodError` generated from the class path
for google-api-client and grpc-core.

The package name `io.grpc.internal` is used for convenience, because DnsNameResolver
is package private.

## How to run

Run `mvn exec:java` to reproduce the issue.

```
grpc-vs-google-api-client$ mvn compile exec:java
[INFO] Scanning for projects...
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] Building grpc-vs-google-api-client 1.0-SNAPSHOT
[INFO] ------------------------------------------------------------------------
[INFO] 
[INFO] --- exec-maven-plugin:1.6.0:java (default-cli) @ grpc-vs-google-api-client ---
[WARNING] 
java.lang.NoSuchMethodError: com.google.common.base.Verify.verify(ZLjava/lang/String;Ljava/lang/Object;)V
    at io.grpc.internal.DnsNameResolver.maybeChooseServiceConfig (DnsNameResolver.java:514)
    at io.grpc.internal.App.main (App.java:15)
    at sun.reflect.NativeMethodAccessorImpl.invoke0 (Native Method)
    at sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62)
    at sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43)
    at java.lang.reflect.Method.invoke (Method.java:498)
    at org.codehaus.mojo.exec.ExecJavaMojo$1.run (ExecJavaMojo.java:282)
    at java.lang.Thread.run (Thread.java:748)
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 0.489 s
[INFO] Finished at: 2019-01-09T16:58:10-05:00
[INFO] Final Memory: 22M/970M
[INFO] ------------------------------------------------------------------------
```


# Diagnosis

## google-api-client 1.27.0

This artifact has dependency to `com.google.guava:guava:20.0`.
This version of Guava does not have `Verify.verify` method with the signature below
([Javadoc](https://google.github.io/guava/releases/20.0/api/docs/com/google/common/base/Verify.html)).

## grpc-core 1.17.1 

This artifact has dependency to `com.google.guava:guava:26.0-android`.

`io.grpc.internal.DnsNameResolver.maybeChooseServiceConfig` uses Guava's
`Verify.verify` method with following method signature:
`void verify(boolean expression, String errorMessageTemplate, Object p1)`
([Javadoc](https://google.github.io/guava/releases/26.0-android/api/docs/com/google/common/base/Verify.html#verify-boolean-java.lang.String-java.lang.Object-)).
This method with the signature has been added since Guava version 23.1.

