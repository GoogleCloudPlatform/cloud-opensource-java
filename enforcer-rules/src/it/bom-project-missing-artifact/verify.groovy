def buildLog = new File(basedir, "build.log").text

assert buildLog.contains("Failed to collect dependency: "
        +"[xerces:xerces-impl:jar:2.6.2 was not resolved. "
        +"Dependency path: ant:ant:jar:1.6.2 (compile) > xerces:xerces-impl:jar:2.6.2 (compile?)")