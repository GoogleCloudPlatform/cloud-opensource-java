To release a snapshot:

1. Install GPG

```
$ export GPG_TTY=$(tty)
$ cd cloud-opensource-java/boms
$ mvn install -Prelease -DskipTests -Dadditionalparam="-Xdoclint:none"
$ mvn deploy -Prelease -DskipRemoteStaging -DskipTests -Dadditionalparam="-Xdoclint:none" -DaltStagingDirectory=/tmp/blah-deploy -Dmaven.install.skip
```
