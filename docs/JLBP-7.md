# [JLBP-7] Make breaking transitions easy

When a library makes an in-place breaking change (meaning that the Java package
is not renamed - see [JLBP-6](JLBP-6.md) for more discussion on renaming rules),
this can create a lot of work for consumers to adapt to the change. If a change
is "atomic" (meaning that the old version of something like a method is removed
at the same time the new version is added), all usages of the old method must be
converted to the new method in a single change to each consumer's codebase. If
the usage is widespread enough, and especially if usage spans packages by
multiple owners and spans multiple levels of the dependency tree, the cost of
transitioning may be too high to do at all.

Consequently, libraries should instead make two-phase breaking changes to stable
features:

1. Mark old methods/classes as `@Deprecated` at the same time as adding new
   methods/classes. This is a "stepping stone" release.
2. Delete the deprecated methods/classes. This should wait until major consumers
   have stopped using the deprecated functionality, where major consumers means
   consumers with high usage or consumers that are deep in the dependency tree.

When a breaking change is introduced as two phases, consumers can adapt to the
change in #1 incrementally. Then when they have completed the transition,
adapting to #2 is trivial - upgrading to the version with the removed methods
and classes shouldn't cause any build failures. The ideal breaking-change
release would have *only* removals of deprecated features.

The larger the number of places that use the old version's code, and the deeper
in the tree that the library is used, the longer the time needs to be between #1
and #2. Such a phase can even last years, depending on the release cadence of
all the libraries and apps using the old code.

As an example, OkHttp 1.6 was released to help users upgrade to OkHttp 2.
OkHttp 1.6 added some new 2.0 APIs which enabled users to transition large code
bases to the OkHttp 2 API (in 1.6) before flipping from OkHttp 1 to OkHttp 2.
(Source: https://medium.com/square-corner-blog/okhttp-2-0-6da3fe12c879)
