# Release Steps

To release artifacts 'dependencies-parent', 'dependencies', and 'linkage-checker-enforcer-rules',
follow the ["Developers/Releasing" steps in go/ct4j](
https://g3doc.corp.google.com/company/teams/cloud-java/tools/developers/releasing.md?cl=head).

To distinguish the enforcer rule release and Google Cloud Libraries BOM release, the release script
takes `enforcer` as the first argument:

```
./script/prepare_release.sh enforcer 1.0.0
```

Note: Google Cloud Libraries BOM release procedure [boms/cloud-oss-bom/RELEASING.md](
boms/cloud-oss-bom/RELEASING.md) is not part of this document.

## Rapid builds

| Artifact ID | Rapid project | Dependency |
| ---------- | ------------------ | --------- |
|dependencies-parent| [cloud-java-tools-cloud-opensource-java-parent-kokoro-release](http://rapid/cloud-java-tools-cloud-opensource-java-parent-kokoro-release)||
|dependencies| [cloud-java-tools-cloud-opensource-java-dependencies-kokoro-release](http://rapid/cloud-java-tools-cloud-opensource-java-dependencies-kokoro-release)|dependencies-parent|
|linkage-checker-enforcer-rules|[cloud-java-tools-cloud-opensource-java-enforcer-rules-kokoro-release](http://rapid/cloud-java-tools-cloud-opensource-java-enforcer-rules-kokoro-release)|dependencies-parent, dependencies|

When releasing a version of `linkage-checker-enforcer-rules`, you need to release `dependencies`
and `dependencies-parent` together with the same version.
When releasing a version of `dependencies`, you need to release `dependencies-parent` together
with the same version.

The release pipelines can run concurrently. For example, you don't have to wait for
`dependencies-parent` pipeline before initiating `dependencies` pipeline. They use different
GCS buckets.
