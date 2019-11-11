# Linkage Checker Enforcer Rule

Linkage Checker Enforcer Rule verifies that the transitive dependency tree of `pom.xml` does not have
any [linkage errors](https://jlbp.dev/glossary.html#linkage-error).

User documentation: [Linkage Checker Enforcer Rule](
https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/Linkage-Checker-Enforcer-Rule)

# Debug

For developers to debug the enforcer rule implementation, set the `MAVEN_OPTS` environment variable
to wait for debuggers (`suspend=y`) before running `mvn` command.

```
$ export MAVEN_OPTS='-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005'
$ mvn verify
Listening for transport dt_socket at address: 5005
```

Then run remote debug to the port (5005) via your IDE.
