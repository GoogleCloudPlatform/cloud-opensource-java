# Classpath Checker Enforcer Rule

The Maven enforcer rule runs Classpath Checker for the Maven project and shows linkage errors.

# Usage

Add following plugin configuration to your `pom.xml`.

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
            <artifactId>classpath-checker</artifactId>
            <version>0.0.1-SNAPSHOT</version>
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
                <classpathCheckerRule implementation="com.google.cloud.tools.opensource.enforcer.ClasspathCheckerRule">
                  <shouldIfail>false</shouldIfail><!-- This is from tutorial -->
                </classpathCheckerRule>
              </rules>
            </configuration>
          </execution>
        </executions>
      </plugin>
   ...
```

# Run

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
