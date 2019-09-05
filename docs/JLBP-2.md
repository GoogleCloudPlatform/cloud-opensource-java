# [JLBP-2] Minimize API surface

Avoid exposing types from your dependencies.

  - Method arguments and return values from public methods should be standard Java
    types such as `java.time.LocalDate` or classes defined in the library itself,
    not third party types such as `org.joda.time.LocalDate`.

  - Your own public types should not be subclasses or implementations of types
    in third party libraries.  

  - If a third party type on the surface of your API changes or is removed,
    you either have to break your own API or remain with an older,
    unsupported version of the library that is likely to cause diamond dependency
    problems. Historically this was a big problem for libraries that exposed Guava types
    such as `com.google.common.io.OutputSupplier` because Guava incremented major versions
    every 6 months.

  - A type you've exposed on your own API surface cannot be shaded. This removes
    one of the available techniques for resolving diamond dependency conflicts.

Use package-protected classes and methods for internal APIs that should not be used by consumers.

Do not mark methods and classes public by default. Assume non-public until a need is
known.<sup id='a1'>[1](#item15)</sup>

Design for inheritance or prohibit it. That is, mark classes final unless there is a clear
reason for them to be subclassed. Mark methods in non-final classes final unless they
are meant to be overridden.<sup id='a2'>[2](#item19)</sup>

Prefer fewer packages over more packages to avoid
unnecessarily publicizing internal details,
since any dependency across package boundaries needs to be
public. (We may revisit this when we can rely on
the new module system in Java 11 or later.)

If you absolutely must create public classes that clients should not depend on,
one of the superpackages that contains these classes should be named `internal`.
For example, `com.foo.utilities.internal.xml`. 

<b id="item15">1</b> Bloch, Joshua. "Item 15: Minimize the accessibility of classes and members."
Effective Java, 3rd Edition. Boston: Addison-Wesley, 2018. p. 73[↩](#a1)

<b id="item19">2</b> Bloch, Joshua. "Item 19: Design and document for inheritance or else
prohibit it." Effective Java, 3rd Edition. Boston: Addison-Wesley, 2018. p. 93[↩](#a2)
