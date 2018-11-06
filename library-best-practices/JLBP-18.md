[JLBP-18] Only shade dependencies as a last resort
--------------------------------------------------

Shading is a process where selected classes (generally from dependencies)
from the classpath of a library are relocated to different Java packages and
then copied into the final jar of the library. The main purpose of shading
is to avoid conflicts between the versions of dependencies used by a library
and the versions used by the consumers of that library. Most implementations
of shading do a simple search and replace of Java package strings in the
byte code of the dependency classes and the code using the dependency classes.

There are a number of problems with shading:

- It can cause considerable size bloat. If it is applied at multiple layers,
  it can have a massive accumulating effect.
- Users cannot upgrade dependencies shaded by other libraries (which means
  security fixes to a transitive dependency need to wait for the shading library
  to also roll in the security fix).
- Service entries under `META-INF/services` can easily be messed up. They need
  special effort to be merged because the entries are located in the same place
  for every jar.
- Shading doesn't relocate JNI code.
- Shaders either don't support classes loaded by reflection or they replace all
  strings matching selected packages in the bytecode, leading to the corruption
  of some constants.
- It is easy to accidentally not relocate classes or other files, resulting in
  an artifact that overlaps classes and files with the original dependency
  (creating the situation described in [JLBP-5](JLBP-5.md) ).

For these reasons, shading should be used sparingly, especially for libraries
consumed by other libraries (because of the snowball effect).

One area where shading has been put to good use is in frameworks, which need to
allow for user-space code to choose their own dependencies that might not be
compatible with the framework's dependencies.

If you do shade, you need intimate knowledge of how your dependencies work, and
you need to read the source code. Make sure you do all of the following:

- Add a test to make sure that all classes and other files copied into your jar
  from your dependencies are relocated.
  - Other files include things like log4j.properties files.
- Promote transitive dependencies that are not relocated to direct
  dependencies.
- Make sure no dependencies that appear on your own library surface are
  relocated.
- Make sure that all service files are merged correctly.
- Make sure to relocate JNI library names.
- Confirm that the licenses of the dependencies that you are shading are
  compatible with being re-released within your artifact.
