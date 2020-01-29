# Linkage Checker Enforcer Rule Change Log

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
