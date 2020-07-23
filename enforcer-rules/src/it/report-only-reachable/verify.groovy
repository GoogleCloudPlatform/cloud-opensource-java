def buildLog = new File(basedir, "build.log").text.replaceAll("\\r\\n", "\n")

assert buildLog.contains('''\
(com.google.guava:guava:20.0) com.google.common.base.Verify's method verify(boolean, String, Object) is not found;
  referenced by 3 class files
    io.grpc.internal.ServiceConfigInterceptor (io.grpc:grpc-core:1.17.1)
    io.grpc.internal.JndiResourceResolverFactory (io.grpc:grpc-core:1.17.1)
    io.grpc.internal.DnsNameResolver (io.grpc:grpc-core:1.17.1)

''')
