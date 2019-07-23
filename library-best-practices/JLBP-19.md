[JLBP-19] Place each package in a single jar
--------------------------------------------------

Every package should have a single Maven group ID and artifact ID.
Do not put give classes in the same package different group IDs or artifact IDs.
Doing so causes problems in Java 9 and later and in OSGI environments
such as Eclipse plugins.

For example, if `com.google.foo.Localization` is in the artifact
`com.google:i18n-utilities`, then `com.google.foo.Strings`
and `com.google.foo.Characters` must also be in the artifact
`com.google.foo:i18n-utilities`.

This rule does not apply to subpackages. It is acceptable for
`com.google.foo.charactersets.Latin1` to be placed in a different
artifact such as `com.google.foo:i18n-charactersets`. It is also
OK to publish classes from both `com.google.foo` and
`com.google.foo.charactersets` under the same group ID and artifact ID.
Similarly, it is fine to publish completely different packages such as
`com.google.foo` and `org.example.bar` under the same group ID and artifact ID.
However once a class from a package has been published with a certain
group ID and artifact ID, no other class in that package should have
a different group ID or artifact ID.
