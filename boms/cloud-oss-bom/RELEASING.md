# To release a snapshot:

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
$ mvn deploy -Prelease -DskipTests -Dadditionalparam="-Xdoclint:none" -DaltStagingDirectory=/tmp/blah-deploy -Dmaven.install.skip
```

Notes:

This probably doesn't work on the Mac.

You might be able to skip the first deploy step; need to test that. 

# To release a non-snapshot:

Run [prepare_release.sh](https://g3doc.corp.google.com/company/teams/cloud-java/tools/developers/releasing.md#setup)
in `boms/cloud-oss-bom` directory:

```
$ cd cloud-opensource-java/boms/cloud-oss-bom
$ git checkout master
$ git pull
$ ../../scripts/prepare_release.sh 1.0.0 1.0.1
```

Then follow the ["Developers/Releasing" steps in go/ct4j](
https://g3doc.corp.google.com/company/teams/cloud-java/tools/developers/releasing.md?cl=head).

Rapid project is [cloud-java-tools-cloud-opensource-java-bom-kokoro-release](
http://rapid/cloud-java-tools-cloud-opensource-java-bom-kokoro-release).
