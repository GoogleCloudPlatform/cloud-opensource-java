[JLBP-9] Ensure consumers always use a higher or equal minimum Java version
---------------------------------------------------------------------------

If a library B requiring Java 1.7 depends on a library A requiring Java 1.7, and
library A bumps its minimum version to Java 1.8, then library B cannot pick up
the new version of library A until it also bumps its own minimum Java
version. This can result in a situation where the ecosystem is stuck on older
library versions, preventing the pickup of important things like security fixes,
and it can also lead to diamond dependency conflicts where no version works for
all consumers. Thus, libraries should make sure their actively-maintained
consumers advance their minimum version of Java before they advance it
themselves. (Waiting for every last consumer could block an upgrade permanently,
due to cases like abandoned projects.)

