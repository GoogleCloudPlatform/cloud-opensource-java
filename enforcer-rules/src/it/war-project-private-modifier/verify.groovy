def buildLog = new File(basedir, "build.log")

assert buildLog.text.contains("No error found")
