def buildLog = new File(basedir, "build.log").text.replaceAll("\\r\\n", "\n")

assert buildLog.contains('''\
com.google.api.gax.grpc.InstantiatingGrpcChannelProvider (in com.google.api:gax-grpc:1.38.0) does not implement com.google.api.gax.rpc.TransportChannelProvider withCredentials(com.google.auth.Credentials), required by com.google.api.gax.rpc.TransportChannelProvider (in com.google.api:gax:1.48.0)
  Cause:
    Dependency conflict: com.google.api:gax:1.48.0 defines incompatible version of com.google.api.gax.rpc.TransportChannelProvider but com.google.api:gax:1.38.0 defines compatible one.
      selected: com.google.cloud.tools.opensource:abstract-method-error-example:jar:1.0-SNAPSHOT / com.google.api:gax:1.48.0 (compile)
      unselected: com.google.cloud.tools.opensource:abstract-method-error-example:jar:1.0-SNAPSHOT / com.google.api:gax-grpc:1.38.0 (compile) / com.google.api:gax:1.38.0 (compile)''')


