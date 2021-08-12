# Linkage Checker Gradle Plugin

For usage of this plugin, see [the plugin documentation](
https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/Linkage-Checker-with-Gradle).

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

## Using the plugin in the local Maven repository

To use the plugin that is installed in the local Maven repository, write the following code
in your `settings.gradle`.

```
buildscript{
  repositories {
    mavenLocal()

    dependencies{
      classpath 'com.google.cloud.tools:linkage-checker-gradle-plugin:1.5.10-SNAPSHOT'
    }
  }
}
```

If you do this, remove the version of the plugin in `build.gradle`; otherwise Gradle shows an error
message to do so.

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
