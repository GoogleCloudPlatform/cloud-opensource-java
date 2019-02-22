[JLBP-9] Support the minimum Java version of your consumers
-----------------------------------------------------------

Imagine that library B depends on library A and both work with Java 8.
If library A releases a new version that requires Java 11, library B cannot upgrade
to the new version of library A unless it also starts requiring Java 11. 
If library B is not able to do that—for instance because it must work in an environment
such as App Engine that does not yet support Java 11—it must keep using the older
version of library A, missing out on security and bug fixes in the newer version.

Thus, libraries should not require a new minimum Java version until
actively-maintained dependents already require that version of Java.
It is rarely possible to wait for all dependents to upgrade.
Some projects are abandoned or lightly maintained and may never upgrade.

As an alternative, to support both dependents on the old version of
Java and dependents wanting features provided by a newer version of Java, 
you can continue development in two forks, version N for the older version of
Java and version N+1 for the newer version of Java. Typically in this case,
the major version is incremented to distinguish the two versions.
The Java package and Maven coordinates should be the same in the new major version.
