# Linkage Checker Enforcer Rule Change Log

## 1.0.1
* Fixed a bug where the enforcer rule could not resolve artifacts having `${os.detect.classifier}`
  in their dependencies. The values are usually set by [os-maven-plugin](
  https://github.com/trustin/os-maven-plugin). Now the enforcer rule works without the plugin.
