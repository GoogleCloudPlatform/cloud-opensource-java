[JLBP-13] Quickly remove references to deprecated features in dependencies
--------------------------------------------------------------------------

- This best practice refers to code that *uses* deprecated functionality from
  another library, not to the deprecated functionality itself.
  - Consequently, this best practice does not apply to libraries that have no
    dependencies, and does not apply to usage of deprecated features within
    a library.
  - A "usage" in this context is any reference to a class or method in
    a dependency that can cause a static linkage error if it is deleted or
    made incompatible in some way (for example, by changing it to `private`).
- The earlier you remove usages of external deprecated functionality, the
  more versions of your product will work with your dependency once the
  deprecated features are fully deleted or hidden in the dependency.
  - For example, api-common-java 1.6.0 refers to `Futures.transform(ListenableFuture, Function)`
    in Guava, which was deprecated in 19.0 and removed in 26.0. In
    api-common-java 1.7, the code was changed to instead call
    `Futures.transform(ListenableFuture, Function, Executor)`,
    which is not deprecated. The oldest version of api-common-java that
    can be used with Guava 26.0 or later is 1.7.0, because 1.6 and earlier
    refers to a deleted method. If api-common-java had moved to the new
    method even earlier (for example, if this had been done in 1.0.0), then
    more versions of api-common-java would be compatible with Guava 26.0 and
    later, making it easier to find compatible combinations of versions.
