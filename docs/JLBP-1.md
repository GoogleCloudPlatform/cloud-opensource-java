# [JLBP-1] Minimize dependencies

Use the minimum number of dependencies that is reasonable.
Every dependency of a library is a liability of both
that library and its consumers.

Adding a dependency for something that is difficult and complicated may be OK,
but avoid adding a dependency just to save a few lines of code.
Remember you're also paying the cost of transitive dependencies.
Before adding a dependency, consider what that dependency depends on.

Scrutinize all dependency additions. Whenever you add a new
dependency, check the full tree of transitive dependencies that
are pulled in as a result. If a large number of transitive
dependencies are pulled in, consider a different direct dependency.
Alternatively, if the functionality you need is small, reimplement
it in your own library.

  - Maven: Run `mvn dependency:tree` (after running
    `mvn install -DskipTests` to build the library).
  - Gradle: Run `./gradlew dependencies`

Prefer JDK classes where available. For example, XOM and JDOM
are very convenient and far easier to use than DOM. However, most
uses of these libraries can be satisfied with the `org.w3c.dom`
or other packages bundled with the JDK at some cost in development
time.

For any given functionality, pick exactly one library. For example,
GSON, Jackson, and javax.json all parse JSON files. If one is already
pulled in by another dependency, use that. Otherwise choose one
and standardize on it. Do not include more than one in your dependency tree.
Do not allow different team members to choose different libraries.

If you can reasonably reimplement functionality instead of adding
another dependency, do so. For example, if the only classes you're
using from Guava are `Preconditions` and `Strings`, it's not
worth adding a dependency on Guava. You can easily reimplement
any method in those classes.  

## Minimize dependency scope

When you do add a dependency, keep it scoped as narrowly as possible.
For example, Maven libraries used only for testing such as JUnit, Mockito, and Truth
should have `test` scope:

```
  <dependency>
    <groupId>junit</groupId>
    <artifactId>junit</artifactId>
    <version>4.12</version>
    <scope>test</scope>
  </dependency>
```

In Gradle these libraries should have `testCompile` scope:

```
  dependencies {
    testCompile 'junit:junit:4.12'
  }
```

Libraries needed at compile time but not at runtime should be marked optional
in Maven. For example,

```
  <dependency>
    <groupId>com.google.auto.value</groupId>
    <artifactId>auto-value-annotations</artifactId>
    <version>1.6.6</version>
    <optional>true</optional> <!-- not needed at runtime -->
  </dependency>
```

In Gradle these libraries should have `compileOnly` scope:

```
  dependencies {
    compileOnly 'com.google.auto.value:auto-value-annotations:1.6.6'
  }
```

The `provided` scope should be used when the dependency is needed at runtime
and compile time. However the specific jar will be supplied by the environment
in which the code runs. For example, Java servlet containers such as Tomcat,
Jetty, and App Engine supply `javax.servlet:javax.servlet-api`.
