[JLBP-2] Minimize API surface
-----------------------------

- Try not to expose types from your dependencies.
  - Your dependencies have their own rules about how often
    they do major version bumps and make breaking changes,
    and could be shorter than yours, which could cause a lot
    of problems if you want to retain a stable surface for
    longer than your dependencies. Historically this was a
    big problem for Guava, which made a major version bump
    every 6 months, although Guava no longer does this.
- Use protected packages/classes when the API is internal
  only and should not be used by consumers.
- Do not mark methods and classes public by default.
  Assume non-public until a need is known.
- Prefer fewer packages over more packages to avoid
  unnecessarily publicizing internal and implementation details,
  since any dependencies across package boundaries need to be
  public. (We may be able to revisit this when we can rely on
  Java 11 or later.) 
