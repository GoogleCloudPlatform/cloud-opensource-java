

run `gradle build` should succeed. 
Comment out "exclude" line 18 in build.gradle, expect `gradle build` fail with  
```
> protoc: stdout: . stderr: sample.proto:6:52: Enum type "google.api.FieldBehavior" has no value named "IDENTIFIER" for option "google.api.field_behavior".
```