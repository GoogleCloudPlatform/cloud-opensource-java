# Classpath Checker Enforcer Rule

The rule runs Classpath Checker for the Maven project and shows linkage errors.

# Usage



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