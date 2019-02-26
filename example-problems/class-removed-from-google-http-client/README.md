This project is to demonstrate a hidden incompatibility between
`com.google.api-client:google-api-client:1.27.0` and
`com.google.cloud:google-cloud-bigtable:0.81.0-alpha`.

Maven artifact `com.google.api-client:google-api-client:1.27.0` contains `GoogleApacheHttpTransport`
class.
`GoogleApacheHttpTransport.newTrustedTransport()` returns `ApacheHttpTransport` class.
This class is available `com.google.http-client:google-http-client:1.27.0`, which is one of the
transitive dependencies of the google-api-client artifact.

However, when the `pom.xml` uses `com.google.api-client:google-api-client:1.27.0` and
`com.google.cloud:google-cloud-bigtable:0.81.0-alpha` together, Maven picks up 
`com.google.http-client:google-http-client:1.28.0` in transitive dependencies.
`com.google.http-client:google-http-client:1.28.0` does not have `ApacheHttpTransport` any more.
Because of the missing class for the returned value, the compilation fails.

```
$ mvn clean compile
[INFO] Scanning for projects...
...
[ERROR] COMPILATION ERROR : 
[INFO] -------------------------------------------------------------
[ERROR] /usr/local/google/home/suztomo/cloud-opensource-java/example-problems/class-removed-from-google-http-client/src/main/java/com/google/cloud/tools/examples/HelloTransport.java:[31,50] cannot access com.google.api.client.http.apache.ApacheHttpTransport
  class file for com.google.api.client.http.apache.ApacheHttpTransport not found
[INFO] 1 error
[INFO] -------------------------------------------------------------
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
```