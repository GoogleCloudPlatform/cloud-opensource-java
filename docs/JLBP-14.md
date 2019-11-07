# [JLBP-14] Specify a single, soft version of each dependency

Give the version of each dependency as a single value such as `2.3`
instead of a range such as `[2.3,2.9]`, `[2.3,)`, or even `[2.3]`.
When a pom.xml specifies a range instead of a
single version for any particular dependency, builds at different points
in time can see different versions of that dependency (as your dependency
releases new versions), which can break your product unexpectedly.

Version ranges cause builds to be non-reproducible. Builds of the
same code with the same compiler at different
times can produce different artifacts with different behavior.
Version ranges can pull in incomplete releases that break the
build. For example, a [version range in appengine-gcs-client's
dependency on google-http-java-client broke multiple customers'
builds](https://github.com/GoogleCloudPlatform/appengine-gcs-client/issues/71)
when only half the artifacts in the project were pushed one afternoon.

Version ranges can be a security hole. A project that uses a range to select
the version of a dependency picks up new versions of that dependency when
they're pushed to Maven Central. Someone with release privileges can take
advantage of this to slip malicious code into projects without proper review. A
variant of this [attack in the node.js ecosystem was used to steal
Bitcoins](https://www.theregister.co.uk/2018/11/26/npm_repo_bitcoin_stealer/).

Version ranges have another, less obvious problem. A normal soft version
requirement such as `<version>2.3</version>` says that Maven prefers
version 2.3 but will accept version 2.0 or 3.0 if other dependencies
pick that first. This flexibility is important
for large projects with many dependencies, since it's very rare for
all artifacts that depend on a particular library to agree on which version they
need.

A version range does not have this flexibility. When Maven sees
`<version>[2.3,2.9]</version>`, it accepts any version between 2.3 and 2.9 inclusive. However it does not accept 2.0 or 3.1.5.
A single version range such as `<version>[2.3]</version>` is even stricter,
requiring version 2.3 and no other. If two `dependency` elements in the
graph have non-overlapping version ranges, the build fails.

Some projects such as gRPC specify single element version ranges to 
cause the build to fail early when multiple versions are mixed. For example, 
`io.grpc:grpc-alts:1.25.0` depends on exactly `io.grpc:grpc-core:1.25.0`:

```
<dependency>
  <groupId>io.grpc</groupId>
  <artifactId>grpc-core</artifactId>
  <version>[1.25.0]</version>
</dependency>
```

`io.grpc:grpc-alts:1.25.0` and `io.grpc:grpc-alts:1.23.0` cannot appear in 
the classpath at the same time. If a dependency tree contains both
`io.grpc:grpc-alts:1.25.0` and `io.grpc:grpc-alts:1.23.0`, the build fails, and the developer must fix the conflict before they can proceed.
OpenCensus did not do this, and as a result there have been several problems
when different versions of different OpenCensus artifacts did not work with each other. This technique is more helpful for libraries that are changing 
rapidly than for more stable libraries.

Even if all hard requirements can be satisfied, version ranges override
the normal Maven algorithm for selecting among different versions of the
same artifact. They pin the dependency tree to that version or versions,
even when other artifacts in the classpath specify newer compatible versions
that include important bug and security fixes.

See https://maven.apache.org/pom.html#Dependency_Version_Requirement_Specification
for more information.
