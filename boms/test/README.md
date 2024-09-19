
This is a test aiming to reproduce an issue with dependency conflict with a  "google/api/field_behavior.proto" file.

pom included dependencies of question:
proto-google-common-protos and beam-vendor-grpc-1_60_1:
```
[INFO] --- dependency:3.7.0:tree (default-cli) @ test ---
[INFO] com.google.cloud.tools.opensource:test:jar:1.0-SNAPSHOT
[INFO] \- org.apache.beam:beam-sdks-java-core:jar:2.57.0:compile
[INFO]    \- org.apache.beam:beam-vendor-grpc-1_60_1:jar:0.2:compile
```

```
[INFO] com.google.cloud.tools.opensource:test:jar:1.0-SNAPSHOT
[INFO] \- com.google.api.grpc:proto-google-common-protos:jar:2.39.0:compile
```

Use the mvn clean compile command to trigger the Protocol Buffer compilation. This will generate Java source files in the target/generated-sources/protobuf directory.