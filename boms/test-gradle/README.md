
## original issue
run `gradle build` should succeed. 
If comment out protobufPlatform configurations in L33-37, expect `gradle build` fail with  
```
> protoc: stdout: . stderr: sample.proto:6:52: Enum type "google.api.FieldBehavior" has no value named "IDENTIFIER" for option "google.api.field_behavior".
```


## Now with separate configurations for protobuf plugin
dependencies for main project compilation is not changed, `org.apache.beam:beam-vendor-grpc-1_60_1:0.2` is available.
```
compileClasspath - Compile classpath for source set 'main'.
+--- com.google.cloud:gcp-lts-bom:7.0.1
|    +--- com.google.api.grpc:proto-google-common-protos:2.39.0 (c)
|    +--- org.apache.beam:beam-sdks-java-core:2.57.0 (c)
|    +--- com.google.protobuf:protobuf-java:3.25.5 (c)
|    \--- com.google.auto.value:auto-value-annotations:1.10.4 (c)
+--- org.apache.beam:beam-sdks-java-core -> 2.57.0
|    +--- org.apache.beam:beam-model-pipeline:2.57.0
|    |    +--- org.apache.beam:beam-vendor-grpc-1_60_1:0.2
|    |    +--- com.google.auto.value:auto-value-annotations:1.8.2 -> 1.10.4
|    |    +--- com.google.errorprone:error_prone_annotations:2.20.0
|    |    +--- org.apache.httpcomponents:httpclient:4.5.13
|    |    |    +--- org.apache.httpcomponents:httpcore:4.4.13 -> 4.4.15
|    |    |    +--- commons-logging:commons-logging:1.2
|    |    |    \--- commons-codec:commons-codec:1.11 -> 1.17.0
|    |    +--- org.apache.httpcomponents:httpcore:4.4.15
|    |    \--- org.conscrypt:conscrypt-openjdk-uber:2.5.2
|    +--- org.apache.beam:beam-model-fn-execution:2.57.0
|    |    +--- org.apache.beam:beam-vendor-grpc-1_60_1:0.2
|    |    +--- com.google.auto.value:auto-value-annotations:1.8.2 -> 1.10.4
|    |    +--- com.google.errorprone:error_prone_annotations:2.20.0
|    |    +--- org.apache.httpcomponents:httpclient:4.5.13 (*)
|    |    +--- org.apache.httpcomponents:httpcore:4.4.15
|    |    \--- org.conscrypt:conscrypt-openjdk-uber:2.5.2
|    +--- org.apache.beam:beam-model-job-management:2.57.0
|    |    +--- org.apache.beam:beam-vendor-grpc-1_60_1:0.2
|    |    +--- com.google.auto.value:auto-value-annotations:1.8.2 -> 1.10.4
|    |    +--- com.google.errorprone:error_prone_annotations:2.20.0
|    |    +--- org.apache.httpcomponents:httpclient:4.5.13 (*)
|    |    +--- org.apache.httpcomponents:httpcore:4.4.15
|    |    \--- org.conscrypt:conscrypt-openjdk-uber:2.5.2
|    +--- org.apache.beam:beam-sdks-java-transform-service-launcher:2.57.0
|    |    +--- org.checkerframework:checker-qual:3.42.0
|    |    +--- org.apache.beam:beam-vendor-guava-32_1_2-jre:0.1
|    |    +--- org.slf4j:slf4j-api:1.7.30
|    |    \--- args4j:args4j:2.33
|    +--- org.apache.beam:beam-vendor-grpc-1_60_1:0.2
|    +--- org.apache.beam:beam-vendor-guava-32_1_2-jre:0.1
|    +--- net.bytebuddy:byte-buddy:1.14.12
|    +--- org.antlr:antlr4-runtime:4.7
|    +--- org.apache.commons:commons-compress:1.26.2
|    |    +--- commons-codec:commons-codec:1.17.0
|    |    +--- commons-io:commons-io:2.16.1
|    |    \--- org.apache.commons:commons-lang3:3.14.0
|    +--- org.apache.commons:commons-lang3:3.14.0
|    +--- io.github.classgraph:classgraph:4.8.162
|    +--- com.google.code.findbugs:jsr305:3.0.2
|    +--- com.google.errorprone:error_prone_annotations:2.10.0 -> 2.20.0
|    +--- com.fasterxml.jackson.core:jackson-core:2.15.4
|    |    \--- com.fasterxml.jackson:jackson-bom:2.15.4
|    |         +--- com.fasterxml.jackson.core:jackson-annotations:2.15.4 (c)
|    |         +--- com.fasterxml.jackson.core:jackson-core:2.15.4 (c)
|    |         \--- com.fasterxml.jackson.core:jackson-databind:2.15.4 (c)
|    +--- com.fasterxml.jackson.core:jackson-annotations:2.15.4
|    |    \--- com.fasterxml.jackson:jackson-bom:2.15.4 (*)
|    +--- com.fasterxml.jackson.core:jackson-databind:2.15.4
|    |    +--- com.fasterxml.jackson.core:jackson-annotations:2.15.4 (*)
|    |    +--- com.fasterxml.jackson.core:jackson-core:2.15.4 (*)
|    |    \--- com.fasterxml.jackson:jackson-bom:2.15.4 (*)
|    +--- org.slf4j:slf4j-api:1.7.30
|    +--- org.xerial.snappy:snappy-java:1.1.10.4
|    \--- joda-time:joda-time:2.10.10
\--- com.google.api.grpc:proto-google-common-protos -> 2.39.0
     \--- com.google.protobuf:protobuf-java:3.25.3 -> 3.25.5
```

`gradle build` can successfully generate proto java classes, because
for protobuf plugin, uses these dependencies. `org.apache.beam:beam-vendor-grpc-1_60_1:0.2` is
excluded.
```
protobufPlatform
+--- com.google.cloud:gcp-lts-bom:7.0.1
|    +--- com.google.api.grpc:proto-google-common-protos:2.39.0 (c)
|    +--- org.apache.beam:beam-sdks-java-core:2.57.0 (c)
|    +--- com.google.protobuf:protobuf-java:3.25.5 (c)
|    \--- com.google.auto.value:auto-value-annotations:1.10.4 (c)
+--- org.apache.beam:beam-sdks-java-core -> 2.57.0
|    +--- org.apache.beam:beam-model-pipeline:2.57.0
|    |    +--- com.google.auto.value:auto-value-annotations:1.8.2 -> 1.10.4
|    |    +--- com.google.errorprone:error_prone_annotations:2.20.0
|    |    +--- org.apache.httpcomponents:httpclient:4.5.13
|    |    |    +--- org.apache.httpcomponents:httpcore:4.4.13 -> 4.4.15
|    |    |    +--- commons-logging:commons-logging:1.2
|    |    |    \--- commons-codec:commons-codec:1.11 -> 1.17.0
|    |    +--- org.apache.httpcomponents:httpcore:4.4.15
|    |    \--- org.conscrypt:conscrypt-openjdk-uber:2.5.2
|    +--- org.apache.beam:beam-model-fn-execution:2.57.0
|    |    +--- com.google.auto.value:auto-value-annotations:1.8.2 -> 1.10.4
|    |    +--- com.google.errorprone:error_prone_annotations:2.20.0
|    |    +--- org.apache.httpcomponents:httpclient:4.5.13 (*)
|    |    +--- org.apache.httpcomponents:httpcore:4.4.15
|    |    \--- org.conscrypt:conscrypt-openjdk-uber:2.5.2
|    +--- org.apache.beam:beam-model-job-management:2.57.0
|    |    +--- com.google.auto.value:auto-value-annotations:1.8.2 -> 1.10.4
|    |    +--- com.google.errorprone:error_prone_annotations:2.20.0
|    |    +--- org.apache.httpcomponents:httpclient:4.5.13 (*)
|    |    +--- org.apache.httpcomponents:httpcore:4.4.15
|    |    \--- org.conscrypt:conscrypt-openjdk-uber:2.5.2
|    +--- org.apache.beam:beam-sdks-java-transform-service-launcher:2.57.0
|    |    +--- org.checkerframework:checker-qual:3.42.0
|    |    +--- org.apache.beam:beam-vendor-guava-32_1_2-jre:0.1
|    |    +--- org.slf4j:slf4j-api:1.7.30
|    |    \--- args4j:args4j:2.33
|    +--- org.apache.beam:beam-vendor-guava-32_1_2-jre:0.1
|    +--- net.bytebuddy:byte-buddy:1.14.12
|    +--- org.antlr:antlr4-runtime:4.7
|    +--- org.apache.commons:commons-compress:1.26.2
|    |    +--- commons-codec:commons-codec:1.17.0
|    |    +--- commons-io:commons-io:2.16.1
|    |    \--- org.apache.commons:commons-lang3:3.14.0
|    +--- org.apache.commons:commons-lang3:3.14.0
|    +--- io.github.classgraph:classgraph:4.8.162
|    +--- com.google.code.findbugs:jsr305:3.0.2
|    +--- com.google.errorprone:error_prone_annotations:2.10.0 -> 2.20.0
|    +--- com.fasterxml.jackson.core:jackson-core:2.15.4
|    |    \--- com.fasterxml.jackson:jackson-bom:2.15.4
|    |         +--- com.fasterxml.jackson.core:jackson-annotations:2.15.4 (c)
|    |         +--- com.fasterxml.jackson.core:jackson-core:2.15.4 (c)
|    |         \--- com.fasterxml.jackson.core:jackson-databind:2.15.4 (c)
|    +--- com.fasterxml.jackson.core:jackson-annotations:2.15.4
|    |    \--- com.fasterxml.jackson:jackson-bom:2.15.4 (*)
|    +--- com.fasterxml.jackson.core:jackson-databind:2.15.4
|    |    +--- com.fasterxml.jackson.core:jackson-annotations:2.15.4 (*)
|    |    +--- com.fasterxml.jackson.core:jackson-core:2.15.4 (*)
|    |    \--- com.fasterxml.jackson:jackson-bom:2.15.4 (*)
|    +--- org.slf4j:slf4j-api:1.7.30
|    +--- org.xerial.snappy:snappy-java:1.1.10.4
|    \--- joda-time:joda-time:2.10.10
\--- com.google.api.grpc:proto-google-common-protos -> 2.39.0
     \--- com.google.protobuf:protobuf-java:3.25.3 -> 3.25.5

```