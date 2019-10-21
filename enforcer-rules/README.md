# Linkage Checker Enforcer Rule

This Maven enforcer rule verifies that the transitive dependency tree of `pom.xml` does not have
any [linkage errors](../library-best-practices/glossary.md#types-of-conflicts-and-compatibility).

This rule performs the following steps:

1. Build a dependency tree of Maven artifacts with a root node having the dependencies of the
   project

   Unlike Maven's resolution algorithm, this tree includes `optional` and `provided` dependencies
   of transitive dependencies to detect incompatibilities beyond Maven's build class path.
1. Build a class path from the dependency tree
1. Run [Linkage Checker](../dependencies) with the class path (list of JAR files) as input

   Linkage Checker performs the following steps:
   - From all of the class files in the input JAR files, it reads class, method and field
     references.
   - It verifies the references have valid referents in the input files.
     A reference is invalidated when the referenced symbol (class, field, or method) is not found or
     inaccessible from the referencing class.
   - Invalidated references are reported as linkage errors.
1. Succeed if there is no linkage error; otherwise fails

## Class path and dependencySection element

The dependencySection element determines whether the rule checks the dependencies in
the `dependencies` section or the `dependencyManagement` section.
The following values are accepted:

- `DEPENDENCIES` (default value): the rule checks the class path calculated from the project's
  `dependencies` section.
- `DEPENDENCY_MANAGEMENT`: the rule checks the class path calculated from the project's
  `dependencyManagement` section.

# Usage

Add the following plugin configuration to your `pom.xml`:

```xml
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-enforcer-plugin</artifactId>
        <version>3.0.0-M2</version>
        <dependencies>
          <dependency>
            <groupId>com.google.cloud.tools</groupId>
            <artifactId>linkage-checker-enforcer-rules</artifactId>
            <version>1.0.0</version>
          </dependency>
        </dependencies>
        <executions>
          <execution>
            <id>enforce-linkage-checker</id>
            <!-- Important! Should run after compile -->
            <phase>verify</phase>
            <goals>
              <goal>enforce</goal>
            </goals>
            <configuration>
              <rules>
                <LinkageCheckerRule
                    implementation="com.google.cloud.tools.dependencies.enforcer.LinkageCheckerRule"/>
              </rules>
            </configuration>
          </execution>
        </executions>
      </plugin>
   ...
```

For a BOM project, set `dependencySection` element to `DEPENDENCY_MANAGEMENT`.

```xml
  <LinkageCheckerRule
      implementation="com.google.cloud.tools.dependencies.enforcer.LinkageCheckerRule">
      <dependencySection>DEPENDENCY_MANAGEMENT</dependencySection>
  </LinkageCheckerRule>
```

To suppress linkage errors that are not [_reachable in the class reference graph_](
../library-best-practices/glossary.md#class-reference-graph) from the classes in the direct
dependencies of the project, set `reportOnlyReachable` element to `true`. (default: `false`).

```xml
  <LinkageCheckerRule
      implementation="com.google.cloud.tools.dependencies.enforcer.LinkageCheckerRule">
      <reportOnlyReachable>true</reportOnlyReachable>
  </LinkageCheckerRule>
```

If a violation should not fail the build, set `level` element to `WARN`:

```xml
  <LinkageCheckerRule
      implementation="com.google.cloud.tools.dependencies.enforcer.LinkageCheckerRule">
      <level>WARN</level>
  </LinkageCheckerRule>
```

## Run

Linkage Checker Enforcer Rule is bound to `verify` lifecycle. Run the enforcer rule by `mvn`
command:

```
$ mvn verify
```

### Successful Result

Successful checks should output no error.

```
[INFO] --- maven-enforcer-plugin:3.0.0-M2:enforce (enforce-linkage-checker) @ protobuf-java-util ---
[INFO] No error found
```


### Failed Result

Failed checks should output the missing classes, fields, or methods and the referencing classes.

```
[INFO] --- maven-enforcer-plugin:3.0.0-M2:enforce (enforce-linkage-checker) @ google-cloud-core-grpc ---
[ERROR] Linkage Checker rule found 21 reachable errors. Linkage error report:
Class org.eclipse.jetty.npn.NextProtoNego is not found;
  referenced by 1 class file
    io.grpc.netty.shaded.io.netty.handler.ssl.JettyNpnSslEngine (grpc-netty-shaded-1.23.0.jar)
...
```

# Debug

For developers to debug the enforcer rule implementation, set the `MAVEN_OPTS` environment variable
to wait for debuggers (`suspend=y`) before running `mvn` command.

```
$ export MAVEN_OPTS='-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005'
$ mvn verify
Listening for transport dt_socket at address: 5005
```

Then run remote debug to the port (5005) via your IDE.
