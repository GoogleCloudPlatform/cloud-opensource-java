[JLBP-4] Avoid dependencies on unstable libraries and features
--------------------------------------------------------------

- Unstable libraries are libraries that allow breaking changes to their
  public API within the same major version. For libraries following semantic
  versioning, this means libraries with a 0.x.y version. (See [JLBP-3](JLBP-3.md) 
  for more details on the recommendations for semantic versioning.)
- Unstable features are not part of the public API of a
  stable library. They are often marked with annotations like `@Beta`. (See
  [JLBP-3](JLBP-3.md) for more details on the recommendations for annotating
  unstable features.)
- If your library depends on an unstable library or feature, and that feature
  experiences a breaking change between versions, your library will be locked to
  a specific version of that dependency.
  - If you expose the unstable feature on your library's surface, then your
    library's current major version will be permanently locked to the version
    you initially exposed, and you won't be able to upgrade without making a
    breaking change to your users.
  - If you only use the unstable feature in your implementation, then each minor
    or patch version of your library will require a very specific version of
    your dependency, and it will be unsafe for your users to upgrade your
    library on its own, creating opportunities for hard-to-diagnose runtime
    conflicts for users.
- Given the consequences of depending on unstable features in dependencies,
  avoid doing so.
  - Depending on unstable features between submodules of a single library is
    acceptable, provided that users can easily force their build system to use
    compatible versions of the submodules. Some strategies for library owners
    include:
    - Using the same version number for all submodules in a library
    - Providing a [BOM](http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Importing_Dependencies)
