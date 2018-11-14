[JLBP-17] Coordinate Major Version Adoption
-------------------------------------------

It is clearly undesirable for a dependency tree to contain conflicts.
Unfortunately, as soon as any library in a dependency tree introduces a breaking
change, consumers can't use the latest versions of those libraries in the
dependency tree together. To enable clients to depend on the latest versions of
their dependencies, breaking changes should be propagated as quickly as
 possible.

Perform the rollout in this manner:

1. Decide whether to break the feature as an atomic change or to perform the
   change in two passes, deprecate then delete.
   - If the feature is used by stable code in other libraries, you must use two
     passes (as per [JLBP-7](JLBP-7.md)).
   - If the code does not appear to be used in other libraries, an in-place
     breakage may be ok.
   - If the feature is used by unstable code or stable code having little usage
     itself, prefer two passes if possible, with in-place breakage only if two
     passes are judged to be too costly.
2. Make sure that consuming libraries are prepared for the breakage.
   - In the case of in-place breakage, have pull requests (PRs) submitted to the
     consuming libraries that switch from the old surface to the new surface
     (marked as DO NOT SUBMIT).
     These PRs of course will not pass CI, but the author should at
     least verify that the code builds locally before requesting a review.
     The author should make sure that any code-level concerns of the library
     owners have been addressed before proceeding with the rollout (step 3) so
     that the rollout can proceed efficiently.
   - In the case of a two-pass breakage, mark the undesired surface as
     `@Deprecated` and release. Make sure that all consuming libraries
     have removed their references to the deprecated functionality. This invokes
     [JLBP-13](JLBP-13.md) for consuming libraries.
3. Release the incompatible version and make sure that the version propagates up
   the dependency tree as quickly as possible. In the case of in-place breakage,
   update the provisionally-approved PRs with published dependency versions,
   verify that CI passes, and finally get a formal approval to merge.
