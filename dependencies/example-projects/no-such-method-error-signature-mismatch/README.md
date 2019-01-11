# NoSuchMethodError due to signature mismatch

This project demonstrates that a Maven project using google-api-client 1.17.1
and grpc-core 1.27.1 can generate a `NoSuchMethodError`.

The package name `io.grpc.internal` is used for convenience to access
package-private `DnsNameResolver` class; otherwise calling
`DnsNameResolver.maybeChooseServiceConfig` method requires setting up a [gRPC Service
Config included in a DNS record][1].

## How to run

Run `mvn exec:java` to reproduce the issue.

```
no-such-method-error-signature-mismatch$ mvn compile exec:java
[INFO] Scanning for projects...
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] Building no-such-method-error-example 1.0-SNAPSHOT
[INFO] ------------------------------------------------------------------------
[INFO] 
[INFO] --- maven-resources-plugin:2.6:resources (default-resources) @ no-such-method-error-example ---
[INFO] Using 'UTF-8' encoding to copy filtered resources.
[INFO] skip non existing resourceDirectory /usr/local/google/home/suztomo/cloud-opensource-java/dependencies/example-projects/no-such-method-error-signature-mismatch/src/main/resources
[INFO] 
[INFO] --- maven-compiler-plugin:3.6.1:compile (default-compile) @ no-such-method-error-example ---
[INFO] Changes detected - recompiling the module!
[INFO] Compiling 1 source file to /usr/local/google/home/suztomo/cloud-opensource-java/dependencies/example-projects/no-such-method-error-signature-mismatch/target/classes
[INFO] 
[INFO] --- exec-maven-plugin:1.6.0:java (default-cli) @ no-such-method-error-example ---
[WARNING] 
java.lang.NoSuchMethodError: com.google.common.base.Verify.verify(ZLjava/lang/String;Ljava/lang/Object;)V
    at io.grpc.internal.DnsNameResolver.maybeChooseServiceConfig (DnsNameResolver.java:514)
    at io.grpc.internal.App.main (App.java:31)
    at sun.reflect.NativeMethodAccessorImpl.invoke0 (Native Method)
    at sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62)
    at sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43)
    at java.lang.reflect.Method.invoke (Method.java:498)
    at org.codehaus.mojo.exec.ExecJavaMojo$1.run (ExecJavaMojo.java:282)
    at java.lang.Thread.run (Thread.java:748)
```

# Diagnosis

## google-api-client 1.27.0

This artifact depends on `com.google.guava:guava:20.0`.
[Guava's `Verify` class in this version][2] does not have
a [`void verify(boolean, String, Object)`][3] method.

When google-api-client appears first in a breadth-first traversal of the dependencies,
Maven puts Guava version 20.0 into the class path.

## grpc-core 1.17.1 

This artifact depends on `com.google.guava:guava:26.0-android`.

`DnsNameResolver.maybeChooseServiceConfig` calls [`void Verify.verify(boolean, String, Object)`][3].
This method was added to Guava in version 23.1.

[1]: https://github.com/grpc/proposal/blob/master/A2-service-configs-in-dns.md
[2]: https://google.github.io/guava/releases/20.0/api/docs/com/google/common/base/Verify.html
[3]: https://google.github.io/guava/releases/26.0-android/api/docs/com/google/common/base/Verify.html#verify-boolean-java.lang.String-java.lang.Object-
