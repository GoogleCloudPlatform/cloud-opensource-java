[JLBP-19] Give each jar a module name
--------------------------------------------------

For compatibility with the Java Platform Module System (JPMS) in Java 9 and
later, every JAR you publish should have a module name, even if the library
does not itself use modules. More precisely, the JAR manifest in 
META-INF/MANIFEST.MF should have an Automatic-Module-Name field such as
this one for `com.google.http-client:google-http-client`:

```
Automatic-Module-Name: com.google.api.client
```

The module name should usually be a reversed domain name such as commonly
found in Java package names. It often has the same name as one of the packages
in the JAR, though of course JARs frequently contain more than one package.

To add an Automatic-Module-Name field to a jar using Maven configure the 
Maven jar plugin in pom.xml like so:

```xml
    <plugin>
      <artifactId>maven-jar-plugin</artifactId>
      <configuration>
      <archive>  
        <manifestFile>${project.build.outputDirectory}/META-INF/MANIFEST.MF</manifestFile>
        <manifestEntries>
          <Automatic-Module-Name>com.google.api.client</Automatic-Module-Name>
        </manifestEntries>
      </archive> 
    </configuration>
    </plugin> 
```

To add an Automatic-Module-Name field using Gradle, add the following to
build.gradle:

```
ext.moduleName = "com.google.api.client"

jar {
    inputs.property("moduleName", moduleName)

    manifest {
       attributes  'Automatic-Module-Name': moduleName
   }
}
```
