def buildLog = new File(basedir, "build.log").text

assert buildLog.contains("No reachable error found")
assert buildLog.contains("xerces:xerces-impl:2.6.2")
