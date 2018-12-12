To release a snapshot:

```
$ mvn install -Prelease -DskipTests -Dadditionalparam="-Xdoclint:none"
$ mvn deploy -Prelease -DskipRemoteStaging -DskipTests -Dadditionalparam="-Xdoclint:none" -DaltStagingDirectory=/tmp/blah-deploy -Dmaven.install.skip
```