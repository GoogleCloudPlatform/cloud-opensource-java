def buildLog = new File(basedir, "build.log")

// Windows build contains \r character
assert buildLog.text.replaceAll("\\r", "").contains('''\
[ERROR] Linkage Checker rule found 16 errors. Linkage error report:
Class javax.jms.Connection is not found;
  referenced by 1 class file
    org.apache.log4j.net.JMSAppender (log4j:log4j:1.2.12)
''')
