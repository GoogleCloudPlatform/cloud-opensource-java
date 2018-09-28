[JLBP-1] Minimize dependencies
------------------------------

Use the minimum number of dependencies that is reasonable.
Adding a dependency for a large amount of functionality may be ok,
but avoid pulling in dependencies just to save a few lines of code,
because every dependency is a liability inherited by your own
dependents.

Some specific notes about minimizing dependencies:

- Use the smallest scope possible. For example, auto-value doesn't
  need to use compile scope, and can instead use compile-only,
  since it doesn't need to appear on the classpath of consumers.
  - All libraries used only for testing should have test scope
    (for example junit, mockito, and truth).

- Scrutinize all dependency additions. Check the result of
  `mvn dependency:tree` (after running `mvn install -DskipTests`
  to build the library) to see which transitive dependencies are
  added by just adding a single dependency to your own library,
  and if you require all of the transitive dependencies.

- Prefer JDK classes where available. For example, XOM and JDOM
  are very convenient and far easier to use than DOM. However, most 
  uses of these library can be satisfied with the `org.w3c.dom` 
  or other packages bundled with the JDK at some cost in development
  time.

- For any given functionality, pick exactly one library. For example,
  GSON, Jackson, and javax.json all parse JSON files. If one is already
  pulled in by another dependency, use that. Otherwise choose the one
  you prefer. Do not include more than one in your dependency tree.

- If you can reasonably reimplement functionality instead of adding
  another dependency, do so. For example, if the only classes you're 
  using from Guava are `Preconditions` and `Strings`, it's not 
  worth it. You can easily reimplement any methods in those classes
  you're using.  