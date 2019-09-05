# [JLBP-10] Maintain API stability as long as needed for consumers

Every breaking change that consumers must incorporate into their own code incurs
a cost to them. There is the immediate cost of the consumers who are forced to
change their own code to adapt to the newer version, but the far higher cost is
the inconsistency introduced into the ecosystem and the resulting potential for
diamond dependency conflicts. The deeper a library is in the dependency tree,
the higher the inconsistency cost, regardless of the magnitude of the direct
change cost.

Consequently, a library should try to maintain API stability as long as
possible.  It's hard to recommend a frequency for all cases because of the
widely varying cost. On one end of the spectrum are pre-1.0 libraries which
don't promise stability, and which can have breaking changes between every
release. On the other end are Java standard libraries which all libraries depend
on, most of whose classes have not had any breaking changes since they were
introduced. As an anchoring point, no more than once every five years might be a
good default for any library used by other open source libraries. In the end,
the decision depends a lot on the needs of the consumers.

One thing to keep in mind is that many consumers are highly resistant to
accepting breaking changes for various reasons:

- They might have low test coverage, making it costly to verify correctness.

- They might not have enough developers and time to do dependency upgrade work.

- They might have a high risk aversion.

- They might have put your major version on their own surface and they don't
  want to force a breaking change on their own users for a long period.

As soon as you introduce breaking changes, they might decide to stick with the
last working version indefinitely.
For example, Hadoop added a dependency on Guava 11.0.2 in May of 2012,
and didn't upgrade it until March of 2017 (nearly five years later).

Changes to your API surface that customers don't have to incorporate into their
own code are a different story. One example is using a different Java package
and Maven ID when releasing a new major version. However, there is a caveat:
this only applies as long as the old major version is still maintained. As soon
as the old major version is no longer supported, customers who want continued
support will have to incur breaking changes so that they can depend on something
that does have support. In this case, the transition can be seen as a "virtual
breaking change." The same rules apply here: maintain stability as long as
needed by the consumers.
