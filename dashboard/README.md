To generate the dashboard from the root directory run:

```
$ mvn clean install
$ mvn exec:java -Dexec.arguments="-f boms/cloud-oss-bom/pom.xml"
```
