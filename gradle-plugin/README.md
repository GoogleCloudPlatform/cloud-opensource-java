# Linkage Checker Gradle Plugin

For usage of this plugin, see the documentation.

# Build Instruction

```
$ cd gradle-plugin
$ ./gradlew build publishToMavenLocal
```

This command installs the Linkage Checker Gradle plugin in Maven local repository.

```
suztomo-macbookpro44% ls ~/.m2/repository/com/google/cloud/tools/linkage-checker-gradle-plugin/0.1.0-SNAPSHOT/
linkage-checker-gradle-plugin-0.1.0-SNAPSHOT.jar
linkage-checker-gradle-plugin-0.1.0-SNAPSHOT.pom
```

# Debug

```
./gradlew check --stacktrace  -Dorg.gradle.debug=true --no-daemon
```

## Debugging Tests
For IntelliJ, install Spock Framework Enhancement to add breakpoints in the Groovy scripts.

To enable break points in Java code during the functional tests, add
`-Dorg.gradle.testkit.debug=true` to the VM argument.
