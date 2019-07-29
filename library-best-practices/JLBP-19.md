[JLBP-19] Place each package in a single jar
--------------------------------------------------

Each Java package such as `com.google.i18n` should
appear in a single jar file in the classpath.
In Java 9 and later splitting the classes in a package across
more than one jar file can be a compile time error.
In OSGI environments such as Eclipse plugins, split packages
can lead to nondeterministic behavior.

If classes from the same package appear in Maven artifacts
with different group IDs or different artifact IDs,
it is easy to construct a classpath that
splits packages. This is a problem even if individual classes
do not overlap. For example, the google-cloud-vision Java API client
places compiled protobufs in `com.google.api.grpc:proto-google-cloud-vision-v1`
and hand written code in `com.google.api.grpc:google-cloud-vision-v1`.
Since both artifacts contain classes in the `com.google.cloud.vision.v1` package,
these two artifacts cannot be used together after Java 8.

All classes from a single package, whatever its sources, should
be published with a single Maven group ID and artifact ID.
Do not publish different classes in the same package in separate
Maven artifacts with different group IDs or artifact IDs.

For example, if the class `com.google.i18n.Localization` is in the artifact
`com.google.i18n:i18n-utilities`, then `com.google.i18n.Strings`
and `com.google.i18n.Characters` must also be in the artifact
`com.google.i18n:i18n-utilities`.

It is acceptable for a package and its subpackage to have different Maven coordinates.
For example, `com.google.i18n.Localization` can be in the artifact
`com.google.i18n:i18n-utilities` while `com.google.i18n.charactersets.Latin1` is in
`com.google.foo:i18n-charactersets`.

It is also acceptable to publish classes from both `com.google.i18n` and
`com.google.i18n.charactersets` under the same group ID and artifact ID.
Similarly, it is fine to bundle completely different packages such as
`com.google.foo` and `org.example.bar` in the same jar with the same group ID and artifact ID.
However, once any class from a package has been published under a certain
group ID and artifact ID, no other class in that package should ever use
a different group ID or artifact ID.

Finally, it is acceptable to publish the same package in Maven artifacts with
different versions as long as the group ID and artifact ID remain the same.
The Maven or Gradle dependency mediation algorithms ensure that no more than one
such jar is added to the classpath.
