[JLBP-4] Avoid dependencies on unstable libraries and features
--------------------------------------------------------------

- Unstable libraries allow breaking changes to their
  public APIs within the same major version. For libraries following semantic
  versioning, this means libraries with a 0.x.y version. (See [JLBP-3](JLBP-3.md) 
  for more details on semantic versioning.)
  
- Features that are not part of the public API of a
  stable library are called *unstable*. To mark a feature as unstable and 
  not part of the public API, use an annotation such as `@Beta`. (See
  [JLBP-3](JLBP-3.md) for more details on annotating unstable features.)

- If your library depends on an unstable library or feature which
  experiences a breaking change between versions, your library is locked to
  a specific version of that dependency.

  - If you expose the unstable feature on your library's surface, then your
    library's current major version is permanently locked to the version
    you initially exposed, and you won't be able to upgrade without making a
    breaking change to your users.
  
  - If you only use the unstable feature in your implementation, then each minor
    or patch version of your library requires a very specific version of
    your dependency, and it is unsafe for your users to upgrade your
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
