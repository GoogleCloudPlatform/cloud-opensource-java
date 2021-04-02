def buildLog = new File(basedir, "build.log")

assert buildLog.text.contains("NoSuchFileException: no-such-file.xml")
