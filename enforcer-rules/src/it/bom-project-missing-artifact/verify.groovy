def buildLog = new File(basedir, "build.log").text

assert buildLog.contains("Failed to collect dependency: ")
assert buildLog.contains(
        "xerces:xerces-impl:jar:2.6.2 was not resolved. Dependency path: ant:ant:jar:1.6.2 (compile) > xerces:xerces-impl:jar:2.6.2 (compile?)")
assert buildLog.contains(        
        "xml-apis:xml-apis:jar:2.6.2 was not resolved. Dependency path: ant:ant:jar:1.6.2 (compile) > xml-apis:xml-apis:jar:2.6.2 (compile?)")