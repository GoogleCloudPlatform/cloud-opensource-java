# Declaring Dependencies on Google Cloud Java Libraries

Google maintains a number of open source Java libraries that make it
easier to use services in Google Cloud Platform (GCP). Additionally
Google maintains several foundational libraries that can be used for
very general purposes, which the GCP libraries also depend on. This
document explains how to use the `com.google.cloud:libraries-bom`
Bill of Materials and other strategies to avoid dependency conflicts in
these libraries.

These recommendations apply to the following libraries:

- [guava](https://github.com/google/guava)
- [protobuf](https://github.com/protocolbuffers/protobuf)
- [grpc-java](https://github.com/grpc/grpc-java)
- [google-http-java-client](https://github.com/googleapis/google-http-java-client)
- [BigQuery](https://github.com/googleapis/java-bigquery) 
- [Cloud AutoML](https://github.com/googleapis/java-automl)
- [Cloud Bigtable](https://github.com/googleapis/java-bigtable) 
- [Cloud Build](https://github.com/googleapis/java-cloudbuild) 
- [Cloud Datastore](https://github.com/googleapis/java-datastore)
- [Cloud Data Loss Prevention](https://github.com/googleapis/java-dlp)
- [Cloud Firestore](https://github.com/googleapis/java-firestore)
- [Cloud KMS](https://github.com/googleapis/java-kms)
- [Cloud Natural Language](https://github.com/googleapis/java-language)
- [Cloud Pub/Sub](https://github.com/googleapis/java-pubsub)
- [Cloud Scheduler](https://github.com/googleapis/java-scheduler)
- [Cloud Spanner](https://github.com/googleapis/java-spanner)
- [Cloud Speech](https://github.com/googleapis/java-speech)
- [Cloud Storage](https://github.com/googleapis/java-storage)
- [Cloud Translation](https://github.com/googleapis/java-translate)
- [Cloud Tasks](https://github.com/googleapis/java-tasks)
- [Cloud Text-to-Speech](https://github.com/googleapis/java-texttospeech)
- [Cloud Video Intelligence](https://github.com/googleapis/java-video-intelligence)
- [Cloud Vision](https://github.com/googleapis/java-vision)
- [Stackdriver Logging](https://github.com/googleapis/java-logging)
- [Stackdriver Monitoring](https://github.com/googleapis/java-monitoring)
- [Stackdriver Trace](https://github.com/googleapis/java-trace)
- [BigQuery Data Transfer](https://github.com/googleapis/java-bigquerydatatransfer)
- [BigQuery Storage](https://github.com/googleapis/java-bigquerystorage)
- [Cloud Asset](https://github.com/googleapis/java-asset)
- [Cloud Billing Budgets](https://github.com/googleapis/java-billingbudgets)
- [Cloud Container Analysis](https://github.com/googleapis/java-containeranalysis)
- [Cloud Dataproc](https://github.com/googleapis/java-dataproc)
- [Cloud Data Catalog](https://github.com/googleapis/java-datacatalog)
- [Cloud Data Labeling](https://github.com/googleapis/java-datalabeling)
- [Cloud IAM Service Account Credentials API](https://github.com/googleapis/java-iamcredentials)
- [Cloud IoT Core](https://github.com/googleapis/java-iot)
- [Cloud Memorystore for Redis](https://github.com/googleapis/java-redis)
- [Cloud OS Login](https://github.com/googleapis/java-os-login)
- [Cloud Phishing Protection](https://github.com/googleapis/java-phishingprotection)
- [Cloud Recommender](https://github.com/googleapis/java-recommender)
- [Cloud Secret Manager](https://github.com/googleapis/java-secretmanager)
- [Cloud Security Center](https://github.com/googleapis/java-securitycenter)
- [Cloud Security Scanner](https://github.com/googleapis/java-websecurityscanner)
- [Cloud Talent Solution](https://github.com/googleapis/java-talent)
- [Cloud Web Risk](https://github.com/googleapis/java-webrisk)
- [Dialogflow](https://github.com/googleapis/java-dialogflow)
- [Kubernetes Engine](https://github.com/googleapis/java-container)
- [reCAPTCHA Enterprise](https://github.com/googleapis/java-recaptchaenterprise)
- [Stackdriver Error Reporting](https://github.com/googleapis/java-errorreporting)
- [Cloud Compute](https://github.com/googleapis/java-compute)
- [Cloud DNS](https://github.com/googleapis/java-dns)
- [Cloud Logging via Logback](https://github.com/googleapis/java-logging-logback)
- [Cloud Resource Manager](https://github.com/googleapis/java-resourcemanager)
- [Cloud Storage via NIO](https://github.com/googleapis/java-storage-nio)

`com.google.cloud:libraries-bom` also covers several other libraries
that client code does not usually depend on directly. However if you do import
any of these, use  `com.google.cloud:libraries-bom` to specify their versions
too:

- [GAX Google API Extensions for Java](https://github.com/googleapis/gax-java)
- [Google Auth Library](https://github.com/googleapis/google-auth-library-java)
- [Google API Client Library for Java](https://github.com/googleapis/google-api-java-client)
- [Grafeas Artifact Metadata API](https://github.com/googleapis/java-grafeas)

## Ensuring Compatibility

To ensure that your own project uses compatible versions of these
libraries, follow the guidance below for your build system.

### Maven

#### Use com.google.cloud:libraries-bom to specify dependency versions

Conflicts can occur when multiple artifacts from a single library are
part of a dependency tree, and Maven resolves different artifacts from
that library to versions from different releases. Using a BOM fixes
this problem by dictating consistent versions for all artifacts.

BOMs are imported in the `dependencyManagement` section of the pom.xml
like this:

```
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>com.google.cloud</groupId>
        <artifactId>libraries-bom</artifactId>
        <version>4.1.0</version>
        <type>pom</type>
        <scope>import</scope>
       </dependency>
     </dependencies>
  </dependencyManagement>

  <dependencies>
    <dependency>
      <groupId>com.google.cloud</groupId>
      <artifactId>google-cloud-storage</artifactId>
    </dependency>
  </dependencies>
```

In this example, since the BOM manages library versions, the
version of the google-cloud-storage artifact is omitted.

#### Use the requireUpperBoundDeps enforcer rule

Maven's dependency mediation algorithm can select older versions
instead of newer versions, meaning new features will be missing that
other libraries depend on. The `requireUpperBoundDeps` enforcer rule
can be used to automatically discover incorrect version selection
because it fails the build when an older version is chosen instead of
a newer one.

You can add `requireUpperBoundDeps` to your build like this:

```
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-enforcer-plugin</artifactId>
        <version>3.0.0-M3</version>
        <executions>
          <execution>
            <id>enforce</id>
            <goals>
              <goal>enforce</goal>
            </goals>
            <configuration>
              <rules>
                <requireUpperBoundDeps/>
              </rules>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
```

When the newest versions are not compatible, you can use BOMs to
select compatible versions.

### Gradle

Gradle selects the highest version of any given dependency in the
dependency tree.

When a library publishes multiple artifacts, using different versions
for different artifacts may cause dependency conflicts. To fix this
problem, you can import a BOM to force consistent versions, as long as
you are using at least Gradle 4.6. To do this:

- Turn on BOM support:
  - If you are using Gradle 4.x, add 
    `enableFeaturePreview('IMPROVED_POM_SUPPORT')` to `settings.gradle`. 
   
  - If you are using Gradle 5.x or higher, BOM support is on by default.
- Add a dependency on the BOM for the library you depend on
- Remove the version from the dependency declarations of the artifacts in that library

```
dependencies {
  api platform('com.google.cloud:libraries-bom:4.1.0')
  api 'com.google.cloud:google-cloud-storage'
  api 'com.google.guava:guava'
}
```

For more details, refer to [Gradle: Importing Maven BOMs](
https://docs.gradle.org/current/userguide/platforms.html#sub:bom_import) documentation.

## Intrinsic conflicts

It is possible for GCP open source Java libraries to have *intrinsic conflicts*
that cannot be resolved by following these recommendations. There is an
ongoing effort to remove intrinsic conflicts among GCP open source
Java libraries and prevent new ones from occurring.
The [Cloud Open Source Java Dashboard](
https://storage.googleapis.com/cloud-opensource-java-dashboard/com.google.cloud/libraries-bom/snapshot/index.html)
reports the current results of compatibility checks.
As of the time of this writing, some conflicts are still in the
process of being fixed, but they should not be encountered by most
users who only use the public APIs of the libraries. If you encounter
such a conflict, please
[file an issue against cloud-opensource-java](https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/new).

## Background details about library compatibility

The following is true for the libraries in the GCP open source Java
library ecosystem:

- For each library release, the artifacts included in that release are
  compatible with each other.
- Each library publishes a BOM that defines the
  compatible versions for each release.
- Each library follows semantic versioning. This means that once a
  library reaches a 1.x version, features and APIs can be added in minor/patch
  releases but not removed within a major version.
