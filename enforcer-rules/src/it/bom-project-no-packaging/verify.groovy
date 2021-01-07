def buildLog = new File(basedir, "build.log")

assert buildLog.text.contains("[WARNING] A BOM should have packaging pom")
