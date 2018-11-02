[JLBP-18] Use shading sparingly and carefully
---------------------------------------------

The top answer to the question at
https://softwareengineering.stackexchange.com/questions/297276/what-is-a-shaded-java-dependency
does a good job of explaining what shading is. In short, it is a process which
relocates dependencies so that they don't clash with conflicting versions of the
same dependencies in a dependency tree, then copies them all into a single jar
with your own code.

There are a number of problems with shading:

- It can cause considerable size bloat, and if it is applied at multiple layers,
  it can have a massive accumulating effect.
- Users cannot upgrade dependencies shaded by other libraries (which means
  security fixes to a transitive dependency need to wait for the shading library
  to also roll in the security fix).
- Service files can easily be messed up. They need special effort to be merged
  because there is one per jar, always at the same path.
- Shading doesn't relocate JNI code.
- Classes loaded by reflection either can't be shaded automatically, or the
  shader will replace all strings matching the path in the bytecode, leading to
  corruption of some constants.
- It is easy to accidentally not relocate classes or other files, resulting in
  an artifact that overlaps classes and files with the original dependency
  (creating the situation described in [JLBP-5](JLBP-5.md) ).

For these reasons, shading should be used sparingly, especially for libraries
consumed by other libraries (because of the snowball effect).

One area where shading has been put to good use is in frameworks, which need to
allow for user-space code to choose their own dependencies that might not be
compatible with the framework's dependencies.

If you do shade, you will need to have intimate knowledge of how your
dependencies work, and you will probably need to read the source code. Make sure
you do all of the following:

- Add a test to make sure that for all classes and other files that are copied
  into your jar from your dependencies, they are all relocated.
  - Other files include things like log4j.properties files.
- For any transitive dependencies that are not relocated, promote them to direct
  dependencies.
- Make sure that no dependencies that appear on your own library surface are
  relocated.
- Make sure that all service files are merged correctly.
- Make sure to relocate JNI library names.
- Confirm that the licenses of the dependencies that you are shading are
  compatible with being re-released within your artifact.
