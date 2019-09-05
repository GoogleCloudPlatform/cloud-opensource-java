# [JLBP-1] Minimize dependencies

Use the minimum number of dependencies that is reasonable.
Every dependency of a library is a liability of both
that library and its consumers.

Adding a dependency for something that is difficult and complicated to do may be OK,
but avoid adding a dependency just to save a few lines of code.

Some specific notes about minimizing dependencies:

- Remember you're also paying the cost of transitive dependencies.
  Before adding a dependency, also consider what that dependency
  depends on.

- Use the smallest scope possible. For example, AutoValue doesn't
  need to use `compile` scope, and can instead use `compile-only`,
  since it doesn't need to appear on the classpath of consumers.
  - Libraries used only for testing should have `test` scope
    (for example junit, mockito, and truth).

- Scrutinize all dependency additions. Whenever you add a new
  dependency, check the full tree of transitive dependencies that
  are pulled in as a result. If a large number of transitive
  dependencies are pulled in, consider a different direct dependency.
  Alternatively, if the functionality you need is small, reimplement
  it in your own library.
  - Maven: Run `mvn dependency:tree` (after running
    `mvn install -DskipTests` to build the library).
  - Gradle: Run `./gradlew dependencies`

- Prefer JDK classes where available. For example, XOM and JDOM
  are very convenient and far easier to use than DOM. However, most 
  uses of these libraries can be satisfied with the `org.w3c.dom` 
  or other packages bundled with the JDK at some cost in development
  time.

- For any given functionality, pick exactly one library. For example,
  GSON, Jackson, and javax.json all parse JSON files. If one is already
  pulled in by another dependency, use that. Otherwise choose one
  and standardize on it. Do not include more than one in your dependency tree.
  Do not allow different team members to choose different libraries.

- If you can reasonably reimplement functionality instead of adding
  another dependency, do so. For example, if the only classes you're 
  using from Guava are `Preconditions` and `Strings`, it's not 
  worth adding a dependency on Guava. You can easily reimplement 
  any method in those classes.  
