# Cloud Libraries BOM Release

Run `prepare_release.sh` with `bom` argument in `boms/cloud-oss-bom` directory:

```
$ cd cloud-opensource-java/boms/cloud-oss-bom
$ git checkout master
$ git pull
$ ../../scripts/prepare_release.sh bom 1.0.0 1.0.1
```

Create a PR for the release, and get it approved.

## Rapid build

Rapid project is [cloud-java-tools-cloud-opensource-java-bom-kokoro-release](
http://rapid/cloud-java-tools-cloud-opensource-java-bom-kokoro-release).
