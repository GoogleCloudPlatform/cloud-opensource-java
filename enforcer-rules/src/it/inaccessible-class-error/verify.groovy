def buildLog = new File(basedir, "build.log").text.replaceAll("\\r\\n", "\n")

assert buildLog.contains('''\
(io.grpc:grpc-core:0.15.0) Class io.grpc.internal.SingleTransportChannel has default access;
  referenced by 1 class file in a different package
    io.grpc.grpclb.GrpclbLoadBalancer (io.grpc:grpc-grpclb:0.12.0)''')
