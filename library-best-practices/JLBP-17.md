[JLBP-17] Coordinate Major Version Adoption
-------------------------------------------

For any particular library dependency chain, the libraries involved need to
coordinate the adoption of new major versions of any shared dependency that has
breaking changes. This is necessary so that consumers can easily use the latest
version of all of the functionality together.  This best practice doesn't apply
to the adoption of new major versions where the package was renamed, because the
old and new packages can co-exist.

Below is an example of what happens when a new major version of a leaf library
is partially adopted. Suppose gax-java and grpc-java depend on guava 20.0:

```
gax-java 1.29.0 ->
  grpc-java 1.13.1 ->
    guava 20.0
  guava 20.0
```

Suppose Guava version 26.0 deletes a public method and it is now the latest
version. If gax-java 1.29.0 and grpc-java 1.13.1 invoke that method, it is
impossible for a user to depend on the latest version of all three artifacts
without static linkage conflicts. The following dependency chain demonstrates
this. This assumes that the consumer selects the latest version of guava at
that point in time, 26.0, which overrides the version that gax-java and
grpc-java select:

```
gax-java 1.29.0 ->
  grpc-java 1.13.1 ->
    (ERROR) guava 26.0
  (ERROR) guava 26.0
```

After grpc-java releases a new version (1.16.0) which depends on the new version
of guava, gax-java still experiences conflicts:

```
gax-java 1.29.0 ->
  grpc-java 1.16.0 ->
    guava 26.0
  (ERROR) guava 26.0
```

Only when gax-java and grpc-java both upgrade their dependency on Guava are
there no longer any conflicts:

```
gax-java 1.35.0 ->
  grpc-java 1.16.0 ->
    guava 26.0
  guava 26.0
```

This state is impossible to avoid completely (particularly during the process of
rolling out such a breaking change), but the amount of time that the dependency
chain is in this state should be minimized. Each library in the chain should be
prepared for such an upgrade before it starts. The upgrade should be propagated
up the tree in as little time as possible, so that consumers can continue to
safely depend on the latest version of every package.
