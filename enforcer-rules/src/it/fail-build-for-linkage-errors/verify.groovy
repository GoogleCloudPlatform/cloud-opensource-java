def buildLog = new File(basedir, "build.log").text.replaceAll("\\r\\n", "\n")

assert buildLog.contains('''\
[ERROR] Linkage Checker rule found 4 errors:
Class org.apache.avalon.framework.logger.Logger is not found;
  referenced by 1 class file
    org.apache.commons.logging.impl.AvalonLogger (commons-logging:commons-logging:1.1.1)
''')

assert buildLog.contains('''\
(com.google.guava:guava:20.0) com.google.common.base.Verify's method verify(boolean, String, Object) is not found;
  referenced by 3 class files
    io.grpc.internal.ServiceConfigInterceptor (io.grpc:grpc-core:1.17.1)
    io.grpc.internal.JndiResourceResolverFactory (io.grpc:grpc-core:1.17.1)
    io.grpc.internal.DnsNameResolver (io.grpc:grpc-core:1.17.1)
  Cause:
    Dependency conflict: com.google.guava:guava:20.0 does not define com.google.common.base.Verify's method verify(boolean, String, Object) but com.google.guava:guava:26.0-android defines it.
      selected: com.google.cloud.tools.opensource:test-no-such-method-error-example:jar:1.0-SNAPSHOT / com.google.api-client:google-api-client:1.27.0 (compile) / com.google.guava:guava:20.0 (compile)
      unselected: com.google.cloud.tools.opensource:test-no-such-method-error-example:jar:1.0-SNAPSHOT / io.grpc:grpc-core:1.17.1 (compile) / com.google.guava:guava:26.0-android (compile)
''')

assert buildLog.contains('''\
Problematic artifacts in the dependency tree:
commons-logging:commons-logging:1.1.1 is at:
  com.google.cloud.tools.opensource:test-no-such-method-error-example:jar:1.0-SNAPSHOT \
/ com.google.api-client:google-api-client:1.27.0 (compile) \
/ com.google.oauth-client:google-oauth-client:1.27.0 (compile) \
/ com.google.http-client:google-http-client:1.27.0 (compile) \
/ com.google.android:android:1.5_r4 (provided) \
/ commons-logging:commons-logging:1.1.1 (compile)
''')
assert buildLog.contains('''\
com.google.guava:guava:20.0 is at:
  com.google.cloud.tools.opensource:test-no-such-method-error-example:jar:1.0-SNAPSHOT \
/ com.google.api-client:google-api-client:1.27.0 (compile) \
/ com.google.guava:guava:20.0 (compile)
io.grpc:grpc-core:1.17.1 is at:
  com.google.cloud.tools.opensource:test-no-such-method-error-example:jar:1.0-SNAPSHOT \
/ io.grpc:grpc-core:1.17.1 (compile)
''')
