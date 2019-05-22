To generate the dashboard from the root directory run:

```
$ mvn clean install
$ cd dashboard
$ mvn exec:java -Dexec.arguments="-f ../boms/cloud-oss-bom/pom.xml"
```
