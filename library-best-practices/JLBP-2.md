[JLBP-2] Minimize API surface
-----------------------------

- Avoid exposing types from your dependencies.

  - Method arguments and return values from public methods should be standard Java 
    types such as `java.time.LocalDate` or classes defined in the library itself,
    not third party types such as `org.joda.time.LocalDate`.

  - Your own public types should not be subclasses or implementations of types
    in third party libraries.  

  - Third party libraries may change their own API more frequently than you like
    or do so at inconvenient times. If a third party type on the surface of
    your API changes or is removed, you either have to break your own API or remain with an older,
    unsupported version of the library that is likely to cause diamond dependency
    problems. Historically this was a big problem for libraries that exposed Guava types
    such as `com.google.common.io.OutputSupplier` because Guava bumped major versions
    every 6 months.

- Use package-protected classes and methods for internal APIs that should not be used by consumers.

- Do not mark methods and classes public by default. Assume non-public until a need is known.

- Prefer fewer packages over more packages to avoid
  unnecessarily publicizing internal details,
  since any dependency across package boundaries needs to be
  public. (We may revisit this when we can rely on
  the new module system in Java 11 or later.) 
