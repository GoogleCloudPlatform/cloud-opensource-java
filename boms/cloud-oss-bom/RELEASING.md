To release a snapshot:

1. Install GPG

2. On Linux, run:

```
$ export GPG_TTY=$(tty)
$ cd cloud-opensource-java/boms/cloud-oss-bom
$ git checkout master
$ git pull
$ mvn install -Prelease -DskipTests -Dadditionalparam="-Xdoclint:none"
_Enter your GPG password when prompted_
$ mvn deploy -Prelease -DskipRemoteStaging -DskipTests -Dadditionalparam="-Xdoclint:none" -DaltStagingDirectory=/tmp/blah-deploy -Dmaven.install.skip
```

(This probably doesn't work on the Mac.)
