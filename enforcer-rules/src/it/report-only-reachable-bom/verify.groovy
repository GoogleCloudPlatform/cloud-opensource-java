def buildLog = new File(basedir, "build.log").text.replaceAll("\\r\\n", "\n")

assert buildLog.contains('''\
(io.opencensus:opencensus-api:0.28.1) Class io.opencensus.trace.unsafe.ContextUtils has default access;
  referenced by 1 class file in a different package
    io.grpc.census.CensusTracingModule (io.grpc:grpc-census:1.35.0)
''')
