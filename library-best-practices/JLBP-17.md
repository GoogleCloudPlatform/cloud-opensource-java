[JLBP-17] Coordinate Major Version Adoption
-------------------------------------------

For any particular library dependency chain, the libraries involved need to
coordinate the adoption of a new major version of any particular shared library
which doesn't rename its package so that consumers can continue using all
functionality together. (This best practice doesn't apply to adoption of new
major versions where the java package was renamed, because the old and new
packages can co-exist.)

As an example of what happens when this is not followed, imagine a partial
adoption of a new major version of a leaf library. Start with the following
dependency chain (both gax-java and grpc-java depend on guava 20.0):

```
gax-java 1.29.0 ->
  grpc-java 1.13.1 ->
    guava 20.0
  guava 20.0
```

Next, guava releases a later version (26.0) which has a breaking change (a
method is deleted). If gax-java 1.29.0 and grpc-java 1.13.1 depend on the
feature that is deleted, then it would be impossible for a user to depend on the
latest version of all of these artifacts. The following dependency chain will
have static linkage conflicts (this assumes that the end user overrides
grpc-java's dependency on guava to the latest at that point in time, 26.0):

```
gax-java 1.29.0 ->
  grpc-java 1.13.1 ->
    (ERROR) guava 26.0
  (ERROR) guava 26.0
```

After grpc-java releases a new version (1.16.0) which depends on the new version
of guava, gax-java will still experience conflicts:

```
gax-java 1.29.0 ->
  grpc-java 1.16.0 ->
    guava 26.0
  (ERROR) guava 26.0
```

Finally after gax-java upgrades its dependency on Guava, there will no longer be
conflicts:

```
gax-java 1.35.0 ->
  grpc-java 1.16.0 ->
    guava 26.0
  guava 26.0
```

This state is impossible to avoid completely (particularly during the process of
rolling out such a breaking change), but the amount of time that the dependency
chain is in this state should be minimized. Each library in the chain should be
prepared for such an upgrade before it starts, and the upgrade should be
propagated up the tree in as little time as possible, so that consumers can
continue to safely use the rule of depending on the latest version of every
package in the tree.
