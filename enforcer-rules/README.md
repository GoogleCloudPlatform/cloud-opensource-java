# Linkage Checker Enforcer Rule

This Maven enforcer rule runs [Linkage Checker](../dependencies) for Maven projects.
The rule verifies that the project does not have linkage errors in its class path.

## Class path and bom flag

Use `bom` flag to `true` if you use this rule for a BOM project. By default it is `false`.

- When `bom=false`, the rule checks a class path consisting of the project's `dependencies` section
  (immediate child element of `project`) and their transitive dependencies.
- When `bom=true`, the rule checks a class path consisting of artifacts in the BOM's
  `dependencyManagement` section and their transitive dependencies.

# Usage

Add the following plugin configuration to your `pom.xml`.

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
            <artifactId>linkage-checker</artifactId>
            <version>1.0-SNAPSHOT</version>
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

For a BOM project, set `bom` flag to true.

```xml
  <banLinkageErrors
      implementation="com.google.cloud.tools.opensource.enforcer.LinkageCheckerRule">
      <bom>true</bom>
  </banLinkageErrors>
```


# Run

Enforcer rules are part of `validate` lifecycle in Maven.

```
$ mvn validate
```

## Debug

For developers of this enforcer rule, set `MAVEN_OPTS` environment variable to wait for
debuggers (`suspend=y`).

```
$ export MAVEN_OPTS='-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005'
$ mvn validate
Listening for transport dt_socket at address: 5005
```

Then run remote debug to the port (5005) via your IDE.
