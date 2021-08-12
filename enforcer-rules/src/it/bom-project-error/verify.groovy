def buildLog = new File(basedir, "build.log").text.replaceAll("\\r\\n", "\n")

assert buildLog.contains('''\
[ERROR] Linkage Checker rule found 1 error:
(com.google.guava:guava:20.0) com.google.common.base.Verify's method "void verify(boolean, String, Object)" is not found;
  referenced by 3 class files
    io.grpc.internal.ServiceConfigInterceptor (io.grpc:grpc-core:1.17.1)
    io.grpc.internal.JndiResourceResolverFactory (io.grpc:grpc-core:1.17.1)
    io.grpc.internal.DnsNameResolver (io.grpc:grpc-core:1.17.1)
  Cause:
    Dependency conflict: com.google.guava:guava:20.0 does not define com.google.common.base.Verify's method "void verify(boolean, String, Object)" but com.google.guava:guava:26.0-android defines it.
      selected: com.google.api-client:google-api-client:1.27.0 (compile) / com.google.guava:guava:20.0 (compile)
      unselected: io.grpc:grpc-core:1.17.1 (compile) / com.google.guava:guava:26.0-android (compile)
''')

assert buildLog.contains('''Problematic artifacts in the dependency tree:
com.google.guava:guava:20.0 is at:
  com.google.api-client:google-api-client:1.27.0 (compile) / com.google.guava:guava:20.0 (compile)
  and 3 other dependency paths.
io.grpc:grpc-core:1.17.1 is at:
  io.grpc:grpc-core:1.17.1 (compile)
''');
