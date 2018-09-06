[JLBP-1] Minimize dependencies
------------------------------

Use the minimum number of dependencies that is reasonable.
Adding a dependency for a large amount of functionality may be ok,
but avoid pulling in dependencies just to save a few lines of code,
because every dependency is a liability.

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
