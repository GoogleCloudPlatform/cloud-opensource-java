# Cloud Libraries BOM Release

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
