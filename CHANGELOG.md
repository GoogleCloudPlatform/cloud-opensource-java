# Linkage Checker Enforcer Rule Change Log

## 1.0.1
* The enforcer rule now works with artifacts having `${os.detect.classifier}` in their dependencies.
  When such artifacts were resolved without [os-maven-plugin](https://github.com/trustin/os-maven-plugin)
  and cached in Maven, the enforcer rule could not resolve the dependency tree.
