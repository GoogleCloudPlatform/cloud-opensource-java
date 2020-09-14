# Linkage Checker Enforcer Rule Change Log

## 1.5.2
* Linkage Checker enforcer rule works with other repositories than Maven Central.

## 1.5.1
* Fixed the NullPointerException bug that occurs when printing certain linkage errors ([#1599](
  https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/1599)).

## 1.5.0
* Linkage Checker no longer includes optional dependencies when building dependency graphs.
  This avoids constructing unexpectedly large graphs due to optional dependencies ([#1256](
  https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/1256)).
* Linkage errors now have `cause` field that can explain the dependency conflicts by analyzing
  dependency graphs.

## 1.4.3
* Made ClassPathEntry.getArtifact() public for the Linkage Checker Gradle plugin

## 1.4.2
* Fixed unnecessary graph traversal logic when building dependency graphs

## 1.4.1
* Fixed false positive linkage errors in Maven projects with WAR packaging

## 1.4.0
* Linkage Checker enforcer rule shows dependency paths to problematic Maven artifacts.
* Fixed the enforcer rule's incorrect selection of entry point JARs for reachability analysis

## 1.3.0
* LinkageCheckerMain has an option (`-o`) to output linkage errors into a file ([document](
  https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/LinkageCheckerMain#exclusion-files
  )). This feature is currently alpha; we may change the behavior/format in later releases.
* LinkageCheckerMain throws LinkageCheckResultException if it finds linkage errors.

## 1.2.1
* Linkage Checker handles class files containing methods without a body.

## 1.2.0
* Linkage Checker takes an [exclusion file](
https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/Linkage-Checker-Exclusion-File)
  to filter out linkage errors.
* Fixed LinkageCheckerMain's incorrect handling of JAR file input

## 1.1.4
* Linkage Checker resolves class paths in a more efficient manner.

## 1.1.3
* Linkage Checker now reports missing artifacts.
* Linkage Checker shows dependency paths to Maven artifacts that have linkage errors.

## 1.1.2
* Fixed Maven Central URL to use HTTPS

## 1.1.1
* The enforcer rule prints unresolved dependencies.

## 1.1.0
* The enforcer rule detects unimplemented methods in interfaces and abstract classes.
* Fixed wrong URL and SCM section in pom.xml

## 1.0.1
* The enforcer rule now interpolates the `${os.detect.classifier}` property defined by the
  [os-maven-plugin](https://github.com/trustin/os-maven-plugin).
