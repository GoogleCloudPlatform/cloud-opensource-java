# [JLBP-21] Separate tool classpath from product classpath

The tools we use for Java projects are more often than not themselves
written in Java. This includes build system such as Maven, javac, and Ant;
runtime environments such as Tomcat, Hadoop, Beam, and App Engine; and editors
such as jEdit, Eclipse, and IntelliJ IDEA. Tools like these should
have their own classpaths from which they load their own code.

It is critical not to mix the classpath of the tool with the classpath of the product.
Dependencies of the tool should not be dependencies of the product.
For example, there's no reason `javax.tools.JavaCompiler` should appear in the
classpath of an MP3 player simply because the product was compiled by `javac`.

Indeed `javac` does not transmit its own dependencies into the products
it builds, but not all tools are this well behaved. Maven annotation processors
such as AutoValue and Animal Sniffer are sometimes declared to be dependencies
of the product itself in the pom.xml. Since they are only needed at
compile time, they should instead be added to the [annotation processor 
path](https://maven.apache.org/plugins/maven-compiler-plugin/compile-mojo.html#annotationProcessorPaths ).

When running code from Maven, prefer `mvn exec:exec` to `mvn exec:java`.
`mvn exec:exec` uses a completely separate process to run the user's
code while `mvn exec:java` runs the user's process in the same virtual machine
Maven itself uses.

Modern application servers are reasonably good about separating the code they host
from their own. However there have been issues in the past where the boundary
was not as aggressively maintained. The jre/lib/ext directory in particular
has been a common source of hard to debug dependency issues where a different
version of a library was being loaded than the user expected. If you are
creating a product that will run user supplied code, plan to implement
completely separate classpaths for the user code and the runtime code.
