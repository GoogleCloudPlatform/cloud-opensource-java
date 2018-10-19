Java Library Best Practices
---------------------------

The Java Library Best Practices are a set of rules that we believe will minimize
problems for consumers of interconnected Java libraries.  These practices come
from decades of aggregated experience in maintaining open source Java libraries
and are informed by many hard-learned lessons from mistakes that have been
made. We have found that following these rules will result in higher quality
Java libraries with fewer dependency conflicts and other kinds of problems. The
list is open-ended, so new ones may be added from time to time.

*Note* This list is currently in "Beta," which means that we don't anticipate
fundamentally altering them, but there may be tweaks and additions until it is
declared "Stable."

- [JLBP-1](JLBP-1.md): Minimize dependencies
- [JLBP-2](JLBP-2.md): Minimize API surface
- [JLBP-3](JLBP-3.md): Use Semantic versioning
- [JLBP-4](JLBP-4.md): Avoid dependencies on unstable libraries and features
- [JLBP-5](JLBP-5.md): Avoid dependencies that overlap classes with other
  dependencies
- [JLBP-6](JLBP-6.md): Package and artifact renaming rules
- [JLBP-7](JLBP-7.md): Make breaking transitions easy
- [JLBP-8](JLBP-8.md): Advance widely used functionality to a stable version
- [JLBP-9](JLBP-9.md): Support the minimum Java version of your consumers
- [JLBP-10](JLBP-10.md): Maintain API stability as long as needed for consumers
- [JLBP-11](JLBP-11.md): Stay up to date with compatible dependencies
- [JLBP-13](JLBP-13.md): Remove references to deprecated features quickly
