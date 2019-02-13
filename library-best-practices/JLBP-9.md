[JLBP-9] Support the minimum Java version of your consumers
-----------------------------------------------------------

Imagine library B depends on library A and both work with Java 8.
If library A releases a new version that requires Java 11, library B cannot upgrade
unless it also starts requiring Java 11. 
If library B is not able to do that—for instance because it must work in an environment
such as App Engine that does not yet support Java 11—it must keep using the older
version of library B, missing out on security and bug fixes in the newer version.
This can also lead to diamond dependency conflicts where no version works for
all consumers. 

Thus, libraries should wait a reasonable period of time
for actively-maintained consumers to advance their minimum version of Java before
they advance their own. It is rarely possible to wait for all dependents to upgrade.
Some projects are abandoned or lightly maintained and may never upgrade.

As an alternative, to support both consumers on the old version of
Java and consumers wanting features provided by a newer version of Java, 
you can continue development in two forks, version N for the older version of
Java and version N+1 for the newer version of Java. Typically in this case,
the major version is incremented to distinguish the two versions.
The Java package and Maven coordinates should be the same in the new major version.
