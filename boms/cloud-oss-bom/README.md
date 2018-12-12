To release a snapshot:

```
$ cd cloud-opensource-java/boms
$ mvn install -Prelease -DskipTests -Dadditionalparam="-Xdoclint:none"
$ mvn deploy -Prelease -DskipRemoteStaging -DskipTests -Dadditionalparam="-Xdoclint:none" -DaltStagingDirectory=/tmp/blah-deploy -Dmaven.install.skip
```