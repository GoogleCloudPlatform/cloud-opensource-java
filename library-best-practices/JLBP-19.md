[JLBP-19] Place each package in a single jar
--------------------------------------------------

Every package should have a single Maven group ID and artifact ID.
Do not give different classes in the same package different group IDs or artifact IDs.
Doing so causes problems in Java 9 and later and in OSGI environments
such as Eclipse plugins.

For example, if the class `com.google.i18n.Localization` is in the artifact
`com.google.i18n:i18n-utilities`, then `com.google.i18n.Strings`
and `com.google.i18n.Characters` must also be in the artifact
`com.google.i18n:i18n-utilities`.

This rule does not apply to subpackages. It is acceptable for
`com.google.i18n.charactersets.Latin1` to have different Maven coordinates
than `com.google.i18n.Localization`; for example, `com.google.foo:i18n-charactersets`.
It is also acceptable to publish classes from both `com.google.i18n` and
`com.google.i18n.charactersets` under the same group ID and artifact ID.
Similarly, it is fine to publish completely different packages such as
`com.google.foo` and `org.example.bar` under the same group ID and artifact ID.
However, once a class from a package has been published with a certain
group ID and artifact ID, no other class in that package should ever have
a different group ID or artifact ID.
