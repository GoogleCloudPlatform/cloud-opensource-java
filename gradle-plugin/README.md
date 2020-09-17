# Linkage Checker Gradle Plugin

For usage of this plugin, see the documentation.

# Build Instruction

This Gradle project depends on the `dependencies` module through the local Maven repository.

At the root of the cloud-opensource-java project, first install the Maven projects to the local
Maven repository:

   ```
   $ mvn install
   ```

Then build this Gradle project:

   ```
   $ cd gradle-plugin
   $ ./gradlew build publishToMavenLocal
   ```

This command installs the Linkage Checker Gradle plugin in the local Maven repository.

   ```
   suztomo-macbookpro44% ls ~/.m2/repository/com/google/cloud/tools/linkage-checker-gradle-plugin/0.1.0-SNAPSHOT/
   linkage-checker-gradle-plugin-0.1.0-SNAPSHOT.jar
   linkage-checker-gradle-plugin-0.1.0-SNAPSHOT.pom
   ```
    
# Debug

   ```
   ./gradlew check --stacktrace  -Dorg.gradle.debug=true --no-daemon
   ```

## Debugging Functional Tests (src/functionalTest)

To enable break points in the Groovy scripts in IntelliJ, install 'Spock Framework Enhancement'.

To enable break points in the Java code of the Gradle plugin during the functional tests, add
`-Dorg.gradle.testkit.debug=true` to the VM argument (
[Testing Build Logic with TestKit: Debugging build logic](
https://docs.gradle.org/current/userguide/test_kit.html#sub:test-kit-debug)).
When you see IntelliJ has outdated class files, run `./gradlew build publishToMavenLocal` to
reflect the latest code.
