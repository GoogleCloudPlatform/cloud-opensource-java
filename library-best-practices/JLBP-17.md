[JLBP-17] Coordinate Major Version Adoption
-------------------------------------------

It is clearly undesirable for any particular library dependency tree to contain
conflicts. Unfortunately, for any consumer of such a dependency tree that tries
to use the highest versions available of all libraries contained in it, there
are conflicts as soon as a breaking change is introduced. Thus, breaking changes
need to be propagated as quickly as possible so that such consumers can continue
using the highest versions.

Perform the rollout in this manner:

1. Decide whether to break the feature as an atomic change or to perform the
   change in two passes (deprecate then delete).
   - If the feature is used by stable code in other libraries, you must use two
     passes (as per [JLBP-7](JLBP-7.md)).
   - If there does not appear to be usage of the code in other libraries, an
     in-place breakage may be ok.
   - If the feature is used by unstable code or stable code having little usage
     itself, prefer to use two passes if possible, with in-place breakage only
     if two passes is judged to be too costly.
2. Make sure that consuming libraries are prepared for the breakage.
   - In the case of in-place breakage, have a PR prepared and approved in the
     consuming libraries that switches from the old surface to the new surface.
   - In the case of a two-pass breakage, mark the undesired surface as
     `@Deprecated` and perform a release. Make sure that all consuming libraries
     have removed their references to the deprecated functionality. This invokes
     [JLBP-13](JLBP-13.md) for consuming libraries.
3. Release the breakage and make sure that the version propagates up the
   dependency tree as quickly as possible.
