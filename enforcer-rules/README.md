# Linkage Checker Enforcer Rule

This Maven enforcer rule verifies that the transitive dependency tree of `pom.xml` does not have
any [linkage error](../library-best-practices/glossary.md#types-of-conflicts-and-compatibility).

## Class path and dependencySection flag

The rule takes `dependencySection` property to control the behavior (by default `DEPENDENCIES`).

- When `DEPENDENCIES`, the rule checks the class path calculated from the project's `dependencies`
  section and their transitive dependencies.
- When `DEPENDENCY_MANAGEMENT`, the rule checks a class path consisting of artifacts in the BOM's
  `dependencyManagement` section and their transitive dependencies.

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
            <groupId>com.google.cloud.tools.opensource</groupId>
            <artifactId>linkage-checker-enforcer-rules</artifactId>
            <version>0.1-SNAPSHOT</version>
          </dependency>
        </dependencies>
        <executions>
          <execution>
            <id>enforce</id>
            <goals>
              <goal>enforce</goal>
            </goals>
            <configuration>
              <rules>
                <banLinkageErrors
                    implementation="com.google.cloud.tools.opensource.enforcer.LinkageCheckerRule"/>
              </rules>
            </configuration>
          </execution>
        </executions>
      </plugin>
   ...
```

For a BOM project, set `targetSection` property to `DEPENDENCY_MANAGEMENT`.

```xml
  <banLinkageErrors
      implementation="com.google.cloud.tools.opensource.enforcer.LinkageCheckerRule">
      <targetSection>DEPENDENCY_MANAGEMENT</targetSection>
  </banLinkageErrors>
```

When you do not want the rule to fail, set `level` to `WARN`:

```xml
  <banLinkageErrors
      implementation="com.google.cloud.tools.opensource.enforcer.LinkageCheckerRule">
      <level>WARN</level>
  </banLinkageErrors>
```

# Run

Enforcer rules are part of the `validate` lifecycle in Maven.

```
$ mvn validate
```

## Debug

For developers of this enforcer rule, set the `MAVEN_OPTS` environment variable to wait for
debuggers (`suspend=y`).

```
$ export MAVEN_OPTS='-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005'
$ mvn validate
Listening for transport dt_socket at address: 5005
```

Then run remote debug to the port (5005) via your IDE.
