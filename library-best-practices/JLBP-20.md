[JLBP-20] Give each jar a module name
--------------------------------------------------

For compatibility with the Java Platform Module System (JPMS) in Java 9 and
later, every JAR you publish should have a module name, even if the library
does not itself use modules. More precisely, the JAR manifest in 
META-INF/MANIFEST.MF should have an Automatic-Module-Name field such as
this one for `com.google.http-client:google-http-client`:

```
Automatic-Module-Name: com.google.api.client
```

The module name should be globally unique and composed of 
dot-separated Java identifiers. It should usually be a reversed domain name such
as commonly found in Java package names. It often has the same name as the root
package of the JAR. For example, if a JAR contains `com.google.utilities.i18n`
and `com.google.utilities.strings` then `com.google.utilities` is a good 
choice for module name. However if there's a second artifact that contains
`com.google.utilities.math` and `com.google.utilities.stats`, you can't choose
the name `com.google.utilities` for both modules.

This is similar to the OSGI Bundle-SymbolicName and should probably have the
same value as that field.

To add an Automatic-Module-Name field to a jar using Maven configure the 
Maven jar plugin in pom.xml like so:

```xml
    <plugin>
      <artifactId>maven-jar-plugin</artifactId>
      <configuration>
        <archive>  
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
