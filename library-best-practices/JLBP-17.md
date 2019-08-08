[JLBP-17] Coordinate rollout of breaking changes
------------------------------------------------

When a library introduces a breaking change, consumers
can't update to that version until all their other dependencies which also use
the library update to that version first. To enable clients to depend on the
latest versions of all their dependencies, propagate breaking changes as
quickly as possible.

Perform the rollout in this manner:

1. Decide whether to introduce the incompatibility in a single release or
   make the change in two phases.
   - The two-phase approach is described in [JLBP-7: Make breaking transitions
     easy](JLBP-7.md).
   - If the feature is used by stable code in other libraries, you must use the
     two-phase approach, except for the types of changes where this is
     impossible (see below).
   - If the code does not appear to be used in other libraries, an in-place
     breakage may be ok.
   - If the feature is used by unstable code or stable code having little usage
     itself, prefer two phases if possible, with in-place breakage only if a
     two-phase rollout is judged to be too costly.
   - Some types of changes cannot be done using a two-phase approach, for
     example, making classes or methods `final` or adding methods to interfaces
     without a default implementation. Minimize these types of changes.
2. Make sure that consuming libraries are prepared for the breakage.
   - In the case of in-place breakage, submit pull requests to the
     consuming libraries that switch from the old surface to the new surface,
     and add "[DO NOT SUBMIT]" to the title of the pull request.
     - These PRs of course will not pass CI, but the author should at
       least verify that the code builds locally before requesting a review.
     - The author should make sure that any code-level concerns of the library
       owners have been addressed before proceeding with the rollout (step 3) so
       that the rollout can proceed efficiently.
   - To use the two-phase approach, mark the undesired surface as
     `@Deprecated` and release. Make sure that all consuming libraries
     have removed their references to the deprecated functionality. This invokes
     [JLBP-13](JLBP-13.md) for consuming libraries.
3. Release the incompatible version and make sure that the version propagates up
   the dependency tree as quickly as possible. In the case of in-place breakage,
   update the provisionally-approved PRs with published dependency versions,
   verify that CI passes, remove the "[DO NOT SUBMIT]" text from the title of
   the pull request, and finally get a formal approval to merge.
