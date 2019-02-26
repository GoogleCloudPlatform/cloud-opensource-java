This project demonstrates a hidden incompatibility between
`com.google.api-client:google-api-client:1.27.0` and
`com.google.cloud:google-cloud-bigtable:0.81.0-alpha`.

# Diagnosis

Maven artifact `com.google.api-client:google-api-client:1.27.0` contains `GoogleApacheHttpTransport`
class.
In this project, `HelloTransport` class tries to call
`GoogleApacheHttpTransport.newTrustedTransport()`. This method returns `ApacheHttpTransport` class.
This class is available in `com.google.http-client:google-http-client:1.27.0`, which is one of the
transitive dependencies of the google-__api__-client artifact. The google-__api__-client version
1.27.0 and the google-__http__-client version 1.27.0 work fine without any problems.

However, the google-__api__-client version 1.__27__.0 and the google-__http__-client version
1.__28__.0 has a linkage error.
When the `pom.xml` uses `com.google.api-client:google-api-client:1.27.0` and
`com.google.cloud:google-cloud-bigtable:0.81.0-alpha` together in this order, Maven picks up 
`com.google.http-client:google-http-client:1.28.0` in the transitive dependencies.
This version of the google-__http__-client artifact does not have `ApacheHttpTransport` any more
([PR #543: Split http apache artifact](
https://github.com/googleapis/google-http-java-client/commit/bf4a8dad3f44772504f0223544ab7b92c9bea3be#diff-a2171533e9e559802ade0026c92d3bdf)).

Because of the missing `ApacheHttpTransport` class for the return value (not
`GoogleApacheHttpTransport`), the compilation of this project fails.

```
$ mvn clean compile
 Scanning for projects...
...
[ERROR] COMPILATION ERROR : 
 -------------------------------------------------------------
[ERROR] /usr/local/google/home/suztomo/cloud-opensource-java/example-problems/class-removed-from-google-http-client/src/main/java/com/google/cloud/tools/examples/HelloTransport.java:[31,50] cannot access com.google.api.client.http.apache.ApacheHttpTransport
  class file for com.google.api.client.http.apache.ApacheHttpTransport not found
 1 error
 -------------------------------------------------------------
 ------------------------------------------------------------------------
 BUILD FAILURE
```

# Changes in Dependency Tree

The google-api-client:1.__27__.0 artifact used `ApacheHttpTransport` through
`com.google.http-client:google-http-client:1.27.0`:

```
com.google.api-client:google-api-client:jar:1.27.0:compile
+- com.google.oauth-client:google-oauth-client:jar:1.27.0:compile
|  +- com.google.http-client:google-http-client:jar:1.27.0:compile  <-- com.google.api.client.http.apache.ApacheHttpTransport was here
|  |  +- org.apache.httpcomponents:httpclient:jar:4.5.5:compile
|  |  |  +- org.apache.httpcomponents:httpcore:jar:4.4.9:compile
|  |  |  +- commons-logging:commons-logging:jar:1.2:compile
|  |  |  \- commons-codec:commons-codec:jar:1.10:compile
|  |  \- com.google.j2objc:j2objc-annotations:jar:1.1:compile
|  +- com.google.code.findbugs:jsr305:jar:3.0.2:compile
+- com.google.http-client:google-http-client-jackson2:jar:1.27.0:compile
|  \- com.fasterxml.jackson.core:jackson-core:jar:2.9.6:compile
\- com.google.guava:guava:jar:20.0:compile
```

The google-api-client:1.__28__.0 artifact (This example project does not use this artifact) depends
on google-http-client-__apache__ artifact that has `ApacheHttpTransport` class:

```
com.google.api-client:google-api-client:jar:1.28.0:compile
+- com.google.oauth-client:google-oauth-client:jar:1.28.0:compile
|  +- com.google.http-client:google-http-client:jar:1.28.0:compile  <-- No ApacheHttpTransport any more.
|  |  +- io.opencensus:opencensus-api:jar:0.18.0:compile
|  |  |  \- io.grpc:grpc-context:jar:1.14.0:compile
|  |  \- io.opencensus:opencensus-contrib-http-util:jar:0.18.0:compile
|  +- com.google.code.findbugs:jsr305:jar:3.0.2:compile
+- com.google.http-client:google-http-client-jackson2:jar:1.28.0:compile
|  \- com.fasterxml.jackson.core:jackson-core:jar:2.9.6:compile
+- com.google.http-client:google-http-client-apache:jar:2.0.0:compile   <-- New artifact! com.google.api.client.http.apache.ApacheHttpTransport is here
|  \- org.apache.httpcomponents:httpclient:jar:4.5.5:compile
|     +- org.apache.httpcomponents:httpcore:jar:4.4.9:compile
|     +- commons-logging:commons-logging:jar:1.2:compile
|     \- commons-codec:commons-codec:jar:1.10:compile
\- com.google.guava:guava:jar:26.0-android:compile
   +- org.checkerframework:checker-compat-qual:jar:2.5.2:compile
   +- com.google.errorprone:error_prone_annotations:jar:2.1.3:compile
   +- com.google.j2objc:j2objc-annotations:jar:1.1:compile
   \- org.codehaus.mojo:animal-sniffer-annotations:jar:1.14:compile
```

Dependency tree of this project shows that Maven picks up
`com.google.http-client:google-http-client:1.28.0` and
`com.google.api-client:google-api-client:1.27.0`:

```
com.google.cloud.tools.opensource:class-removed-from-google-http-client:jar:1.0-SNAPSHOT
 +- com.google.cloud:google-cloud-bigtable:jar:0.81.0-alpha:compile
 |  +- com.google.cloud:google-cloud-core:jar:1.63.0:compile
 |  |  +- com.google.http-client:google-http-client:jar:1.28.0:compile     <-- No ApacheHttpTransport any more. 
 |  |  |  +- com.google.j2objc:j2objc-annotations:jar:1.1:compile
 |  |  |  +- io.opencensus:opencensus-api:jar:0.18.0:compile
 |  |  |  \- io.opencensus:opencensus-contrib-http-util:jar:0.18.0:compile
 |  |  +- com.google.code.findbugs:jsr305:jar:3.0.2:compile
 |  |  +- com.google.api:api-common:jar:1.7.0:compile
 |  |  +- com.google.api:gax:jar:1.40.0:compile
 |  |  |  +- org.threeten:threetenbp:jar:1.3.3:compile
 |  |  |  \- com.google.auth:google-auth-library-oauth2-http:jar:0.13.0:compile
 |  |  +- com.google.protobuf:protobuf-java-util:jar:3.6.1:compile
 |  |  |  \- com.google.code.gson:gson:jar:2.7:compile
 |  |  +- com.google.api.grpc:proto-google-common-protos:jar:1.14.0:compile
 |  |  \- com.google.api.grpc:proto-google-iam-v1:jar:0.12.0:compile
 |  +- com.google.cloud:google-cloud-core-grpc:jar:1.63.0:compile
 |  |  +- com.google.auth:google-auth-library-credentials:jar:0.13.0:compile
 |  |  +- com.google.protobuf:protobuf-java:jar:3.6.1:compile
 |  |  +- io.grpc:grpc-protobuf:jar:1.18.0:compile
 |  |  |  +- org.checkerframework:checker-compat-qual:jar:2.5.2:compile
 |  |  |  \- io.grpc:grpc-protobuf-lite:jar:1.18.0:compile
 |  |  +- io.grpc:grpc-context:jar:1.18.0:compile
 |  |  \- com.google.api:gax-grpc:jar:1.40.0:compile
 |  |     \- io.grpc:grpc-alts:jar:1.18.0:compile
 |  |        +- org.apache.commons:commons-lang3:jar:3.5:compile
 |  |        \- io.grpc:grpc-grpclb:jar:1.18.0:runtime
 |  +- com.google.api.grpc:proto-google-cloud-bigtable-v2:jar:0.46.0:compile
 |  +- com.google.api.grpc:proto-google-cloud-bigtable-admin-v2:jar:0.46.0:compile
 |  +- io.grpc:grpc-netty-shaded:jar:1.18.0:compile
 |  |  \- io.grpc:grpc-core:jar:1.18.0:compile (version selected from constraint [1.18.0,1.18.0])
 |  |     +- com.google.errorprone:error_prone_annotations:jar:2.2.0:compile
 |  |     +- org.codehaus.mojo:animal-sniffer-annotations:jar:1.17:compile
 |  |     \- io.opencensus:opencensus-contrib-grpc-metrics:jar:0.18.0:compile
 |  +- io.grpc:grpc-stub:jar:1.18.0:compile
 |  +- io.grpc:grpc-auth:jar:1.18.0:compile
 |  \- javax.annotation:javax.annotation-api:jar:1.2:compile
 \- com.google.api-client:google-api-client:jar:1.27.0:compile       <-- Uses com.google.api.client.http.apache.ApacheHttpTransport
    +- com.google.oauth-client:google-oauth-client:jar:1.27.0:compile
    +- com.google.http-client:google-http-client-jackson2:jar:1.27.0:compile
    |  \- com.fasterxml.jackson.core:jackson-core:jar:2.9.6:compile
    \- com.google.guava:guava:jar:20.0:compile
```