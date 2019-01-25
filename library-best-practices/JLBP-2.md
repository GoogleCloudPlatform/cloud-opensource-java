[JLBP-2] Minimize API surface
-----------------------------

- Try not to expose types from your dependencies.
  - Your dependencies have their own rules about how often
    they do major version bumps and make breaking changes.
    Their frequency of such changes may be more often than yours,
    which causes problems if you want to retain a stable surface for
    longer than your dependencies. Historically this was a
    big problem for Guava, which made a major version bump
    every 6 months, although Guava no longer does this.
- Use package-protected classes and methods when the API is internal
  only and should not be used by consumers.
- Do not mark methods and classes public by default.
  Assume non-public until a need is known.
- Prefer fewer packages over more packages to avoid
  unnecessarily publicizing internal details,
  since any dependency across package boundaries need to be
  public. (We may revisit this when we can rely on
  the new module system in Java 11 or later.) 
