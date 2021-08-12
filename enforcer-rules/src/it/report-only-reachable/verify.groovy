def buildLog = new File(basedir, "build.log").text.replaceAll("\\r\\n", "\n")

assert buildLog.contains('''\
(com.google.guava:guava:20.0) com.google.common.base.Verify's method "void verify(boolean, String, Object)" is not found;
  referenced by 3 class files
    io.grpc.internal.ServiceConfigInterceptor (io.grpc:grpc-core:1.17.1)
    io.grpc.internal.JndiResourceResolverFactory (io.grpc:grpc-core:1.17.1)
    io.grpc.internal.DnsNameResolver (io.grpc:grpc-core:1.17.1)
  Cause:
    Dependency conflict: com.google.guava:guava:20.0 does not define com.google.common.base.Verify's method "void verify(boolean, String, Object)" but com.google.guava:guava:26.0-android defines it.
      selected: com.google.cloud.tools.opensource:test-report-only-reachable:jar:1.0-SNAPSHOT / com.google.api-client:google-api-client:1.27.0 (compile) / com.google.guava:guava:20.0 (compile)
      unselected: com.google.cloud.tools.opensource:test-report-only-reachable:jar:1.0-SNAPSHOT / io.grpc:grpc-core:1.17.1 (compile) / com.google.guava:guava:26.0-android (compile)
''')
