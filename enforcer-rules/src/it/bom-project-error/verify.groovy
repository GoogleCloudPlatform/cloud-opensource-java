def buildLog = new File(basedir, "build.log")

assert buildLog.text.replaceAll("\\r", "").contains('''\
[ERROR] Linkage Checker rule found 1 error. Linkage error report:
(com.google.guava:guava:20.0) com.google.common.base.Verify's method verify(boolean arg1, String arg2, Object arg3) is not found;
  referenced by 1 class file
    io.grpc.internal.ServiceConfigInterceptor (io.grpc:grpc-core:1.17.1)
''')