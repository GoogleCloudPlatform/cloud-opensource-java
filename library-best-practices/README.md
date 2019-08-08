Java Library Best Practices
---------------------------

The Java Library Best Practices are a set of rules that we believe will minimize
problems for consumers of interconnected Java libraries.  These practices come
from decades of aggregated experience in maintaining open source Java libraries
and are informed by many hard-learned lessons from mistakes that have been
made. We have found that following these rules will result in higher quality
Java libraries with fewer dependency conflicts and other kinds of problems. The
list is open-ended, so new ones may be added from time to time.

*Note* This list is currently in "Alpha," which means we are still figuring out
the complete set of best practices to use. Best practices here could be removed
or significantly changed before the set is classified as Beta.

- [Glossary](glossary.md): Terms used in the best practices and other places in
  cloud-opensource-java.

- [JLBP-1](JLBP-1.md): Minimize dependencies
- [JLBP-2](JLBP-2.md): Minimize API surface
- [JLBP-3](JLBP-3.md): Use semantic versioning
- [JLBP-4](JLBP-4.md): Avoid dependencies on unstable libraries and features
- [JLBP-5](JLBP-5.md): Avoid dependencies that overlap classes with other
  dependencies
- [JLBP-6](JLBP-6.md): Rename artifacts and packages together
- [JLBP-7](JLBP-7.md): Make breaking transitions easy
- [JLBP-8](JLBP-8.md): Advance widely used functionality to a stable version
- [JLBP-9](JLBP-9.md): Support the minimum Java version of your consumers
- [JLBP-10](JLBP-10.md): Maintain API stability as long as needed for consumers
- [JLBP-11](JLBP-11.md): Stay up to date with compatible dependencies
- [JLBP-12](JLBP-12.md): Make level of support and API stability clear
- [JLBP-13](JLBP-13.md): Quickly remove references to deprecated features in
   dependencies
- [JLBP-14](JLBP-14.md): Do not use version ranges
- [JLBP-15](JLBP-15.md): Produce a BOM for multi-module projects
- [JLBP-16](JLBP-16.md): Ensure upper version alignment of dependencies for
  consumers
- [JLBP-17](JLBP-17.md): Coordinate rollout of breaking changes
- [JLBP-18](JLBP-18.md): Only shade dependencies as a last resort
- [JLBP-19](JLBP-19.md): Place each package in only one module
- [JLBP-20](JLBP-20.md): Give each jar a module name
