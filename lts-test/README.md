This tool is an automated test to check the compatibility of the libraries in
`boms/cloud-lts-bom/pom.xml`.

# How to execute tests in a repository

When you want to execute the unit tests, use `lts.test.repository` property.
The value corresponds to the "name" field in `lts-test/src/test/resources/repositories.yaml`.
For example, to run the tests for beam repository, run the following command:

```
$ mvn -pl dependencies,lts-test test -Dlts.test.repository=beam \
  -Dtest=com.google.cloud.tools.opensource.lts.LtsBomCompatibilityTest \
  -DfailIfNoTests=false
```
